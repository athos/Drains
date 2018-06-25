(ns drains.utils
  (:refer-clojure :exclude [count frequencies max max-key min min-key sort sort-by])
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

(defn min-key [f init]
  (d/drain (completing
            (fn [x y]
              (if (< (f x) (f y)) x y)))
           init))

(defn max-key [f init]
  (d/drain (completing
            (fn [x y]
              (if (> (f x) (f y)) x y)))
           init))

(defn sort-by [f]
  (d/drain (completing #(update %1 (f %2) (fnil conj []) %2)
                       #(apply concat (vals %)))
           (sorted-map)))

(defn sort []
  (sort-by identity))
