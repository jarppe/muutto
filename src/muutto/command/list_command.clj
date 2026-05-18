(ns muutto.command.list-command
  (:require [muutto.util :as u :refer [error!]]
            [muutto.mig :as mig]
            [muutto.config :as config]
            [muutto.log :as log]))


(defn list-command
  "List migrations: muutto list <env>"
  [config]
  (let [dbname   (config/get config :dbname)
        verbose? (config/get config :opts :verbose)]
    (when-not (mig/db-initialized? config)
      (error! "database in " (log/yellow dbname) " is not initialized for migrations, use " (log/yellow "muutto init") " command."))
    (when verbose? 
      (println "muutto: listing database" (log/yellow dbname) "migrations"))
    (let [applied-at      (->> (mig/get-applied-migrations config)
                               (reduce (fn [acc {:keys [file-name applied]}]
                                         (assoc acc file-name applied))
                                       {}))
          migration-files (->> (mig/get-migration-files config)
                               (map :file-name))
          file-col-len (->> migration-files
                            (map count)
                            (reduce max 0)
                            (+ 2))
          table-printer   (if verbose?
                            (log/table-printer file-col-len 20)
                            (fn [file-name applied]
                              (println (str (log/rpad file-col-len file-name)
                                            applied))))]
      (when verbose?
        (table-printer "File:" "Applied:")
        (table-printer))
      (doseq [file-name migration-files]
        (let [applied (-> file-name applied-at (u/format-datetime))]
          (table-printer file-name
                         (if applied
                           (log/green applied)
                           (log/yellow "pending"))))))))
