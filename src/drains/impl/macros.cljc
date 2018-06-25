(ns drains.impl.macros
  (:require [clojure.core :as cc]
            [drains.protocols :as p]
            [drains.impl.reduced :as reduced]))

(defn collect-keys [ds]
  (reduce-kv (fn [ks k _] (conj ks k)) [] ds))

(defmacro def-unsafe-drains-n [& ns]
  `(do
     ~@(for [n ns
             :let [d-syms (map #(symbol (str 'd %)) (range 1 (inc n)))
                   reduced?-syms (map #(symbol (str 'd % '-reduced?))
                                      (range 1 (inc n)))]]
         `(deftype ~(symbol (str 'UnsafeDrains n))
              [~'rf
               ~@(map #(with-meta % {:unsynchronized-mutable true}) d-syms)
               ~@(map #(with-meta % {:unsynchronized-mutable true}) reduced?-syms)
               ~'ds]
            p/IDrain
            (~'-reduced? [this#] (and ~@reduced?-syms))
            (~'-flush [this# input#]
             (let [d# (~'rf this# input#)]
               (if (cc/reduced? d#)
                 (reduced/->ReducedDrain (p/-residual this#))
                 this#)))
            (~'-residual [this#]
             ~(let [ks (map (fn [_] (gensym 'k)) d-syms)]
                `(let [~(vec ks) (collect-keys ~'ds)]
                   (-> ~'ds
                       ~@(for [[k d] (map vector ks d-syms)]
                           `(assoc ~k (p/-residual ~d)))))))
            p/Inserter
            (-insert! [this# ~'input]
              ~@(for [[d reduced?] (map vector d-syms reduced?-syms)]
                  `(let [d# (p/-flush ~d ~'input)]
                     (when-not (identical? ~d d#)
                       (set! ~d d#)
                       (when (p/-reduced? d#)
                         (set! ~reduced? true))))))))))
