# Drains

A tiny, but incredibly flexible library that utilizes reducing fns and transducers to build composable and reusable computations.

## Installation

To use the library via [Clojure CLI tool](https://clojure.org/guides/deps_and_cli), add the following to your `deps.edn`:

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

(d/into (d/drain (filter even?) + 0) (range 10))
;; => 20
;; == (+ 0 2 4 6 8)

(d/into (d/drains [(d/drain min Long/MAX_VALUE)
                   (d/drain max Long/MIN_VALUE)])
        (range 10))
;; => [0 9]

(d/into (d/drains {:min (d/drain min Long/MAX_VALUE)
                   :max (d/drain max Long/MIN_VALUE)})
        (range 10))
;; => {:min 0, :max 9}

(d/into (d/fmap (fn [sum] {:sum sum})
                (d/drain +))
        (range 10))
;; => {:sum 45}

(d/into (d/combine-with (fn [sum count] {:sum sum :count count})
                        (d/drain +)
                        (d/drain (map (constantly 1)) + 0))
        (range 10))
;; => {:sum 45 :count 10}

(d/into (d/with (map (partial array-map :val))
                (d/drain conj []))
        (range 3))
;; => [{:val 0} {:val 1} {:val 2}]

;; And, you can combine those with one another however you want

(let [items #(d/fmap (fn [items] {:items items}) (d/drain conj))
      merge-mean (fn [d]
                   (d/combine-with (fn [v sum count]
                                     (assoc v
                                            :sum sum
                                            :count count
                                            :mean (/ sum (double count))))
                                   d
                                   (d/drain +)
                                   (d/drain (map (constantly 1)) + 0)))]
  (d/into (d/drains {:evens (d/with (filter even?)
                                    (merge-mean (items)))
                     :odds (d/with (filter odd?)
                                   (merge-mean (items)))})
          (range 10)))
;; => {:evens {:items [0 2 4 6 8]
;;             :sum 20
;;             :count 5
;;             :mean 4.0}
;;     :odds {:items [1 3 5 7 9]
;;            :sum 25
;;            :count 5
;;            :mean 5.0}}
```

## License

Copyright © 2018 Shogo Ohta

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
