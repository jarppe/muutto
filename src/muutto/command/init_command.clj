(ns muutto.command.init-command
  (:require [muutto.mig :as mig]
            [muutto.config :as config]
            [muutto.log :as log]))


(defn init-command
  "Init database for migrations: muutto init <env>"
  [config]
  (let [verbose? (config/get config :opts :verbose)
        dbname   (config/get config :dbname)]
    (when verbose?
      (println "muutto: initializing database" (log/yellow dbname)))
    (mig/init-migrations config)))

