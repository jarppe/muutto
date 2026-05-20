(ns muutto.command.test-command
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [muutto.exec :as exec]
            [muutto.mig :as mig]
            [muutto.util :as u :refer [error!]]
            [muutto.config :as config]
            [muutto.log :as log]
            [clojure.string :as str])
  (:import (java.nio.file Files)))


(defn find-test-files-from-dir [dir]
  (let [f (.toFile dir)]
    (when-not (.exists f)
      (error! "can't find test files directory " (str dir)))
    (when-not (.isDirectory f)
      (error! (str dir) " is not directory")))
  (->> (Files/list dir)
       (.iterator)
       (iterator-seq) 
       (filter (comp (partial re-matches #".*/test[-_].*\.sql$") str))
       (map (fn [file]
              (.relativize u/cwd file)))))


(defn find-test-files [config]
  (let [dirs (config/get config :tests)]
    (->> (if (sequential? dirs) dirs [dirs])
         (map u/to-path)
         (mapcat find-test-files-from-dir))))


(defmacro with-timing [verbose? step-name & body]
  `(let [start# (System/nanoTime)]
     (when ~verbose?
       (print (log/rpad 70 (log/gray ~step-name)))
       (flush))
     (let [message# (do ~@body)]
       (when ~verbose?
         (println (str message#
                       (log/gray " (")
                       (log/yellow (log/format-duration start#))
                       (log/gray " sec)")))))))


(defn test-command
  "Run database tests: muutto test <env>"
  [config]
  (let [dbname     (config/get config :dbname)
        postgres   (config/env-config config :postgres)
        test-files (find-test-files config)
        verbose?   (-> config :opts :verbose)
        test-start (System/nanoTime)
        locked?    (config/get config :locked)]
    (when locked?
      (error! "database is locked"))
    (when-not (seq test-files)
      (error! "muutto: no test files found"))
    (when verbose?
      (println "muutto: run db tests on database" (log/yellow dbname)))
    (with-timing verbose? "creating database"
      (if (mig/db-exists? postgres dbname)
        (log/yellow "skip")
        (do (exec/exec postgres {:stmt (str "create database " dbname)})
            (mig/init-migrations config)
            (log/green "ok"))))
    (when verbose?
      (println (log/gray "migrating database")))
    (mig/migrate-database config)
    (let [start (System/nanoTime)]
      (when verbose?
        (println (log/gray "installing tests")))
      (exec/exec config {:stmt ["drop schema if exists test cascade"
                                "create schema if not exists test"]})
      (doseq [test-file test-files]
        (with-timing verbose? (str "  " test-file)
          (exec/exec config {:args ["--single-transaction"]
                             :stmt ["set local search_path to example, test, pgunit, public"
                                    test-file]})
          (log/green "ok")))
      (when verbose?
        (println (str (log/gray "installed ")
                      (log/green (count test-files))
                      (log/gray " tests in ")
                      (log/yellow (log/format-duration start))
                      (log/gray " sec")))))
    (let [start (System/nanoTime)]
      (when verbose? (println (log/gray "running tests")))
      (let [resp (exec/exec config {:args ["--csv"]
                                    :stmt ["select
                                              test_name,
                                              successful,
                                              failed,
                                              error_message,
                                              to_char(duration, 'SS.FF3')
                                            from
                                              pgunit.test_run_all()"]})]
        (doseq [[test-name success fail err-msg duration] (->> (csv/read-csv resp) (rest))]
          (let [status (cond
                         (= success "t") :ok
                         (= fail "t")    :fail
                         :else           :error)]
            (println (str (log/color (case status
                                       :ok   log/color-white
                                       :fail log/color-yellow
                                       log/color-red)
                                     (log/rpad 57 test-name))
                          (case status
                            :ok    (log/green " ok ")
                            :fail  (log/red   " fail ")
                            :error (log/red   " error "))
                          (log/gray "(")
                          (log/yellow duration)
                          (log/gray " sec)")))
            (when (and (not= status :ok) err-msg)
              (doseq [line (str/split-lines err-msg)]
                (println "    " (log/yellow line))))))
        (when verbose?
          (println (str (log/gray "tests run in ")
                        (log/yellow (log/format-duration start))
                        (log/gray " sec"))))))
    (when verbose?
      (println (str (log/gray "total time: ")
                    (log/yellow (log/format-duration test-start))
                    (log/gray " sec"))))))


