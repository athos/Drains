(ns drains.bench
  (:require [drains.core :as d]
            [drains.utils :as dutils]
            [libra.bench :as bench :refer [defbench is]]
            [libra.criterium :as cr]))

(defbench drain-bench
  (is (cr/quick-bench (reduce + 0 (range (long 1e2)))))
  (is (cr/quick-bench (d/reduce (dutils/sum) (range (long 1e2)))))

  (is (cr/quick-bench (reduce + 0 (range (long 1e4)))))
  (is (cr/quick-bench (d/reduce (dutils/sum) (range (long 1e4)))))

  (is (cr/quick-bench (reduce + 0 (range (long 1e6)))))
  (is (cr/quick-bench (d/reduce (dutils/sum) (range (long 1e6)))))

  (is (cr/quick-bench (reduce + 0 (range (long 1e8)))))
  (is (cr/quick-bench (d/reduce (dutils/sum) (range (long 1e8))))))

(defbench drains-bench
  (is (cr/quick-bench (reduce (fn [[mi ma] x] [(min mi x) (max ma x)]) [##Inf ##-Inf] (range (long 1e2)))))
  (is (cr/quick-bench (d/reduce (d/drains [(dutils/min) (dutils/max)]) (range (long 1e2)))))

  (is (cr/quick-bench (reduce (fn [[mi ma] x] [(min mi x) (max ma x)]) [##Inf ##-Inf] (range (long 1e4)))))
  (is (cr/quick-bench (d/reduce (d/drains [(dutils/min) (dutils/max)]) (range (long 1e4)))))

  (is (cr/quick-bench (reduce (fn [[mi ma] x] [(min mi x) (max ma x)]) [##Inf ##-Inf] (range (long 1e6)))))
  (is (cr/quick-bench (d/reduce (d/drains [(dutils/min) (dutils/max)]) (range (long 1e6)))))

  (is (cr/quick-bench (reduce (fn [[mi ma] x] [(min mi x) (max ma x)]) [##Inf ##-Inf] (range (long 1e8)))))
  (is (cr/quick-bench (d/reduce (d/drains [(dutils/min) (dutils/max)]) (range (long 1e8))))))

(defbench group-by-bench
  (is (cr/quick-bench (group-by #(rem % 10) (range (long 1e2)))))
  (is (cr/quick-bench (d/reduce (d/group-by #(rem % 10) (d/drain conj)) (range (long 1e2)))))

  (is (cr/quick-bench (group-by #(rem % 10) (range (long 1e4)))))
  (is (cr/quick-bench (d/reduce (d/group-by #(rem % 10) (d/drain conj)) (range (long 1e4)))))

  (is (cr/quick-bench (group-by #(rem % 10) (range (long 1e6)))))
  (is (cr/quick-bench (d/reduce (d/group-by #(rem % 10) (d/drain conj)) (range (long 1e6))))))

(defn -main []
  (bench/run-benches 'drains.bench))
