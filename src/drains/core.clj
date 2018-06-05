(ns drains.core
  (:refer-clojure :exclude [into])
  (:require [clojure.core :as cc]))

(defprotocol IDrain
  (-reduced? [this])
  (-flush [this input])
  (-residue [this])
  (-attach [this xf]))

(deftype ReducedDrain [val]
  IDrain
  (-reduced? [this] true)
  (-flush [this _] this)
  (-residue [this] val)
  (-attach [this _] this))

(defn- unwrap [x]
  (if (fn? x) (x) x))

(defn drain
  ([rf] (drain rf (rf)))
  ([rf init] (drain nil rf init))
  ([xform rf init]
   (fn []
     (let [rf (cond-> rf xform xform)
           val (volatile! init)]
       (reify IDrain
         (-reduced? [this] false)
         (-flush [this input]
           (let [val' (rf @val input)]
             (if (reduced? val')
               (->ReducedDrain (rf @val'))
               (do (vreset! val val')
                   this))))
         (-residue [this] @val)
         (-attach [this xf]
           (drain xf rf @val)))))))

(defn- map-vals [f kvs]
  (persistent! (reduce-kv #(assoc! %1 %2 (f %3)) (transient kvs) kvs)))

(defn drains [ds]
  (fn []
    (let [ds (volatile! (map-vals unwrap (cond-> ds (seq? ds) vec)))]
      (reify IDrain
        (-reduced? [this] false)
        (-flush [this input]
          (vreset! ds
                   (reduce-kv (fn [ds k drain]
                                (let [drain' (-flush drain input)]
                                  (if (identical? drain drain')
                                    ds
                                    (assoc ds k drain'))))
                              @ds
                              @ds))
          this)
        (-residue [this]
          (map-vals -residue @ds))
        (-attach [this xf]
          (drains (map-vals #(-attach % xf) @ds)))))))

(defn fmap [f d]
  (fn []
    (let [d (volatile! (unwrap d))]
      (reify IDrain
        (-reduced? [this]
          (-reduced? @d))
        (-flush [this input]
          (vswap! d -flush input)
          this)
        (-residue [this]
          (f (-residue @d)))
        (-attach [this xf]
          (fmap f (-attach @d xf)))))))

(defn combine-with [f & ds]
  (fmap #(apply f %) (drains ds)))

(defn with [xf d]
  (-attach (unwrap d) xf))

(defn by-key [key-fn d]
  (fn []
    (let [ds (volatile! {})]
      (reify IDrain
        (-reduced? [this] false)
        (-flush [this input]
          (let [key (key-fn input)]
            (if-let [d (get @ds key)]
              (-flush d input)
              (let [d (unwrap d)]
                (vswap! ds assoc key d)
                (-flush d input)))
            this))
        (-residue [this]
          (map-vals -residue @ds))
        (-attach [this xf]
          (by-key key-fn (-attach (unwrap d) xf)))))))

(defn into [drain xs]
  (-> (cc/reduce (fn [d input]
                   (let [d' (-flush d input)]
                     (if (-reduced? d')
                       (reduced d')
                       d')))
                 (unwrap drain)
                 xs)
      -residue))
