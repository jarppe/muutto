(ns muutto.command.psql-command
  (:require [muutto.exec :as exec]))


(defn psql-command
  "Execute sql statements"
  [opts]
  (let [stmt (-> opts :args (rest))]
    (-> (exec/exec opts {:stmt stmt})
        (println))))
