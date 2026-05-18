(ns muutto.command.create-command
  (:require [muutto.config :as config]
            [muutto.util :refer [error!]]
            [muutto.exec :as exec]
            [muutto.mig :as mig]))


(defn create-command
  "create database: muutto create <env>"
  [config]
  (let [postgres (config/env-config config :postgres)
        locked?  (config/get config :locked)
        dbname   (config/get config :dbname)
        migrate? (config/get config :opts :migrate)
        verbose? (config/get config :opts :verbose)]
    (when locked?
      (error! "database is locked"))
    (when-not dbname
      (error! "database is missing \"dbname\" configuration"))
    (when verbose?
      (println "muutto: creating database" dbname))
    (exec/exec postgres {:stmt (str "create database " dbname)})
    (when migrate?
      (mig/init-migrations config)
      (mig/migrate-database config))))


(defn drop-command
  "drop database: muutto drop <env>"
  [config]
  (let [postgres (config/env-config config :postgres)
        locked?  (config/get config :locked)
        dbname   (config/get config :dbname)
        verbose? (config/get config :opts :verbose)]
    (when locked?
      (error! "database is locked"))
    (when-not dbname
      (error! "database is missing \"dbname\" configuration"))
    (when verbose?
      (println "muutto: dropping database" dbname))
    (exec/exec postgres {:stmt (str "drop database " dbname)})))
