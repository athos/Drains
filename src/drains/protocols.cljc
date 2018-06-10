(ns drains.protocols
  (:refer-clojure :exclude [-flush]))

(defprotocol IDrain
  (-reduced? [this])
  (-flush [this input])
  (-residual [this]))

(defprotocol Attachable
  (-attach [this xf]))

(defprotocol ToUnsafe
  (->unsafe [this]))
