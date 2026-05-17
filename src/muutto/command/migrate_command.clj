(ns muutto.command.migrate-command
  (:require [muutto.mig :as mig]
            [muutto.log :as log]))


(defn migrate-command
  "Migrate database"
  [config]
  (println "muutto: migrating database" (:dbname config))
  (mig/migrate-database config {:migration-start (fn [files]
                                                   (println (log/gray "  found")
                                                            (log/yellow (count files))
                                                            (log/gray "migration files"))
                                                   {:migration-start (System/nanoTime)
                                                    :file-count      (count files)
                                                    :file-col-len    (->> (map count files)
                                                                          (reduce max 0)
                                                                          (+ 2))})
                                :migration-end   (fn [{:keys [migration-start file-count]}]
                                                   (println (log/gray "  migrated")
                                                            (log/green file-count)
                                                            (log/gray " files in ")
                                                            (log/yellow (log/format-duration migration-start))
                                                            (log/gray "sec")))
                                :file-start      (fn [ctx file]
                                                   (let [file-col-len (:file-col-len ctx)]
                                                     (print (log/rpad file-col-len file))
                                                     (flush)
                                                     (assoc ctx :start (System/nanoTime))))
                                :file-done       (fn [ctx _file]
                                                   (println (log/yellow "passed   ")
                                                            (log/gray "(")
                                                            (log/format-duration (:start ctx))
                                                            (log/gray ")"))
                                                   ctx)
                                :file-migrated   (fn [ctx _file]
                                                   (println (log/green "migrated ")
                                                            (log/gray "(")
                                                            (log/format-duration (:start ctx))
                                                            (log/gray " sec)"))
                                                   ctx)}))  
