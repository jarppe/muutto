(ns muutto.util
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.time ZoneId
                      ZonedDateTime)
           (java.time.format DateTimeFormatter) 
           (java.io File)
           (java.nio.file Path)))


(def exit-on-error? true)


(defn error! [& message] 
  (.println System/err (str "muutto: Error: " (str/join message) "\n"
                            "muutto: Try: muutto --help")) 
  (when exit-on-error? 
    (System/exit 1)))


(def default-dtf (DateTimeFormatter/ofPattern "yyyy/MM/dd HH:mm:ss"))
(def local-tz (ZoneId/systemDefault))


(defn parse-datetime [datetime]
  (when datetime
    (ZonedDateTime/parse datetime DateTimeFormatter/ISO_OFFSET_DATE_TIME)))


(defn format-datetime [datetime]
  (when datetime
    (-> datetime
        (.withZoneSameInstant local-tz)
        (.format default-dtf))))


(defn to-path [file]
  (cond
    (instance? Path file)   file
    (instance? File file)   (.toPath file)
    (instance? String file) (.toPath (io/file file))
    :else (throw (ex-info (format "don't know hot to coerce path from %s (%s)"
                                  (pr-str file)
                                  (if (some? file)
                                    (.getName (.getClass file))
                                    "nil"))
                          {}))))


(def cwd (to-path "."))


