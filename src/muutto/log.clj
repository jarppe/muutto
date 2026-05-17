(ns muutto.log
  (:refer-clojure :exclude (print println))
  (:require [clojure.string :as str]))


(def color-red 196)
(def color-gray 8)
(def color-green 46)
(def color-yellow 11)


(defn color [color-code & message]
  (str "\u001b[38;5;" color-code "m" (str/join message) "\u001b[m"))


(defn red [& message] (apply color color-red message))
(defn gray [& message] (apply color color-gray message))
(defn green [& message] (apply color color-green message))
(defn yellow [& message] (apply color color-yellow message))
