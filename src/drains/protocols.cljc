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

(defprotocol WithUnsafe
  (-with-unsafe [this unsafe-fn]))

;; intended for internal use
(defprotocol Updater
  (-update! [this input]))
