(ns muutto.command.init-command
  (:require [muutto.mig :as mig]))


(defn init-command
  "Init database for migrations (indempotent)"
  [config _] 
  (println "muutto: initializing database" (:dbname config))
  (mig/init-migrations config))

