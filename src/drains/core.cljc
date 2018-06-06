(ns drains.core
  (:refer-clojure :exclude [into group-by])
  (:require [clojure.core :as cc]
            [drains.protocols :as p]))

(deftype ReducedDrain [val]
  p/IDrain
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
       (reify p/IDrain
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
      (reify p/IDrain
        (-reduced? [this] false)
        (-flush [this input]
          (vreset! ds
                   (reduce-kv (fn [ds k drain]
                                (let [drain' (p/-flush drain input)]
                                  (if (identical? drain drain')
                                    ds
                                    (assoc ds k drain'))))
                              @ds
                              @ds))
          this)
        (-residue [this]
          (map-vals p/-residue @ds))
        (-attach [this xf]
          (drains (map-vals #(p/-attach % xf) @ds)))))))

(defn fmap [f d]
  (fn []
    (let [d (volatile! (unwrap d))]
      (reify p/IDrain
        (-reduced? [this]
          (p/-reduced? @d))
        (-flush [this input]
          (vswap! d p/-flush input)
          this)
        (-residue [this]
          (f (p/-residue @d)))
        (-attach [this xf]
          (fmap f (p/-attach @d xf)))))))

(defn combine-with [f & ds]
  (fmap #(apply f %) (drains ds)))

(defn with [xf d]
  (p/-attach (unwrap d) xf))

(defn group-by [key-fn d]
  (fn []
    (let [ds (volatile! {})]
      (letfn [(make [rf reduced?]
                (reify p/IDrain
                  (-reduced? [this] reduced?)
                  (-flush [this input]
                    (let [d (rf this input)]
                      (if (cc/reduced? d)
                        (make rf true)
                        d)))
                  (-residue [this]
                    (map-vals p/-residue @ds))
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

(defn into [drain xs]
  (-> (cc/reduce (fn [d input]
                   (let [d' (p/-flush d input)]
                     (if (p/-reduced? d')
                       (reduced d')
                       d')))
                 (unwrap drain)
                 xs)
      p/-residue))
