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

To use this library, first require the `drains.core` ns as follows:

```
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

`d/drain` can optionally take a [transducer](https://clojure.org/reference/transducers), in which case the transducer will be applied to the reducing fn:

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

An interesting nature of drains is that they can be composed easily. `d/drains` is the simplest way to compose existing drains:

```clj
(d/reduce (d/drains [(d/drain conj)
                     (d/drain +)])
          (range 5))
;=> [[0 1 2 3 4] 10]

(d/reduce (d/drains [(d/drain min ##Inf)
                     (d/drain max ##-Inf)])
          [3 1 4 1 5 9 2])
;=> [1 9]

;; d/drains can also take a map instead of a vector
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

`d/drains` can compose an arbitrary number of drains and can also be nested arbitrarily. Even in such cases, the sequence aggregation will be done in a one-pass process.

### `d/fmap`, `d/combine-with`

`d/fmap` is another way to create a drain from another existing drain. It enables to transform the resulting aggregation value:

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

`d/combine-with` is the alias of the combination of `d/drains` and `d/fmap`. With `d/combine-with`, you can rewrite the above example code like the following:

```clj
(d/reduce (d/combine-with (fn [sum count] {:average (/ sum (double count))})
                          (d/drain + 0)
                          (d/drain (map (constantly 1)) + 0))
          (range 10))
```

### `d/with`

You can also attach a transducer to existing drains using `d/with`:

```clj
(d/reduce (d/with (take 5)
                  (d/drains {:min (d/drain min ##Inf)
                             :max (d/drain max ##-Inf)}))
          [3 1 4 1 5 9 2])
;=> {:min 1, :max 5}
```

In particular, `(d/with xf (d/drain op val))` is equivalent to `(d/drain xf op val)`.

### `d/group-by`

Another convenient facility is `d/group-by`. `d/group-by` creates a fresh copy of the given drain every time it encounters a new key value (calculated with the specified key-fn), and manages each respectively through the aggregation:

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

Note that attaching a transducer to a drain constructed with `d/group-by` may cause a different result from the one gained by attaching the transducer to the underlying drain. For example:

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
