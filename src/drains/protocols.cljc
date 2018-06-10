(ns drains.protocols
  (:refer-clojure :exclude [-flush]))

(defprotocol IDrain
  (-reduced? [this])
  (-flush [this input])
  (-residual [this])
  (-attach [this xf]))
