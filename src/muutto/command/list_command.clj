(ns muutto.command.list-command
  (:require [clojure.string :as str]
            [muutto.util :as u :refer [error!]]
            [muutto.mig :as mig]
            [muutto.config :as config]
            [muutto.log :as log]))


(defn list-command
  "List migrations"
  [config]
  (let [dbname (config/get config :dbname)]
    (when-not (mig/db-initialized? config)
      (error! "database in " (log/yellow dbname) " is not initialized for migrations"))
    (println "muutto: listing database" (log/yellow dbname) "migrations")
    (let [applied-at       (->> (mig/get-applied-migrations config)
                                (reduce (fn [acc {:keys [file-name applied]}]
                                          (assoc acc file-name applied))
                                        {}))
          migration-files  (->> (mig/get-migration-files config)
                                (map :file-name))
          filename-col-len (->> migration-files
                                (map count)
                                (reduce max 0)
                                (+ 2))
          row-fmt          (format (str "%%-%ds " (log/gray "|") " %%s") filename-col-len)]
      (println (format row-fmt "File:" "Applied:"))
      (println (log/gray (str/join (repeat filename-col-len "-"))
                         "-|--------------------"))
      (doseq [file-name migration-files]
        (let [applied (-> file-name applied-at (u/format-datetime))]
          (println (format row-fmt file-name (if applied
                                               (log/green applied)
                                               (log/yellow "pending")))))))))
