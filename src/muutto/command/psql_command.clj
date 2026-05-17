(ns muutto.command.psql-command
  (:require [muutto.exec :as exec]
            [muutto.util :refer [error!]]))


(defn psql-command
  "Execute sql statements"
  [config]
  (let [stmts (-> config :args (rest))]
    (when-not (seq stmts)
      (error! "psql command requires one of more statements to execute"))
    (-> (exec/exec config {:stmt stmts})
        (println))))
