(ns muutto.command.create-command
  (:require [muutto.exec :as exec]
            [muutto.mig :as mig]
            [muutto.util :refer [error!]]))


(defn create-command
  "Init database for migrations (indempotent)"
  [config databases]
  (when-not (seq databases)
    (error! "create command requires one of more database names"))
  (let [migr? (-> config :migrate)
        init? (or migr? (-> config :init))]
    (doseq [dbname databases]
      (println "muutto: creating database" dbname)
      (exec/exec config {:stmt (str "create database " dbname)})
      (when init?
        (println "muutto: initializing database" dbname)
        (mig/init-migrations (assoc config :dbname dbname)))
      (when migr?
        (mig/migrate-database (assoc config :dbname dbname))))))


(defn drop-command
  "Drop database"
  [config databases]
  (when-not (seq databases)
    (error! "drop command requires one of more database names"))
  (doseq [dbname databases]
    (println "muutto: dropping database" dbname)
    (exec/exec config {:stmt (str "drop database " dbname)})))

