(ns drains.utils
  (:refer-clojure :exclude [count frequencies min max])
  (:require [clojure.core :as cc]
            [drains.core :as d]))

(defn sum
  ([] (sum 0))
  ([init]
   (d/drain + init)))

(defn sum-by
  ([f] (sum-by f 0))
  ([f init]
   (d/with (map f) (sum init))))

(defn count
  ([] (count 0))
  ([init]
   (d/drain (completing (fn [n _] (inc n))) init)))

(defn frequencies []
  (d/group-by identity (count)))

(defn average []
  (d/combine-with (fn [sum count] (/ sum (double count)))
                  (sum)
                  (count)))

(defn min
  ([] (min ##Inf))
  ([init]
   (d/drain cc/min init)))

(defn max
  ([] (max ##-Inf))
  ([init]
   (d/drain cc/max init)))

(defn min-by
  ([f] (min-by f ##Inf))
  ([f init]
   (d/with (map f) (min init))))

(defn max-by
  ([f] (max-by f ##-Inf))
  ([f init]
   (d/with (map f) (max init))))
