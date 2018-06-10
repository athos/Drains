(ns drains.core
  (:refer-clojure :exclude [into group-by])
  (:require [clojure.core :as cc]
            [drains.protocols :as p]))

(deftype ReducedDrain [val]
  p/IDrain
  (-reduced? [this] true)
  (-flush [this _] this)
  (-residual [this] val)
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
         (-residual [this] @val)
         (-attach [this xf]
           (drain xf rf @val)))))))

(defn- map-vals [f kvs]
  (persistent! (reduce-kv #(assoc! %1 %2 (f %3)) (transient kvs) kvs)))

(defn drains [ds]
  (fn []
    (let [ds (cond-> ds (seq? ds) vec)
          state (volatile! {:ds (map-vals unwrap ds)
                            :active-keys (reduce-kv (fn [ks k _] (conj ks k)) #{} ds)})]
      (reify p/IDrain
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
          (map-vals p/-residual (:ds @state)))
        (-attach [this xf]
          (drains (map-vals #(p/-attach % xf) (:ds @state))))))))

(defn fmap [f d]
  (fn []
    (let [d (volatile! (unwrap d))]
      (reify p/IDrain
        (-reduced? [this]
          (p/-reduced? @d))
        (-flush [this input]
          (vswap! d p/-flush input)
          this)
        (-residual [this]
          (f (p/-residual @d)))
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
                  (-residual [this]
                    (map-vals p/-residual @ds))
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

(defn open [drain]
  (unwrap drain))

(defn flush! [drain input]
  (p/-flush drain input))

(defn residual [drain]
  (p/-residual drain))

(defn into [drain xs]
  (-> (cc/reduce (fn [d input]
                   (let [d' (p/-flush d input)]
                     (if (p/-reduced? d')
                       (reduced d')
                       d')))
                 (unwrap drain)
                 xs)
      p/-residual))
