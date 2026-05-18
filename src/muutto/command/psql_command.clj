(ns muutto.command.psql-command
  (:require [muutto.exec :as exec]))


(defn psql-command
  "execute sql statements: muutto psql <env> [<sql> ...]" 
  [config]
  (let [stmts (-> config :args)]
    (-> (exec/exec (if (seq stmts)
                     config
                     (assoc config :tty "--tty")) 
                   {:stmt stmts})
        (println))))
