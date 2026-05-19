(ns muutto.mig
  (:require [clojure.string :as str]
            [clojure.data.csv :as csv]
            [muutto.util :as u :refer [error!]]
            [muutto.config :as config]
            [muutto.exec :as exec]
            [muutto.log :as log])
  (:import (java.nio.file Files)))


(def cwd (u/to-path "."))


(defn db-exists? [config dbname] 
  (-> (exec/exec config {:args ["--csv"]
                         :stmt (str "select exists (select 1 from pg_database where datname = '" dbname "')")})
      (str/split #"\n")
      (second)
      (= "t")))


(defn db-type-exists? [config type-schema type-name]
  (-> (exec/exec config {:args ["--csv"]
                         :stmt (str "select exists (
                                       select 1 from information_schema.user_defined_types
                                       where
                                         user_defined_type_schema = '" type-schema "'
                                         and
                                         user_defined_type_name = '" type-name "'
                                     )")})
      (str/split #"\n")
      (second)
      (= "t")))


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


(defn init-migrations [config]
  (let [mig-table (config/get config :table)
        mig-shema (-> mig-table (str/split  #"\.") (first))]
    (exec/exec config {:stmt [(str "create schema if not exists " mig-shema)
                              (str "create table if not exists " mig-table " (
                                      file_name   text         not null primary key,
                                      file_hash   text         not null,
                                      applied     timestamptz  not null default now()
                                    )")]})))


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
                                 (when-let [[match action args] (re-matches #"\s*--\s*muutto:([a-z\-]+):\s+(.*)" line)]
                                   (case action
                                     "requires" (str/split args #"\s+")
                                     (error! "unknown sql comment declaration:" match)))))
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
      (error! "can't find migration files directory " (pr-str dir)))
    (when-not (.isDirectory f)
      (error! (str dir) " is not directory")))
  (->> (Files/list dir)
       (.iterator)
       (iterator-seq)
       (filter #(str/ends-with? (str %) ".sql"))
       (map parse-migration-file)))


(defn assert-requires-resolved [migration-files]
  (let [file-exists? (->> migration-files
                          (map :file)
                          (reduce conj #{}))]
    (when-let [missing (->> (mapcat :requires migration-files)
                            (remove file-exists?)
                            (seq))]
      (error! "requied files missing: " (str/join ", " (map str missing))))
    migration-files))


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
    (for [migration-dir (if (sequential? migration-dirs)
                          migration-dirs
                          [migration-dirs])]
      (->> (u/to-path migration-dir)
           (find-migration-files-from-dir)
           (assert-requires-resolved)
           (sort-files)))))


(comment
  (get-migration-files {:migrations ["test-resources/db/migrations"]})
  ;
  )


(defn migration-start [files]
  {:migration-start (System/nanoTime)
   :file-count      (count files)
   :file-col-len    (->> (map count files)
                         (reduce max 0) 
                         (+ 2))})


(defn migration-end [{:keys [migration-start file-count]}]
  (println (str (log/gray "migrated ")
                (log/green file-count)
                (log/gray " files in ")
                (log/yellow (log/format-duration migration-start))
                (log/gray " sec"))))


(defn file-start [ctx file]
  (let [file-col-len (:file-col-len ctx)]
    (print "  " (log/rpad file-col-len file))
    (flush)
    (assoc ctx :start (System/nanoTime))))


(defn file-done [ctx _file]
  (println (str (log/yellow "skipped  ")
                (log/gray "(")
                (log/yellow (log/format-duration (:start ctx)))
                (log/gray " sec)")))
  ctx)


(defn file-migrated [ctx _file]
  (println (str (log/green "migrated ")
                (log/gray "(")
                (log/yellow (log/format-duration (:start ctx)))
                (log/gray " sec)")))
  ctx)


(defn migrate-database
  ([config] (migrate-database config (when (-> config :opts :verbose)
                                       {:migration-start migration-start
                                        :file-start      file-start
                                        :file-done       file-done
                                        :file-migrated   file-migrated
                                        :migration-end   migration-end})))
  ([config {:keys [migration-start
                   file-start
                   file-done
                   file-migrated
                   migration-end]
            :or   {migration-start (constantly nil)
                   file-start      (constantly nil)
                   file-done       (constantly nil)
                   file-migrated   (constantly nil)
                   migration-end   (constantly nil)}}]
   (let [postgres        (config/env-config config :postgres)
         dbname          (config/get config :dbname)
         locked?         (config/get config :locked)
         verbose?        (config/get config :opts :verbose)]
     (when verbose?
       (println "muutto: migrating database" (log/yellow dbname)))
     (when locked?
       (error! "database is locked"))
     (when-not (db-exists? postgres dbname)
       (when verbose?
         (print (log/gray (log/rpad 25 "creating database")))) (flush)
       (exec/exec postgres {:stmt (str "create database " dbname)})
       (when verbose?
         (println (log/green "ok"))))
     (when-not (db-initialized? config)
       (when verbose?
         (print (log/gray (log/rpad 25 "initializing database")))) (flush)
       (init-migrations config)
       (when verbose?
         (println (log/green "ok"))))
     (let [all-migration-files (get-migration-files config)
           applied-migrations  (->> (get-applied-migrations config)
                                    (reduce (fn [acc migration]
                                              (assoc acc (:file-name migration) migration))
                                            {}))]
       (doseq [migration-files all-migration-files]
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
                           :else (do (println (log/red "error:") "SQL migration file" (log/yellow file-name) "file has been changed")
                                     (println "  migrated file hash:" (log/yellow applied-hash))
                                     (println "   current file hash:" (log/red file-hash))
                                     (println "If you know what you are doing you can force the file")
                                     (println "hash update with option:" (log/yellow "--force"))
                                     (println "muutto: migration halted")
                                     (System/exit 1)))))
                     (migration-start (map :file-name migration-files))
                     migration-files)
             (migration-end)))))))
