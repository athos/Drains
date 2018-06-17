(ns drains.core
  (:refer-clojure :exclude [flush group-by into reduce reductions])
  (:require [clojure.core :as cc]
            [clojure.core.reducers :as r]
            [drains.impl.safe :as impl]
            [drains.impl.utils :as utils]
            [drains.protocols :as p]))

(defn ^{:inline (fn [x] `(satisfies? p/IDrain ~x))} drain?
  "Returns true if x is a drain."
  [x]
  (satisfies? p/IDrain x))

(defn drain
  "Returns a new drain that consists of a reducing fn rf and initial value init.

  rf must be a valid reducing fn, ie. it must have arity-1 signature as well as
  arity-2. If your rf only has arity-1 signature, try using clojure.core/completing.
  This function can be passed a transducer xf optionally, in which case
  the drain's reducing fn will be (xf rf)."
  ([rf] (drain rf (rf)))
  ([rf init] (drain nil rf init))
  ([xf rf init]
   (impl/drain xf rf init)))

(defn drains
  "Returns a drain that manages multiple underlying drains.

  ds can be a vector or a map. The resulting value of aggregation will be
  the same form as ds. Thus, the result of the following expression will be
  `[0 9]`:

    (require '[drains.core :as d])
    (d/reduce (d/drains [(d/drain min ##Inf) (d/drain max ##-Inf)])
              (range 10))

  whereas that of the following will be `{:min 0, :max 9}`:

    (d/reduce (d/drains {:min (d/drain min ##Inf)
                         :max (d/drain max ##-Inf)})
              (range 10))"
  [ds]
  (impl/drains ds))

(defn fmap
  "Returns a drain that applies f to the result of the underlying drain.

  Eg.
    (require '[drains.core :as d])
    (d/reduce (d/fmap (fn [sum] {:sum sum})
                      (d/drain + 0))
              (range 10))
    ;=> {:sum 45}"
  [f d]
  (impl/fmap f d))

(defn combine-with
  "Alias for (fmap #(apply f %) (drains ds))."
  [f & ds]
  (impl/fmap #(apply f %) (impl/drains ds)))

(defn with
  "Returns a drain with a transducer attached.

  Eg.
    (require '[drains.core :as d])
    (d/reduce (d/with (filter even?)
                      (d/drain + 0))
              (range 10))
    ;=> 20"
  [xf d]
  (p/-attach (utils/unwrap d) xf))

(defn group-by
  "Returns a drain that manages an isolated copy of the underlying drain for
  each key (regarding key-fn).

  The result of the drain will be a map of key to the result of the corresponding
  underlying drain.

  Eg.
    (require '[drains.core :as d])
    (d/reduce (d/group-by odd? (d/drain conj []))
              (range 5))
    ;=> {false [0 2 4], true [1 3]}

    (d/reduce (d/group-by #(mod % 2) (d/drain + 0))
              (range 5))
    ;=> {0 6, 1 4}"
  [key-fn d]
  (impl/group-by key-fn d))

(defn open [drain]
  (utils/unwrap drain))

(defn flush [drain input]
  (p/-flush drain input))

(defn residual [drain]
  (p/-residual drain))

(defn into [drain xs]
  (cc/reduce (fn [d input]
               (let [d' (p/-flush d input)]
                 (if (p/-reduced? d')
                   (reduced d')
                   d')))
             (utils/unwrap drain)
             xs))

(defn reduce
  "Aggregation fn analogous to clojure.core/reduce using drains instead of
  reducing fns."
  [drain xs]
  (p/-residual (into (utils/->unsafe (utils/unwrap drain)) xs)))

(defn reductions
  "Aggregation fn analogous to clojure.core/reductions using drains instead of
  reducing fns."
  [drain xs]
  (letfn [(rec [drain xs]
            (lazy-seq
             (cons (p/-residual drain)
                   (when-not (p/-reduced? drain)
                     (when-first [x xs]
                       (let [drain (p/-flush drain x)]
                         (rec drain (next xs))))))))]
    (rec (utils/->unsafe (utils/unwrap drain)) xs)))

(defn fold
  "Aggregation fn analogous to clojure.core.reducers/fold using drains instead
  of reducing fns."
  ([combinef d xs] (fold 1024 combinef d xs))
  ([n combinef d xs]
   (letfn [(combinef'
             ([] (utils/->unsafe (utils/unwrap d)))
             ([x y]
              (let [x (if (drain? x) (p/-residual x) x)
                    y (if (drain? y) (p/-residual y) y)]
                (combinef x y))))
           (reducef [d input]
             (let [d' (p/-flush d input)]
               (if (p/-reduced? d')
                 (reduced d')
                 d')))]
     (r/fold n combinef' reducef xs))))
