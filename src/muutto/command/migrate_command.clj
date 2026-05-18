(ns muutto.command.migrate-command
  (:require [muutto.mig :as mig]))


(defn migrate-command
  "Migrate database: muutto migrate <env>"
  [config]
  (println "muutto: migrating database" (:dbname config))
  (mig/migrate-database config))  
