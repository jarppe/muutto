(ns muutto.main
  (:require [clojure.string :as str]
            [babashka.cli :as cli]
            [babashka.process :as p]
            [babashka.fs :as fs])
  (:import (java.io File)
           (java.nio.file Path)))



(defn exit [code]
  (System/exit code))


(defn dir-exists? [x] false)


(defn rtfm! [{:keys [spec type cause msg option] :as data}]
  (when (= :org.babashka/cli type)
    (case cause
      :require (println (format "Missing required argument: %s\n" option))
      :validate (println (format "%s does not exist!\n" msg))))
  (exit 1))


(def cli-spec
  {:spec {:num {:coerce :long
                :desc "Number of some items"
                :alias :n                     ; adds -n alias for --num
                :validate pos?                ; tests if supplied --num >0
                :require true}                ; --num,-n is required
          :dir {:desc "Directory name to do stuff"
                :alias :d
                :validate dir-exists?}        ; tests if --dir exists
          :flag {:coerce :boolean             ; defines a boolean flag
                 :desc "I am just a flag"}
          :help {:desc "Show command-line arguments"
                 :alias :h}}
   :error-fn rtfm!})


(defn migrate
  "Migrate database"
  [_]
  (println "Migrating..."))


(defn create
  "Create database"
  [_]
  (println "Creating..."))


(defn drop
  "Create database"
  [_]
  (println "Dropping..."))


(defn test
  "Testing database"
  [_]
  (println "Testing..."))


(def commands [#'migrate
               #'create
               #'drop
               #'test])


(defn help! []
  (println "muutto - Database migrations the easy way")
  (println "usage: muutto <command> <args>")
  (println "command:")
  (doseq [command (map meta commands)
          :let [command-name (-> command :name (name))
                command-doc  (-> command :doc)]]
    (println (format "  %s %s %s" 
                     command-name
                     (str/join (repeat (- 12 (count command-name)) "-"))
                     command-doc)))
  (println "args:")
  (println (cli/format-opts (merge cli-spec {:order (vec (keys (:spec cli-spec)))})))
  (println "")
  (println "For more complete documentation see https://codeberg.org/jarppe/muutto")
  (exit 0))




(defn -main [[command & args]]
  (when (or (str/blank? command) 
            (#{"-h" ":h" "--help" ":help" "help"} command)
            (some #{"-h" ":h" "--help" ":help"} args))
    (help!))
  (let [opts (cli/parse-args args cli-spec)]
    (if (or (:help opts) (:h opts))
      (help!)
      (println "Here are your cli args!:" opts))))
