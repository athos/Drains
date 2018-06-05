(ns drains.core
  (:refer-clojure :exclude [into])
  (:require [clojure.core :as cc]))

(defprotocol IDrain
  (-flush [this input])
  (-attach [this xf])
  (-residue [this]))

(deftype ReducedDrain [val]
  IDrain
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
         (-flush [this input]
           (let [val' (rf @val input)]
             (if (reduced? val')
               (->ReducedDrain (rf @val'))
               (do (vreset! val val')
                   this))))
         (-residue [this] @val)
         (-attach [this xf]
           (drain xf rf @val)))))))

(defn drains [ds]
  (fn []
    (let [map' (fn [f ds]
                 (->> ds
                      (reduce-kv #(assoc! %1 %2 (f %3)) (transient ds))
                      persistent!))
          ds (volatile! (map' unwrap (cond-> ds (seq? ds) vec)))]
      (reify IDrain
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
          (map' -residue @ds))
        (-attach [this xf]
          (drains (map' #(-attach % xf) @ds)))))))

(defn fmap [f d]
  (fn []
    (let [d (volatile! (unwrap d))]
      (reify IDrain
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

(defn into [drain xs]
  (-residue (cc/reduce -flush (unwrap drain) xs)))

(defn by-key [key-fn d]
  (fn []
    (let [ds (volatile! {})]
      (reify IDrain
        (-flush [this input]
          (let [key (key-fn input)]
            (if-let [d (get @ds key)]
              (-flush d input)
              (let [d (unwrap d)]
                (vswap! ds assoc key d)
                (-flush d input)))
            this))
        (-residue [this]
          (cc/into {} (map (fn [[k d]] [k (-residue d)])) @ds))
        (-attach [this xf]
          (by-key key-fn (-attach (unwrap d) xf)))))))
