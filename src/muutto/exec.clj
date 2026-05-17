(ns muutto.exec
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [babashka.process :as p] 
            [muutto.config :as config]
            [muutto.util :as u :refer [error!]])
  (:import (java.io File)
           (java.nio.file Path)))


(defn to-input [v]
  (cond
    (instance? File v) v
    (instance? Path v) (.toFile v)
    (and (string? v) (str/starts-with? v "@")) (io/file (subs v 1))
    :else nil))


(defn exec [config {:keys [args stmt]}]
  (let [psql    (str/join " " [(config/get config :psql-wrapper)
                               (config/get config :psql-command)])
        stmts   (if (sequential? stmt) stmt [stmt])
        input   (let [inputs (keep to-input stmt)]
                  (when (> (count inputs) 1)
                    (error! "only one sql statement can be an input file"))
                  (first inputs))
        args    (concat args (mapcat (fn [stmt]
                                       (if (to-input stmt)
                                         ["--file" "-"]
                                         ["--command" stmt]))
                                     stmts))
        _       (when (-> config :opts :verbose)
                  (println "muutto: execute:" psql (str/join " " args)))
        process (if (-> config :opts :dry-run)
                  {:exit 0
                   :out  "Dry run"}
                  (apply p/shell {:in       input
                                  :out      :string
                                  :err      :string
                                  :continue true}
                         psql
                         args))]
    (if (zero? (:exit process))
      (:out process)
      (do (.println System/err (str "psql error: " (:err process)))
          (case (:on-error config :exit)
            :exit     (System/exit 1)
            :throw    (throw (ex-info "psql exec failed" {:type :process-error
                                                          :exit (:exit process)
                                                          :out  (:out process)
                                                          :err  (:err process)}))
            :continue nil)))))
