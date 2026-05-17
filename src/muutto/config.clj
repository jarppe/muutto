(ns muutto.config
  (:refer-clojure :exclude (get))
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [muutto.util :refer [error!]]))


(def default-config
  {:table        "muutto.migrations"
   :psql-command "psql
                    --quiet
                    --echo-errors
                    --no-readline
                    --no-psqlrc
                    --set ON_ERROR_STOP=true
                    --dbname ${config:dbname}
                    --username ${config:username}"})


(defn- deep-merge [& maps]
  (apply merge-with
         (fn [left right]
           (if (map? left)
             (deep-merge left right)
             (if (nil? right)
               left
               right)))
         (filter some? maps)))


(defn load-config [{:keys [args opts]}]
  (deep-merge default-config
              (when-let [config-file (or (let [config-file-name (-> opts :config)]
                                           (when-let [config-file (and config-file-name (io/file config-file-name))]
                                             (when-not (.canRead config-file)
                                               (error! "can't find config file: " config-file-name))
                                             config-file))
                                         (let [config-file (io/file "muutto.edn")]
                                           (when (.canRead config-file)
                                             config-file)))]
                (with-open [in (-> config-file
                                   (io/reader)
                                   (java.io.PushbackReader.))]
                  (edn/read in)))
              {:opts opts
               :args args}))


(defn get [config k & km]
  (when-let [value (get-in config (if (sequential? k)
                                    (concat k km)
                                    (cons k km)))]
    (if (string? value)
      (str/replace value
                   #"\$\{([a-z]+):([^}:]+)(?::([^}]+))?\}"
                   (fn [[_ exp-type exp-name default]]
                     (or (case exp-type
                           "env"    (System/getenv exp-name)
                           "config" (get-in config (map keyword (str/split exp-name #"/")))
                           "opt"    (get-in config [:opts (keyword exp-name)])
                           (error! "illegal config expansion type: " exp-type))
                         (if (some? default)
                           default
                           (error! "value for config expansion " exp-type ":" exp-name " not found")))))
      value)))


(defn env-config [config env]
  (let [env-config (get-in config [:env env])]
    (when-not env-config
      (error! "unknown database environment: " (name env)))
    (deep-merge config env-config)))


(defn command-config [config command]
  (let [env (or (get-in config [:opts :env])
                (get-in config [:default-env command])
                (error! "command " (name command) " requires database environment, use --env option"))]
    (env-config config env)))


(comment
  (load-config {})
  (env-config (load-config {}) :dev)
  )