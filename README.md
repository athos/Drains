# Drains
[![Clojars Project](https://img.shields.io/clojars/v/drains.svg)](https://clojars.org/drains)
[![CircleCI](https://circleci.com/gh/athos/Drains.svg?style=shield)](https://circleci.com/gh/athos/Drains)
[![codecov](https://codecov.io/gh/athos/Drains/branch/master/graph/badge.svg)](https://codecov.io/gh/athos/Drains)

Drains: A new abstraction for flexible and efficient sequence aggregation in Clojure(Script)

A drain is a stateful object that consists of a reducing fn and an accumulated value. Drains can be used as composable and reusable building blocks for constructing sequence aggregation.
This library provides several easy ways to combining multiple drains and to produce a new drain from another one, and also provides a couple of custom aggregation functions such as `reduce`, `reductions` and `fold`.

## Table of contents

- [Installation](#installation)
- [Usage](#usage)
    - [`d/drain`, `d/reduce`](#ddrain-dreduce)
    - [`d/drains`](#ddrains)
    - [`d/fmap`, `d/combine-with`](#dfmap-dcombine-with)
    - [`d/group-by`](#dgroup-by)
    - [`d/with`](#dwith)
    - [`d/reductions`, `d/fold`](#dreductions-dfold)
- [Performance](#performance)
- [Related works](#related-works)

## Installation

Add the following to your `:dependencies`:

[![Clojars Project](https://clojars.org/drains/latest-version.svg)](https://clojars.org/drains)

If you would rather use an unstable version of the library via [Clojure CLI tool](https://clojure.org/guides/deps_and_cli), add the following to your `deps.edn` instead:

```clj
{...
 :deps {...
        athos/drains {:git/url "https://github.com/athos/Drains.git"
                      :sha "<commit sha>"}
        ...}
 ...}
```

## Usage

To use this library, first require the `drains.core` ns:

```clj
(require '[drains.core :as d])
```

### `d/drain`, `d/reduce`

`d/drain` creates a new drain object consisting of a reducing fn and an initial value:

```clj
(d/drain + 0)
```

Or shortly, you can just write `(d/drain +)` if the reducing fn can generate the initial value when called with no arguments.

Using the `d/reduce`, you can aggregate a sequence like `clojure.core/reduce`:

```clj
(d/reduce (d/drain conj []) (range 5))
;=> [0 1 2 3 4]

(d/reduce (d/drain +) (range 5))
;=> 10
```

`d/drain` optionally takes a [transducer](https://clojure.org/reference/transducers), in which case the transducer will be applied to the reducing fn:

```clj
(d/reduce (d/drain (map inc) conj [])
          (range 5))
;=> [1 2 3 4 5]

(d/reduce (d/drain (comp (filter even?) (take 3)) conj [])
          (range 10))
;=> [0 2 4]
```

In general, `(d/reduce (d/drain xf op val) xs)` is semantically equal to `(transduce xf op val xs)`.

### `d/drains`

An interesting nature of drains is that they can be composed surprisingly easily. `d/drains` is the simplest way to compose existing drains:

```clj
(d/reduce (d/drains [(d/drain conj)
                     (d/drain +)])
          (range 5))
;=> [[0 1 2 3 4] 10]

(d/reduce (d/drains [(d/drain min ##Inf)
                     (d/drain max ##-Inf)])
          [3 1 4 1 5 9 2])
;=> [1 9]

;; d/drains can also take a map, not only a vector
(d/reduce (d/drains {:min (d/drain min ##Inf)
                     :max (d/drain max ##-Inf)})
          [3 1 4 1 5 9 2])
;=> {:min 1, :max 9}

(d/reduce (d/drains {:evens (d/drain (filter even?) conj [])
                     :odds (d/drain (filter odd?) conj [])})
          (range 10))
;=> {:evens [0 2 4 6 8], :odds [1 3 5 7 9]}

(d/reduce (d/drains {:sum (d/drain +)
                     :range (d/drains {:min (d/drain min ##Inf)
                                       :max (d/drain max ##-Inf)})})
          [3 1 4 1 5 9 2])
;=> {:sum 25, :range {:min 1, :max 9}}
```

`d/drains` can compose an arbitrary number of drains and can also be nested arbitrarily. Even in such cases, the sequence aggregation will be done in a single pass.

### `d/fmap`, `d/combine-with`

`d/fmap` is another way to create a drain from another drain. It can transform the form of the aggregation result:

```clj
(d/reduce (d/fmap (fn [sum] {:sum sum})
                  (d/drain + 0))
          (range 10))
;=> {:sum 45}

(d/reduce (d/fmap (fn [[sum count]] {:average (/ sum (double count))})
                  (d/drains [(d/drain + 0)
                             (d/drain (map (constantly 1)) + 0)]))
          (range 10))
;=> {:average 4.5}
```

The combination of `d/drains` and `d/fmap` is useful and relatively common, so Drains provides the alias for that: `d/combine-with`. With `d/combine-with`, you can rewrite the above example like the following:

```clj
(d/reduce (d/combine-with (fn [sum count] {:average (/ sum (double count))})
                          (d/drain + 0)
                          (d/drain (map (constantly 1)) + 0))
          (range 10))
```

### `d/group-by`

Another convenient facility is `d/group-by`. `d/group-by` creates a fresh copy of the given drain every time it encounters a new key value (calculated with the specified key-fn), and manages each of the copies respectively through the aggregation:

```clj
(d/reduce (d/group-by even? (d/drain conj))
          (range 10))
;=> {true [0 2 4 6 8], false [1 3 5 7 9]}

(d/reduce (d/group-by #(rem % 3)
                      (d/drains {:items (d/drains conj)
                                 :sum (d/drain +)}))
          (range 10))
;=> {0 {:items [0 3 6 9], :sum 18},
;    1 {:items [1 4 7], :sum 12},
;    2 {:items [2 5 8], :sum 15}}
```

### `d/with`

You can also attach a transducer to existing drains using `d/with`:

```clj
(d/reduce (d/with (map :x)
                  (d/drains [(d/drain (filter even?) conj [])
                             (d/drain (filter odd?) conj [])]))
          [{:x 1} {:x 2} {:x 3} {:x 4} {:x 5}])
;=> [[2 4] [1 3 5]]

(d/reduce (d/with (take 5)
                  (d/drains {:min (d/drain min ##Inf)
                             :max (d/drain max ##-Inf)}))
          [3 1 4 1 5 9 2])
;=> {:min 1, :max 5}
```

In particular, `(d/with xf (d/drain op val))` is equivalent to `(d/drain xf op val)`.

`d/with` is so powerful to make existing drains reusable in various ways:

```clj
(def countries
  [{:name "Canada" :area 9984 :population 36 :continent "North America"}
   {:name "China" :area 9634 :population 1390 :continent "Asia"}
   {:name "Germany" :area 357 :population 82 :continent "Europe"}
   {:name "Japan" :area 377 :population 126 :continent "Asia"}
   {:name "UK" :area 244 :population 63 :continent "Europe"}
   {:name "USA" :area 9628 :population 327 :continent "North America"}}])

(def sum (d/drain +))

;; total population
(d/reduce (d/with (map :population) sum) countries)
;=> 2024

;; total area
(d/reduce (d/with (map :area) sum) countries)
;=> 30224

;; total are in Europe
(d/reduce (d/with (comp (filter #(= (:continent %) "Europe"))
                        (map :area))
                  sum)
          countries)
;=> 601
```

Note that which drain you attach a transducer to may cause a different result in some cases. For example:

```clj
(d/reduce (d/with (take 5)
                  (d/drains {:evens (d/drain (filter even?) conj [])
                             :odds (d/drain (filter odd?) conj [])}))
          (range 20))
;=> {:evens [0 2 4], :odds [1 3]}

(d/reduce (d/drains {:evens (d/drain (comp (filter even?) (take 5)) conj [])
                     :odds (d/drain (comp (filter odd?) (take 5)) conj [])})
          (range 20))
;=> {:evens [0 2 4 6 8], :odds [1 3 5 7 9]}
```

```clj
(d/reduce (d/with (take 5)
                  (d/group-by #(rem % 3)
                              (d/drain conj)))
          (range 20))
;=> {0 [0 3], 1 [1 4], 2 [2]}

(d/reduce (d/group-by #(rem % 3)
                      (d/with (take 5)
                              (d/drain conj)))
          (range 20))
;=> {0 [0 3 6 9 12],
;    1 [1 4 7 10 13],
;    2 [2 5 8 11 14]}
```

### `d/reductions`, `d/fold`

The library also provides some more aggregation fns such as `d/reductions` and `d/fold` besides `d/reduce`. They can be used almost the same as Clojure's counterparts except that they accept a drain instead of a reducing fn:

```clj
(d/reductions (d/drains {:sum (d/drain +)
                         :count (d/drain (map (constantly 1)) + 0)})
              (range 5))
;=> ({:sum 0, :count 0}
;    {:sum 0, :count 1}
;    {:sum 1, :count 2}
;    {:sum 3, :count 3}
;    {:sum 6, :count 4}
;    {:sum 10, :count 5})

(d/fold 2048 + (d/drain +) (vec (range 100000)))
;=> 4999950000
```

## Performance

The Drains runs very efficiently in spite of its expressiveness and flexibility in design.

Drains suppress memory allocation during the aggregation as much as possible and their implementations are well optimized for their typical use cases. So, they are usually almost equal to (or sometimes even better than) Clojure's counterparts in performance:

```clj
(require '[criterium.core :refer [quick-bench]])

;; Drains

(quick-bench (d/reduce (d/drains [(d/drain min ##Inf) (d/drain max ##-Inf)]) (range 1000000)))
;; Evaluation count : 24 in 6 samples of 4 calls.
;;              Execution time mean : 28.793182 ms
;;     Execution time std-deviation : 950.228568 µs
;;    Execution time lower quantile : 27.811876 ms ( 2.5%)
;;    Execution time upper quantile : 29.787913 ms (97.5%)
;;                    Overhead used : 8.015921 ns

;; corresponding code in clojure.core

(quick-bench (reduce (fn [[mi ma] x] [(min mi x) (max ma x)]) [##Inf ##-Inf] (range 1000000)))
;; Evaluation count : 18 in 6 samples of 3 calls.
;;              Execution time mean : 38.313381 ms
;;     Execution time std-deviation : 1.375499 ms
;;    Execution time lower quantile : 36.656866 ms ( 2.5%)
;;    Execution time upper quantile : 40.239558 ms (97.5%)
;;                    Overhead used : 8.015921 ns
```

```clj
;; Drains

(quick-bench (d/reduce (d/group-by #(rem % 10) (d/drain conj)) (range 1000000)))
;; Evaluation count : 6 in 6 samples of 1 calls.
;;              Execution time mean : 119.204513 ms
;;     Execution time std-deviation : 24.679129 ms
;;    Execution time lower quantile : 89.878243 ms ( 2.5%)
;;    Execution time upper quantile : 149.656373 ms (97.5%)
;;                    Overhead used : 8.015921 ns

;; corresponding code in clojure.core

(quick-bench (group-by #(rem % 10) (range 1000000)))
;; Evaluation count : 6 in 6 samples of 1 calls.
;;              Execution time mean : 222.926809 ms
;;     Execution time std-deviation : 31.079101 ms
;;    Execution time lower quantile : 187.375581 ms ( 2.5%)
;;    Execution time upper quantile : 257.340992 ms (97.5%)
;;                    Overhead used : 8.015921 ns
```

## Related works

- [babbage](https://github.com/ReadyForZero/babbage)
    - Very much resembles Drains except for its computation graph facilities
    - Provides a little bit complicated API due to lack of transducers integration (since it was released long before Clojure introduced transducers in 1.7!)
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
