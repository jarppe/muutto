(ns muutto.command.create-command
  (:require [muutto.config :as config]
            [muutto.util :refer [error!]]
            [muutto.exec :as exec]
            [muutto.mig :as mig]))


(defn create-command
  "create database: muutto create <env>"
  [config]
  (let [postgres (config/env-config config :postgres)
        migrate? (-> config :opts :migrate)
        locked?  (:locked config)
        dbname   (:dbname config)]
    (when locked? (error! "database is locked"))
    (when-not dbname (error! "database is missing \"dbname\" configuration"))
    (println "muutto: creating database" dbname)
    (exec/exec postgres {:stmt (str "create database " dbname " with lc_ctype='C.UTF-8'")})
    (when migrate?
      (mig/init-migrations config)
      (mig/migrate-database config))))


(defn drop-command
  "drop database: muutto drop <env>"
  [config]
  (let [args (rest (config/get config :args))]
    (when-not (seq args)
      (error! "drop command requires one of more target database environments as arguments"))
    (doseq [env-name args
            :let     [target-config (config/env-config config (keyword env-name))
                      locked?       (:locked target-config)
                      dbname        (:dbname target-config)]]
      (when locked?
        (error! "database environment " env-name " is locked"))
      (when-not dbname
        (error! "database environment " env-name " is missing \"dbname\" configuration"))
      (println "muutto: dropping database" dbname)
      (exec/exec config {:stmt (str "drop database " dbname)}))))
