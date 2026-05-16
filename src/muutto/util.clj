(ns muutto.util
  (:require [clojure.string :as str])
  (:import (java.time ZoneId
                      ZonedDateTime)
           (java.time.format DateTimeFormatter)))


(defn error! [& message] 
  (.println System/err (str "muutto: Error: " (str/join " " message) "\n"
                            "muutto: Try: muutto --help")) 
  (System/exit 1))


(def default-dtf (DateTimeFormatter/ofPattern "yyyy/MM/dd HH:mm:ss"))
(def local-tz (ZoneId/systemDefault))


(defn parse-datetime [datetime]
  (ZonedDateTime/parse datetime DateTimeFormatter/ISO_OFFSET_DATE_TIME))


(defn format-datetime [datetime]
  (-> datetime
      (.withZoneSameInstant local-tz)
      (.format default-dtf)))