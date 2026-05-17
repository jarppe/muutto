(ns muutto.command.migrate-command
  (:require [muutto.mig :as mig]))


(defn migrate-command
  "Migrate database"
  [config]
  (mig/migrate-database config))
