(ns drains.protocols
  (:refer-clojure :exclude [-flush]))

(defprotocol IDrain
  (-reduced? [this])
  (-flush [this input])
  (-residue [this])
  (-attach [this xf]))
