(ns muutto.command.list-command
  (:require [clojure.string :as str]
            [muutto.util :as u]
            [muutto.mig :as mig]))


(defn list-command
  "List migrations"
  [config _]
  (let [migrations (mig/applied-migrations config)
        file-len   (->> migrations (map (comp count second)) (reduce max))
        fmt        (format "%%-%ds | %%s" (+ file-len 2))]
    (println (format fmt "File:" "Applied:"))
    (println (str (str/join (repeat (+ file-len 2) "-"))
                  "-|--------------------"))
    (doseq [{:keys [file-name applied]} migrations]
      (println (format fmt file-name (u/format-datetime applied))))))
