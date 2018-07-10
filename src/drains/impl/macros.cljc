(ns drains.impl.macros
  (:require [clojure.core :as cc]
            [drains.protocols :as p]
            [drains.impl.reduced :as reduced]))

(defmacro def-attachable-drain [type-name fields methods & more]
  (let [rf (gensym 'rf)
        emit-methods (fn [methods]
                       (for [[mname method] methods
                             :let [[args & body] method]]
                         `(~(symbol (name mname)) ~args ~@body)))]
    `(do
       (deftype ~type-name ~fields
         p/IDrain
         ~(let [[args & body] (:-flush methods)]
            `(~'-flush ~args ~@body ~(first args)))
         ~@(emit-methods (dissoc methods :-flush))
         ~@more)
       (deftype ~(symbol (str type-name 'Attachable)) ~(vec (cons rf fields))
         p/IDrain
         (~'-flush [this# input#]
          (let [d# (~rf this# input#)]
            (if (cc/reduced? d#)
              (reduced/->ReducedDrain (p/-residual this#))
              this#)))
         ~@(emit-methods (dissoc methods :-flush))
         p/Updater
         ~(let [[args & body] (:-flush methods)]
            `(~'-update! ~args ~@body))
         ~@more))))

(defn collect-keys [ds]
  (reduce-kv (fn [ks k _] (conj ks k)) [] ds))

(defmacro def-unsafe-drains-n [& ns]
  `(do
     ~@(for [n ns
             :let [d-syms (map #(symbol (str 'd %)) (range 1 (inc n)))
                   reduced?-syms (map #(symbol (str 'd % '-reduced?))
                                      (range 1 (inc n)))]]
         `(def-attachable-drain ~(symbol (str 'UnsafeDrains n))
            [~@(map #(with-meta % {:unsynchronized-mutable true}) d-syms)
             ~@(map #(with-meta % {:unsynchronized-mutable true}) reduced?-syms)
             ~'ds]
            {:-reduced? ([this#] (and ~@reduced?-syms))
             :-flush ([this# ~'input]
                      ~@(for [[d reduced?] (map vector d-syms reduced?-syms)]
                          `(let [d# (p/-flush ~d ~'input)]
                             (when-not (identical? ~d d#)
                               (set! ~d d#)
                               (when (p/-reduced? d#)
                                 (set! ~reduced? true))))))
             :-residual ([this#]
                         ~(let [ks (map (fn [_] (gensym 'k)) d-syms)]
                            `(let [~(vec ks) (collect-keys ~'ds)]
                               (-> ~'ds
                                   ~@(for [[k d] (map vector ks d-syms)]
                                       `(assoc ~k (p/-residual ~d)))))))}))))
