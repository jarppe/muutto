(ns muutto.log
  (:require [clojure.string :as str]))


(def color-red 196)
(def color-gray 8)
(def color-green 46)
(def color-yellow 11)
(def color-white 15)

(defn color [color-code & message]
  (str "\u001b[38;5;" color-code "m" (str/join message) "\u001b[m"))


(defn red [& message] (apply color color-red message))
(defn gray [& message] (apply color color-gray message))
(defn green [& message] (apply color color-green message))
(defn yellow [& message] (apply color color-yellow message))
(defn white [& message] (apply color color-white message))


(defn format-duration [start]
  (format "%.3f" (-> (- (System/nanoTime) start)
                     (java.time.Duration/ofNanos)
                     (.toMillis)
                     (double)
                     (/ 1000.0))))

(defn padding
  ([len]
   (padding len " "))
  ([len filler]
   (str/join (repeat len filler))))


(defn lpad
  ([len message]
   (lpad len message " "))
  ([len message filler]
   (str (padding (- len (count message))
                 filler)
        message)))


(defn rpad
  ([len message]
   (rpad len message " "))
  ([len message filler]
   (str message
        (padding (- len (count message))
                 filler))))


(defn table-printer [name-col-len value-col-len]
  (fn
    ([]
     (println (gray (padding (+ name-col-len 2) "-")
                    "|"
                    (padding value-col-len "-")))) 
    ([name value]
     (let [name  (str name)
           value (str value)]
       (println name
                (padding (- name-col-len (count name)))
                (gray "|")
                value)))))


(comment
  (let [p (table-printer 15 10)]
    (p "Name:" "Value:")
    (p)
    (p "foo" "bar")
    (p "foobar" "bar")
    (p "foobarboz" "bar"))
  ; Name:            | Value:
  ; -----------------|----------
  ; foo              | bar
  ; foobar           | bar
  ; foobarboz        | bar
  )