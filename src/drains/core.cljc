(ns drains.core
  (:refer-clojure :exclude [reduce reductions group-by])
  (:require [clojure.core :as cc]
            [clojure.core.reducers :as r]
            [drains.impl.safe :as impl]
            [drains.protocols :as p]))

(defn ^{:inline (fn [x] `(satisfies? p/IDrain ~x))} drain? [x]
  (satisfies? p/IDrain x))

(defn drain
  ([rf] (drain rf (rf)))
  ([rf init] (drain nil rf init))
  ([xform rf init]
   (impl/drain xform rf init)))

(defn drains [ds]
  (impl/drains ds))

(defn fmap [f d]
  (impl/fmap f d))

(defn combine-with [f & ds]
  (impl/fmap #(apply f %) (impl/drains ds)))

(defn with [xf d]
  (p/-attach (impl/unwrap d) xf))

(defn group-by [key-fn d]
  (impl/group-by key-fn d))

(defn open [drain]
  (impl/unwrap drain))

(defn flush! [drain input]
  (p/-flush drain input))

(defn residual [drain]
  (p/-residual drain))

(defn into! [drain xs]
  (cc/reduce (fn [d input]
               (let [d' (p/-flush d input)]
                 (if (p/-reduced? d')
                   (reduced d')
                   d')))
             (impl/unwrap drain)
             xs))

(defn reduce [drain xs]
  (p/-residual (into! (impl/->unsafe (impl/unwrap drain)) xs)))

(defn reductions [drain xs]
  (letfn [(rec [drain xs]
            (lazy-seq
             (cons (p/-residual drain)
                   (when-not (p/-reduced? drain)
                     (when-first [x xs]
                       (let [drain (p/-flush drain x)]
                         (rec drain (next xs))))))))]
    (rec (impl/->unsafe (impl/unwrap drain)) xs)))

(defn fold
  ([combinef d xs] (fold 1024 combinef d xs))
  ([n combinef d xs]
   (letfn [(combinef'
             ([] (impl/->unsafe (impl/unwrap d)))
             ([x y]
              (let [x (if (drain? x) (p/-residual x) x)
                    y (if (drain? y) (p/-residual y) y)]
                (combinef x y))))
           (reducef [d input]
             (let [d' (p/-flush d input)]
               (if (p/-reduced? d')
                 (reduced d')
                 d')))]
     (r/fold n combinef' reducef xs))))
