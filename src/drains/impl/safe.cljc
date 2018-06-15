(ns drains.impl.safe
  (:refer-clojure :exclude [group-by])
  (:require [clojure.core :as cc]
            [drains.protocols :as p]
            [drains.impl.reduced :as reduced]
            [drains.impl.unsafe :as unsafe]
            [drains.impl.utils :as utils]))

(defn drain [xform rf init]
  (fn []
    (let [rf (cond-> rf xform xform)
          val (volatile! init)]
      (reify
        p/IDrain
        (-reduced? [this] false)
        (-flush [this input]
          (let [val' (rf @val input)]
            (if (reduced? val')
              (reduced/->ReducedDrain (rf @val'))
              (do (vreset! val val')
                  this))))
        (-residual [this] (rf @val))
        p/Attachable
        (-attach [this xf]
          (drain xf rf @val))
        p/ToUnsafe
        (->unsafe [this]
          (unsafe/->UnsafeDrain rf @val))))))

(defn drains [ds]
  (fn []
    (let [ds (cond-> ds (seq? ds) vec)
          state (volatile! {:ds (utils/map-vals utils/unwrap ds)
                            :active-keys (reduce-kv (fn [ks k _] (conj ks k)) #{} ds)})]
      (reify
        p/IDrain
        (-reduced? [this] (empty? (:active-keys @state)))
        (-flush [this input]
          (vswap! state
                  (fn [state]
                    (reduce-kv (fn [state k drain]
                                 (let [drain' (p/-flush drain input)]
                                   (if (identical? drain drain')
                                     state
                                     (cond-> (update state :ds assoc k drain')
                                       (p/-reduced? drain')
                                       (update :active-keys disj k)))))
                               state
                               (:ds state))))
          this)
        (-residual [this]
          (utils/map-vals p/-residual (:ds @state)))
        p/Attachable
        (-attach [this xf]
          (drains (utils/map-vals #(p/-attach % xf) (:ds @state))))
        p/ToUnsafe
        (->unsafe [this]
          (let [ds (utils/map-vals utils/->unsafe (:ds @state))]
            (or (when (vector? ds)
                  (case (count ds)
                    2 (unsafe/->UnsafeDrains2 (nth ds 0) (nth ds 1) false false ds)
                    3 (unsafe/->UnsafeDrains3 (nth ds 0) (nth ds 1) (nth ds 2)
                                              false false false ds)
                    nil))
                (unsafe/->UnsafeDrains ds (transient (:active-keys @state)) false))))))))

(defn fmap [f d]
  (fn []
    (let [d (volatile! (utils/unwrap d))]
      (reify
        p/IDrain
        (-reduced? [this]
          (p/-reduced? @d))
        (-flush [this input]
          (vswap! d p/-flush input)
          this)
        (-residual [this]
          (f (p/-residual @d)))
        p/Attachable
        (-attach [this xf]
          (fmap f (p/-attach @d xf)))
        p/ToUnsafe
        (->unsafe [this]
          (unsafe/->UnsafeFmap f (utils/->unsafe @d) false))))))

(defn group-by [key-fn d]
  (fn []
    (let [ds (volatile! {})]
      (letfn [(make [rf reduced?]
                (reify
                  p/IDrain
                  (-reduced? [this] reduced?)
                  (-flush [this input]
                    (let [d (rf this input)]
                      (if (cc/reduced? d)
                        (make rf true)
                        d)))
                  (-residual [this]
                    (utils/map-vals p/-residual @ds))
                  p/Attachable
                  (-attach [this xf]
                    #(make (xf rf) reduced?))))
              (insert [this input]
                (let [key (key-fn input)]
                  (when-not (contains? @ds key)
                    (let [d (unwrap d)]
                      (vswap! ds assoc key d)))
                  (let [d (get @ds key)
                        d' (p/-flush d input)]
                    (when-not (identical? d d')
                      (vswap! ds assoc key d')))
                  this))]
        (make insert false)))))
