(ns muutto.command.init-command
  (:require [muutto.mig :as mig]
            [muutto.config :as config]
            [muutto.log :as log]
            [muutto.util :refer [error!]]))


(defn init-command
  "Init database for migrations: muutto init <env>"
  [config]
  (let [verbose? (config/get config :opts :verbose)
        dbname   (config/get config :dbname)
        locked?  (config/get config :locked)]
    (when verbose?
      (println "muutto: initializing database" (log/yellow dbname)))
    (when locked?
      (error! "database is locked"))
    (mig/init-migrations config)))

