(ns muutto.exec
  (:require [babashka.process :as p]
            [clojure.string :as str]
            [muutto.config :as config]))


(defn exec [config {:keys [args stmt input]}]
  (let [psql    (str/join " " [(config/get config :psql-wrapper)
                               (config/get config :psql-command)])
        args    (concat args (interleave (repeat "--command")
                                         (if (sequential? stmt)
                                           stmt
                                           [stmt])))
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
