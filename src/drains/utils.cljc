(ns drains.utils
  (:refer-clojure :exclude [count frequencies max max-key min min-key sort sort-by])
  (:require [clojure.core :as cc]
            [drains.core :as d]
            [drains.protocols :as p]))

#?(:clj (set! *unchecked-math* true))

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
   (d/with-unsafe (d/drain (completing (fn [n _] (inc n))) init)
     (fn []
       (let [arr (long-array [init])]
         (reify p/IDrain
           (-reduced? [this] false)
           (-flush [this input]
             (aset arr 0 (inc (aget arr 0)))
             this)
           (-residual [this] (aget arr 0))))))))

(defn frequencies []
  (d/group-by identity (count)))

(defn mean []
  (d/combine-with (fn [sum count] (/ sum (double count)))
                  (sum)
                  (count)))

(defn min
  ([] (min ;; ##Inf
           #?(:clj Double/POSITIVE_INFINITY
              :cljs js/Infinity)))
  ([init]
   (d/drain cc/min init)))

(defn max
  ([] (max ;; ##-Inf
           #?(:clj Double/NEGATIVE_INFINITY
              :cljs js/-Infinity)))
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
