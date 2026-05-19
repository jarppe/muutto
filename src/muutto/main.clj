(ns muutto.main
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [babashka.cli :as cli]
            [muutto.util :refer [error!]]
            [muutto.config :as config]
            [muutto.command.create-command :refer [create-command drop-command]]
            [muutto.command.init-command :refer [init-command]]
            [muutto.command.migrate-command :refer [migrate-command]]
            [muutto.command.list-command :refer [list-command]]
            [muutto.command.psql-command :refer [psql-command]]
            [muutto.command.test-command :refer [test-command]])
  (:import (java.io File)))


(def cli-spec
  {:spec     {:config  {:desc     "Config file name (default is muutto.edn)"
                        :alias    :c
                        :coerce   :string
                        :validate (comp File/.canRead io/file)}
              :verbose {:desc   "Be more verbose"
                        :alias  :v
                        :coerce :boolean}
              :dry-run {:desc   "Dry run, don't actually execute anything"
                        :coerce :boolean}
              :migrate {:desc   "Migrate created database (applies to `create` command only)"
                        :coerce :boolean}
              :force   {:desc   "Force migrate file hash update"
                        :coerce :boolean}
              :help    {:desc   "Show help"
                        :alias  :h
                        :coerce :boolean}}
   :error-fn (fn rtfm! [{:keys [type cause option value]}]
               (when (= :org.babashka/cli type)
                 (.println System/err (case cause
                                        :require (format "Missing required argument: %s" option)
                                        :coerce  (format "Argument %s value is not valid" option)
                                        :validate (cond
                                                    (true? value)      (format "Argument %s requires value" option)
                                                    (= option :config) (format "Config file %s not found" value)
                                                    :else              (format "Argument %s value is not valid" option)))))
               (System/exit 1))})


(def commands (->> [#'create-command
                    #'drop-command
                    #'init-command
                    #'migrate-command
                    #'list-command
                    #'psql-command
                    #'test-command]
                   (map (fn [v]
                          {:command v
                           :name    (-> v (meta) :name (name) (str/split #"-") (first))
                           :doc     (-> v (meta) :doc)}))))


(defn help! []
  (println "muutto - Database migrations the easy way")
  (println "usage: muutto [<opts>] <command> <env> [<args>]")
  (println "where:")
  (println "  opts:")
  (doseq [line (-> (cli/format-opts (merge cli-spec {:order (vec (keys (:spec cli-spec)))}))
                   (str/split-lines))]
    (println "   " line))
  (println "  command:")
  (doseq [{:keys [name doc]} commands]
    (println (format "    %s %s %s"
                     name
                     (str/join (repeat (- 10 (count name)) "-"))
                     doc)))
  (println "")
  (println "All commands require a target database environment to operate on. The database")
  (println "environments are defined in `muutto.edn` configuration file.")
  (println "")
  (println "For more complete documentation see https://codeberg.org/jarppe/muutto")
  (System/exit 0))


(defn -main [args]
  (when (some #{"-h" ":h" "--help" ":help"} args)
    (help!))
  (let [opts    (cli/parse-args args cli-spec)
        action  (or (-> opts :args (first))
                    (help!))
        command (or (some (fn [{:keys [name command]}]
                            (when (= name action)
                              command))
                          commands)
                    (error! (str "unknown command: " action)))
        config  (config/load-config opts)]
    (command config)))

