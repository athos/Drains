(ns drains.impl.utils
  (:require [drains.protocols :as p]))

(defn unwrap [x]
  (if (fn? x) (x) x))

(defn ->unsafe [x]
  (if (satisfies? p/ToUnsafe x) (p/->unsafe x) x))

(defn map-vals [f xs]
  (persistent! (reduce-kv #(assoc! %1 %2 (f %3)) (transient xs) xs)))
