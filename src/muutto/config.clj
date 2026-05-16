(ns muutto.config
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [muutto.util :refer [error!]]))


(def default-config
  {:mig-table    "muutto.migrations"
   :psql-command "psql
                    --quiet
                    --echo-errors
                    --no-readline
                    --no-psqlrc
                    --set ON_ERROR_STOP=true
                    --dbname ${opt:dbname:postgres}
                    --username ${opt:username:postgres}"
   :dbname       "postgres"
   :username     "postgres"
   :migrations   ["./resources/db/migrations"]
   :tests        ["./resources/db/tests"]})


(defn- deep-merge [& maps]
  (apply merge-with
         (fn [left right]
           (if (map? left)
             (deep-merge left right)
             (if (nil? right)
               left
               right)))
         (filter some? maps)))


(defn load-config [opts]
  (let [config-file (when-let [config-file (or (let [config-file-name (-> opts :config)]
                                                 (when-let [config-file (and config-file-name (io/file config-file-name))]
                                                   (when-not (.canRead config-file)
                                                     (error! "can't find config file:" config-file-name))
                                                   config-file))
                                               (let [config-file (io/file "muutto.edn")]
                                                 (when (.canRead config-file)
                                                   config-file)))]
                      (with-open [in (-> config-file
                                         (io/reader)
                                         (java.io.PushbackReader.))]
                        (edn/read in)))
        env-config  (when-let [env (-> opts :env)]
                      (let [env-config (-> config-file :env env)]
                        (when-not env-config
                          (error! "environment" (name env) "not found"))
                        env-config))
        config      (deep-merge default-config
                                config-file
                                env-config
                                opts)]
    (->> config
         (map (fn [[k value]]
                [k (if (string? value)
                     (str/replace value
                                  #"\$\{([a-z]+):([^}:]+)(?::([^}]+))?\}"
                                  (fn [[_ opt-type opt-name opt-default]]
                                    (let [opt-value (or (case opt-type
                                                          "env" (System/getenv opt-name)
                                                          "opt" (get config (keyword opt-name))
                                                          (error! "illegal config option type:" opt-type))
                                                        opt-default)]
                                      (when-not opt-value
                                        (error! "missing config variable:" opt-name))
                                      opt-value)))
                     value)]))
         (into {}))))
