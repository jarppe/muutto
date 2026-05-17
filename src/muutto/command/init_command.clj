(ns muutto.command.init-command
  (:require [muutto.mig :as mig]
            [muutto.config :as config]))


(defn init-command
  "Init database for migrations (indempotent)"
  [config]
  (println "muutto: initializing database" (config/get config :dbname))
  (mig/init-migrations config))

