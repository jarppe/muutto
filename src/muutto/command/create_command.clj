(ns muutto.command.create-command
  (:require [muutto.config :as config]
            [muutto.util :refer [error!]]
            [muutto.exec :as exec]
            [muutto.mig :as mig]))


(defn create-command
  "Create database"
  [config] 
  (let [args  (rest (config/get config :args))
        migr? (-> config :opts :migrate)
        init? (or migr? (-> config :opts :init))]
    (when-not (seq args)
      (error! "create command requires one of more target database environments as arguments"))
    (doseq [env-name args
            :let     [target-config (config/env-config config (keyword env-name))
                      locked?       (:locked target-config)
                      dbname        (:dbname target-config)]]
      (when locked?
        (error! "database environment " env-name " is locked"))
      (when-not dbname
        (error! "database environment " env-name " is missing \"dbname\" configuration"))
      (println "muutto: creating database" dbname)
      (exec/exec config {:stmt (str "create database " dbname " with lc_ctype='C.UTF-8'")})
      (when init?
        (println "muutto: initializing database" dbname)
        (mig/init-migrations target-config))
      (when migr?
        (mig/migrate-database target-config)))))


(defn drop-command
  "Drop database"
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
