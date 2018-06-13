(ns drains.impl.unsafe
  (:require [clojure.core :as cc]
            [drains.protocols :as p]
            [drains.impl.reduced :as reduced]))

(deftype UnsafeDrain [rf ^:unsynchronized-mutable val]
  p/IDrain
  (-reduced? [this] false)
  (-flush [this input]
    (let [val' (rf val input)]
      (if (cc/reduced? val')
        (reduced/->ReducedDrain (rf @val'))
        (do (set! val val')
            this))))
  (-residual [this] (rf val)))

;; FIXME: we can't change mutable fields from within fn exprs, so have to bypass
;; that restriction by using dedicated protocol methods

(defprotocol DrainsUpdater
  (update-drains! [this drains']))

(defprotocol ReducedUpdater
  (update-reduced! [this]))

(deftype UnsafeDrains [^:unsynchronized-mutable ds
                       active-keys
                       ^:unsynchronized-mutable reduced?]
  p/IDrain
  (-reduced? [this] reduced?)
  (-flush [this input]
    (reduce-kv (fn [_ k drain]
                 (let [drain' (p/-flush drain input)]
                   (when-not (identical? drain drain')
                     (update-drains! this (assoc ds k drain'))
                     (when (p/-reduced? drain')
                       (disj! active-keys k)
                       (when (zero? (count active-keys))
                         (update-reduced! this))))))
               nil
               ds)
    this)
  (-residual [this]
    (reduce-kv (fn [ds k drain] (assoc ds k (p/-residual drain))) ds ds))
  DrainsUpdater
  (update-drains! [this drains']
    (set! ds drains'))
  ReducedUpdater
  (update-reduced! [this]
    (set! reduced? true)))

(defn- collect-keys [ds]
  (reduce-kv (fn [ks k _] (conj ks k)) [] ds))

(deftype UnsafeDrains2 [^:unsynchronized-mutable d1
                        ^:unsynchronized-mutable d2
                        ^:unsynchronized-mutable d1-reduced?
                        ^:unsynchronized-mutable d2-reduced?
                        ds]
  p/IDrain
  (-reduced? [this] (and d1-reduced? d2-reduced?))
  (-flush [this input]
    (let [d' (p/-flush d1 input)]
      (when-not (identical? d1 d')
        (set! d1 d')
        (when (p/-reduced? d')
          (set! d1-reduced? true))))
    (let [d' (p/-flush d2 input)]
      (when-not (identical? d2 d')
        (set! d2 d')
        (when (p/-reduced? d')
          (set! d2-reduced? true))))
    this)
  (-residual [this]
    (let [[k1 k2] (collect-keys ds)]
      (-> ds
          (assoc k1 (p/-residual d1))
          (assoc k2 (p/-residual d2))))))

(deftype UnsafeDrains3 [^:unsynchronized-mutable d1
                        ^:unsynchronized-mutable d2
                        ^:unsynchronized-mutable d3
                        ^:unsynchronized-mutable d1-reduced?
                        ^:unsynchronized-mutable d2-reduced?
                        ^:unsynchronized-mutable d3-reduced?
                        ds]
  p/IDrain
  (-reduced? [this] (and d1-reduced? d2-reduced? d3-reduced?))
  (-flush [this input]
    (let [d' (p/-flush d1 input)]
      (when-not (identical? d1 d')
        (set! d1 d')
        (when (p/-reduced? d')
          (set! d1-reduced? true))))
    (let [d' (p/-flush d2 input)]
      (when-not (identical? d2 d')
        (set! d2 d')
        (when (p/-reduced? d')
          (set! d2-reduced? true))))
    (let [d' (p/-flush d3 input)]
      (when-not (identical? d3 d')
        (set! d3 d')
        (when (p/-reduced? d')
          (set! d3-reduced? true))))
    this)
  (-residual [this]
    (let [[k1 k2 k3] (collect-keys ds)]
      (-> ds
          (assoc k1 (p/-residual d1))
          (assoc k2 (p/-residual d2))
          (assoc k3 (p/-residual d3))))))

(deftype UnsafeFmap [f
                     ^:unsynchronized-mutable d
                     ^:unsynchronized-mutable reduced?]
  p/IDrain
  (-reduced? [this] reduced?)
  (-flush [this input]
    (set! d (p/-flush d input))
    this)
  (-residual [this]
    (f (p/-residual d))))
