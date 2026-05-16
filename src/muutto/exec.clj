(ns muutto.exec
  (:require [babashka.process :as p]
            [clojure.string :as str]))


(defn exec [config {:keys [args stmt input]}]
  (let [psql    (str (:psql-wrapper config)
                     (when (:psql-wrapper config) " ")
                     (:psql-command config))
        args    (concat args (interleave (repeat "--command")
                                         (if (sequential? stmt)
                                           stmt
                                           [stmt])))
        _       (when (:verbose config)
                  (println "muutto: execute:" psql (str/join " " args)))
        process (apply p/shell {:in       input
                                :out      :string
                                :err      :string
                                :continue true}
                       psql
                       args)]
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
