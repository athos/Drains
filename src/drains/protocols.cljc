(ns drains.protocols)

(defprotocol IDrain
  (-reduced? [this])
  (-flush [this input])
  (-residue [this])
  (-attach [this xf]))
