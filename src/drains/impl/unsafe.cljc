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
