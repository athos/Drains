(ns drains.bench
  (:require [drains.core :as d]
            [drains.utils :as dutils]
            [libra.bench :as bench :refer [defbench is]]
            [libra.criterium :as cr]))

(defbench drain-bench
  (is (cr/bench (reduce + 0 (range 1000000))))
  (is (cr/bench (d/reduce (dutils/sum) (range 1000000)))))

(defbench drains-bench
  (is (cr/bench (reduce (fn [[mi ma] x] [(min mi x) (max ma x)]) [##Inf ##-Inf] (range 1000000))))
  (is (cr/bench (d/reduce (d/drains [(dutils/min) (dutils/max)]) (range 1000000)))))

(defbench group-by-bench
  (is (cr/bench (group-by #(rem % 10) (range 1000000))))
  (is (cr/bench (d/reduce (d/group-by #(rem % 10) (d/drain conj)) (range 1000000)))))

(defn -main []
  (bench/run-benches 'drains.bench))
