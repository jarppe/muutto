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
                 {:args ["--single-transaction"]
                  :stmt [file
                         (format "insert into %s (file_name, file_hash) values ('%s', '%s')"
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


(defn migrate-database [config {:keys [migration-start
                                       file-start
                                       file-done
                                       file-migrated
                                       migration-end]
                                :or   {migration-start (constantly nil)
                                       file-start      (constantly nil)
                                       file-done       (constantly nil)
                                       file-migrated   (constantly nil)
                                       migration-end   (constantly nil)}}]
  (let [applied-migrations (->> (get-applied-migrations config)
                                (reduce (fn [acc migration]
                                          (assoc acc (:file-name migration) migration))
                                        {}))
        migration-files    (get-migration-files config)]
    (when-not (seq migration-files)
      (println "no migration files nound")
      (System/exit 0))
    (-> (reduce (fn [ctx migration-file]
                  (let [file-name    (:file-name migration-file)
                        file-hash    (:file-hash migration-file)
                        applied      (get applied-migrations file-name)
                        applied-hash (:file-hash applied)
                        ctx          (file-start ctx file-name)]
                    (cond
                      (= file-hash applied-hash)  (file-done ctx file-name)
                      (nil? applied)              (do (apply-migration config migration-file)
                                                      (file-migrated ctx file-name))
                      :else (do (println)
                                (println (log/red "error:") " file has been changed")
                                (error! (format "muutto: migration file %s has been changed, migration halted" file-name))))))
                (migration-start (map :file-name migration-files))
                migration-files)
        (migration-end))))
