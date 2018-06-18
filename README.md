# Drains
[![Clojars Project](https://img.shields.io/clojars/v/drains.svg)](https://clojars.org/drains)
[![CircleCI](https://circleci.com/gh/athos/Drains.svg?style=shield)](https://circleci.com/gh/athos/Drains)
[![codecov](https://codecov.io/gh/athos/Drains/branch/master/graph/badge.svg)](https://codecov.io/gh/athos/Drains)

Drains: A new abstraction for flexible and efficient sequence aggregation in Clojure(Script)

A drain is a stateful object that holds a reducing fn and an accumulated value. Drains can be used as composable and reusable building blocks for construction of sequence aggregation.
This library provides several easy ways to combining multiple drains and to produce a new drain from another one, and also a couple of custom aggregation functions such as `reduce`, `reductions` and `fold`.

## Installation

Add the following to your `:dependencies`:

[![Clojars Project](https://clojars.org/drains/latest-version.svg)](https://clojars.org/drains)

Or, if you want to use the library via [Clojure CLI tool](https://clojure.org/guides/deps_and_cli), add the following to your `deps.edn` instead:

```clj
{...
 :deps {...
        athos/drains {:git/url "https://github.com/athos/Drains.git"
                      :sha "<commit sha>"}
        ...}
 ...}
```

## Usage

```clj
(require '[drains.core :as d])

(d/reduce (d/drain (filter even?) + 0) (range 10))
;; => 20
;; == (+ 0 2 4 6 8)

(d/reduce (d/drains [(d/drain min Long/MAX_VALUE)
                     (d/drain max Long/MIN_VALUE)])
          (range 10))
;; => [0 9]

(d/reduce (d/drains {:min (d/drain min Long/MAX_VALUE)
                     :max (d/drain max Long/MIN_VALUE)})
          (range 10))
;; => {:min 0, :max 9}

(d/reduce (d/fmap (fn [sum] {:sum sum})
                  (d/drain +))
          (range 10))
;; => {:sum 45}

(d/reduce (d/combine-with (fn [sum count] {:sum sum :count count})
                          (d/drain +)
                          (d/drain (map (constantly 1)) + 0))
          (range 10))
;; => {:sum 45 :count 10}

(d/reduce (d/with (map (partial array-map :val))
                  (d/drain conj []))
          (range 3))
;; => [{:val 0} {:val 1} {:val 2}]

;; And, you can combine them with one another however you like

(let [items (d/fmap (fn [items] {:items items}) (d/drain conj))
      merge-mean (fn [d]
                   (d/combine-with (fn [v sum count]
                                     (assoc v
                                            :sum sum
                                            :count count
                                            :mean (/ sum (double count))))
                                   d
                                   (d/drain +)
                                   (d/drain (map (constantly 1)) + 0)))]
  (d/reduce (d/drains {:evens (d/with (filter even?)
                                      (merge-mean items))
                       :odds (d/with (filter odd?)
                                     (merge-mean items))})
            (range 10)))
;; => {:evens {:items [0 2 4 6 8]
;;             :sum 20
;;             :count 5
;;             :mean 4.0}
;;     :odds {:items [1 3 5 7 9]
;;            :sum 25
;;            :count 5
;;            :mean 5.0}}

;; Or, in this paticular case, you can simplify the code into something like
;; the following:

(let [items (d/combine-with (fn [items sum count]
                              {:items items
                               :sum sum
                               :count count
                               :mean (/ sum (double count))})
                            (d/drain conj)
                            (d/drain +)
                            (d/drain (map (constantly 1)) + 0))]
  (d/reduce (d/drains {:evens (d/with (filter even?) items)
                       :odds (d/with (filter odd?) items)})
            (range 10)))
```

## Related works

- [xforms](https://github.com/cgrand/xforms)
    - Provides various utility transducers and reducing fns
    - Those utilities can be effectively used from Drains through drain abstraction
- [parallel](https://github.com/reborg/parallel)
    - Defines a version of aggregation fns enabled to perform parallel execution a la `clojure.core.reducers/fold`
    - Although Drains also provides the `fold` fn for that purpose, `parallel` functions often show better performance for parallel execution in paticular.

## License

Copyright © 2018 Shogo Ohta

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
