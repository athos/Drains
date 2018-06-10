(ns drains.core
  (:refer-clojure :exclude [into group-by])
  (:require [clojure.core :as cc]
            [drains.impl.safe :as impl]
            [drains.protocols :as p]))

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

(defn into [drain xs]
  (-> (cc/reduce (fn [d input]
                   (let [d' (p/-flush d input)]
                     (if (p/-reduced? d')
                       (reduced d')
                       d')))
                 (impl/->unsafe (impl/unwrap drain))
                 xs)
      p/-residual))
