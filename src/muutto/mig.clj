(ns muutto.mig
  (:require [clojure.string :as str]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [muutto.exec :as exec]
            [muutto.util :as u :refer [error!]]))


(defn init-migrations [config]
  (let [mig-table (-> config :mig-table)
        mig-shema (-> mig-table (str/split  #"\.") (first))]
    (exec/exec config {:stmt [(str "create schema if not exists " mig-shema)
                              (str "create table if not exists " mig-table " (
                                    file_name   text         not null primary key,
                                    file_hash   text         not null,
                                    applied     timestamptz  not null default now()
                                  )")]})))


(defn applied-migrations [config dbname]
  (->> (exec/exec config {:args ["--csv"]
                          :stmt [(str "select 
                                         file_name,
                                         file_hash,
                                         to_char(applied, 'YYYY-MM-DD\"T\"HH24:MI:SS.FF3\"Z\"') as applied
                                       from " (-> config :mig-table) " 
                                       order by
                                         applied asc")]})
       (csv/read-csv)
       (rest)
       (mapv (fn [[file-name file-hash applied]]
               {:file-name file-name
                :file-hash file-hash
                :applied   (u/parse-datetime applied)}))))


(defn parse-migration-file [file]
  (let [file-name (.getName file)
        buffer    (byte-array 4096)
        sha       (java.security.MessageDigest/getInstance "SHA-256")
        hex       (-> (java.util.HexFormat/of)
                      (.withLowerCase))
        file-hash (->> (with-open [in (io/input-stream file)]
                         (loop []
                           (let [c (.read in buffer)]
                             (if (= c -1)
                               (.digest sha)
                               (do (.update sha buffer 0 c)
                                   (recur))))))
                       (.formatHex hex))
        requires  (with-open [in (io/reader file)]
                    (->> (line-seq in)
                         (take-while (fn [line]
                                       (or (str/blank? line)
                                           (str/starts-with? line "--"))))
                         (keep (fn [line]
                                 (some-> (re-matches #"--\s+requires:\s+(.*)" line)
                                         (second)
                                         (str/split #"\s+"))))
                         (reduce concat)))]
    {:file      file
     :file-name file-name
     :file-hash file-hash
     :requires  requires}))


(comment
  (parse-migration-file (io/file "./test/migration/foo.sql")))


(defn- require-done [done require]
  (some (comp #{require} :file-name)
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


(defn find-migration-files-from-dir [dir]
  (->> (.listFiles dir)
       (filter (fn [f] (str/ends-with? (.getName f) ".sql")))
       (map parse-migration-file)))


(comment
  (find-migration-files-from-dir (io/file "./test/migration")))


(defn find-migration-files [config]
  (let [dirs         (-> config :migrations)
        files        (->> (if (sequential? dirs)
                            dirs
                            [dirs])
                          (map (fn [dir-name]
                                 (let [dir (io/file dir-name)]
                                   (when-not (.exists dir)
                                     (error! "can't find directory" dir-name))
                                   (when-not (.isDirectory dir)
                                     (error! dir-name "is not directory"))
                                   (find-migration-files-from-dir dir))))
                          (reduce concat))
        file-exists? (reduce (fn [acc {:keys [file-name]}]
                               (conj acc file-name))
                             #{}
                             files)]
    (when-let [missing (seq (remove file-exists? (mapcat :requires files)))]
      (error! "requied files missing:" (str/join ", " missing))) 
    (sort-files files)))


(comment
  (find-migration-files {:config {:migrations "./test/migration"}})
  )


(defn- apply-migration [config {:keys [file-name file-hash file]}]
  (try
    (exec/exec (assoc config :on-error :throw)
               {:args  ["--single-transaction"
                        "--file" "-"]
                :input file
                :stmt  [(format "insert into %s (file_name, file_hash) values ('%s', '%s')"
                                (-> config :mig-table)
                                file-name
                                file-hash)]})
    (catch clojure.lang.ExceptionInfo e
      (println "error")
      (.println System/err (str "muutto: ERROR: migration of " file-name " failed"))
      (.println System/err (str "muutto: psql exit code " (-> e (ex-data) :exit)))
      (.println System/err (-> e (ex-data) :err))
      (System/exit 1))))


(defn migrate-database [config dbname]
  (println "muutto: migrating database" dbname)
  (let [applied-migrations (reduce (fn [acc migration]
                                     (assoc acc (:file-name migration) migration))
                                   {}
                                   (applied-migrations config dbname))
        migration-files    (find-migration-files config)]
    (doseq [migration-file migration-files]
      (let [file-name (:file-name migration-file)
            file-hash (:file-hash migration-file)]
        (print "muutto:   applying: %s" file-name)
        (flush)
        (let [applied      (get applied-migrations file-name)
              applied-hash (:file-hash applied)]
          (cond
            (= file-hash applied-hash)  (println "ok")
            (nil? applied)              (do (apply-migration config migration-file)
                                            (println "done"))
            :else (do (println "error: file has been changed")
                      (.println System/err (format "muutto: migration file %s has been changed, migration halted" file-name))
                      (System/exit 1))))))))



(comment
  (let [files [{:file-name "c"
                :requires  ["a"]}
               {:file-name "d"
                :requires  ["c"]}
               {:file-name "a"}
               {:file-name "b"
                :requires  ["a"]}
               {:file-name "e"
                :requires  ["a" "c"]}]]
    (->> (sort-files files)
         (map :file-name))))