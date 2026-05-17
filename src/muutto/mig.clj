(ns muutto.mig
  (:require [clojure.string :as str]
            [clojure.data.csv :as csv]
            [muutto.util :as u :refer [error!]]
            [muutto.config :as config]
            [muutto.exec :as exec]
            [muutto.log :as log])
  (:import (java.nio.file Files)))


(def cwd (u/to-path "."))


(defn init-migrations [config]
  (let [mig-table (config/get config :table)
        mig-shema (-> mig-table (str/split  #"\.") (first))]
    (exec/exec config {:stmt [(str "create schema if not exists " mig-shema)
                              (str "create table if not exists " mig-table " (
                                      file_name   text         not null primary key,
                                      file_hash   text         not null,
                                      applied     timestamptz  not null default now()
                                    )")]})))


(defn db-initialized? [config]
  (let [[schema table] (-> (config/get config :table)
                           (str/split #"\."))]
    (-> (exec/exec config {:args ["--csv"]
                           :stmt (format "select exists (
                                             select 1 from information_schema.tables 
                                             where
                                               table_schema = '%s' 
                                               and
                                               table_name = '%s'
                                           )"
                                         schema
                                         table)})
        (str/split #"\n")
        (second)
        (= "t"))))


(defn get-applied-migrations [config]
  (let [mig-table (config/get config :table)] 
    (->> (exec/exec config {:args ["--csv"]
                            :stmt [(str "select 
                                           file_name,
                                           file_hash,
                                           to_char(applied, 'YYYY-MM-DD\"T\"HH24:MI:SS.FF3\"Z\"') as applied
                                         from " mig-table " 
                                         order by
                                           applied asc")]})
         (csv/read-csv)
         (rest)
         (mapv (fn [[file-name file-hash applied]]
                 {:file-name file-name
                  :file-hash file-hash
                  :applied   (u/parse-datetime applied)})))))


(defn parse-migration-file [file]
  (let [file      (.relativize cwd file)
        dir       (.getParent file)
        file-hash (u/file-hash file)
        requires  (->> (Files/lines file)
                       (.iterator)
                       (iterator-seq)
                       (take-while (fn [line]
                                     (or (str/blank? line)
                                         (str/starts-with? line "--"))))
                       (mapcat (fn [line]
                                 (some-> (re-matches #"--\s+requires:\s+(.*)" line)
                                         (second)
                                         (str/split #"\s+"))))
                       (map (fn [require] 
                              (->> (.resolve dir require)
                                   (.relativize cwd)))))]
    {:file      file
     :file-name (str file)
     :file-hash file-hash
     :requires  requires}))


(defn find-migration-files-from-dir [dir]
  (let [f (.toFile dir)]
    (when-not (.exists f)
      (error! "can't find migration files directory " (str dir)))
    (when-not (.isDirectory f)
      (error! (str dir) " is not directory")))
  (->> (Files/list dir)
       (.iterator)
       (iterator-seq)
       (filter #(str/ends-with? (str %) ".sql"))
       (map parse-migration-file)))


(defn find-migration-files [dirs]
  (mapcat find-migration-files-from-dir dirs))


(defn assert-requires-resolved [migration-files]
  (let [file-exists? (->> migration-files
                          (map :file)
                          (reduce conj #{}))]
    (when-let [missing (->> (mapcat :requires migration-files)
                            (remove file-exists?)
                            (seq))]
      (error! "requied files missing: " (str/join ", " (map str missing))))
    migration-files))


(comment
  (-> (find-migration-files [(u/to-path "test-resources/db/migrations")])
      (assert-requires-resolved))
  ;
  )


(defn- apply-migration [config {:keys [file-name file-hash file]}]
  (let [mig-table (config/get config :table)]
    (try
      (exec/exec (assoc config :on-error :throw)
                 {:args  ["--single-transaction"
                          "--file" "-"]
                  :input (.toFile file)
                  :stmt  [(format "insert into %s (file_name, file_hash) values ('%s', '%s')"
                                  mig-table
                                  file-name
                                  file-hash)]})
      (catch clojure.lang.ExceptionInfo e
        (println "error")
        (.println System/err (str "muutto: ERROR: migration of " file-name " failed"))
        (.println System/err (str "muutto: psql exit code " (-> e (ex-data) :exit)))
        (.println System/err (-> e (ex-data) :err))
        (error! "Migration terminated on error")))))


(defn- require-done [done require]
  (some (comp #{require} :file)
        done))


(defn- requires-in-done [{:keys [done]} {:keys [requires]}]
  (every? (partial require-done done) requires))


(defn- process-files [acc file]
  (if (requires-in-done acc file)
    (update acc :done conj file)
    (update acc :pending conj file)))


(defn- sort-files [files]
  (loop [acc {:done    []
              :pending files}]
    (if (-> acc :pending (seq))
      (-> (reduce process-files
                  (assoc acc :pending [])
                  (-> acc :pending))
          (recur))
      (-> acc :done))))


(defn get-migration-files [config]
  (let [migration-dirs (config/get config :migrations)]
    (->> (if (sequential? migration-dirs)
           migration-dirs
           [migration-dirs])
         (map u/to-path)
         (find-migration-files)
         (assert-requires-resolved)
         (sort-files))))


(comment
  (get-migration-files {:migrations ["test-resources/db/migrations"]})
  ;
  )


(defn duration [start]
  (format "%.3f" (-> (- (System/nanoTime) start)
                     (java.time.Duration/ofNanos)
                     (.toMillis)
                     (double)
                     (/ 1000.0))))

(defn status [applied? start]
  (str (if applied?
         (log/green  "done ")
         (log/yellow "ok   "))
       (log/gray "(")
       (duration start)
       (log/gray " sec)")))


(defn migrate-database [config]
  (println "muutto: migrating database" (:dbname config))
  (let [applied-migrations (->> (get-applied-migrations config)
                                (reduce (fn [acc migration]
                                          (assoc acc (:file-name migration) migration))
                                        {}))
        migration-files    (get-migration-files config)
        filename-col-len   (->> migration-files
                                (map (comp count :file-name))
                                (reduce max 0)
                                (+ 2))
        row-fmt            (format (str "%%-%ds " (log/gray "| ")) filename-col-len)]
    (println (format row-fmt "File:") "Status:")
    (println (log/gray (str/join (repeat filename-col-len "-")) "-|--------------------"))
    (doseq [migration-file migration-files]
      (let [file-name (:file-name migration-file)
            file-hash (:file-hash migration-file)
            start     (System/nanoTime)]
        (print (format row-fmt file-name))
        (flush)
        (let [applied      (get applied-migrations file-name)
              applied-hash (:file-hash applied)]
          (cond
            (= file-hash applied-hash)  (println (status false start))
            (nil? applied)              (do (apply-migration config migration-file)
                                            (println (status true start)))
            :else (do (println (log/red "error:") " file has been changed")
                      (error! (format "muutto: migration file %s has been changed, migration halted" file-name)))))))))
