(ns muutto.command.migrate-command
  (:require [muutto.mig :as mig]
            [muutto.util :refer [error!]]))


(defn migrate-command
  "Migrate database"
  [config databases]
  (when-not (seq databases)
    (error! "migrate command requires one of more database names"))
  (doseq [dbname databases]
    (mig/migrate-database config dbname)))
