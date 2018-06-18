(ns drains.utils-test
  (:require [clojure.test :refer [deftest is are]]
            [drains.core :as d]
            [drains.utils :as dutils]))

(deftest sum-test
  (is (= 45 (d/reduce (dutils/sum) (range 10))))
  (is (= 145 (d/reduce (dutils/sum 100) (range 10)))))

(deftest sum-by-test
  (is (= 6 (d/reduce (dutils/sum-by :x) [{:x 1} {:x 2} {:x 3}]))))

(deftest count-test
  (is (= 10 (d/reduce (dutils/count) (range 10))))
  (is (= 110 (d/reduce (dutils/count 100) (range 10)))))

(deftest average-test
  (is (= 4.5 (d/reduce (dutils/average) (range 10)))))

(deftest min-test
  (is (= 1 (d/reduce (dutils/min) [3 1 4 1 5 9 2 6])))
  (is (= -1 (d/reduce (dutils/min -1) [3 1 4 1 5 9 2 6]))))

(deftest min-by-test
  (is (= 1 (d/reduce (dutils/min-by :x) [{:x 3} {:x 1} {:x 4}]))))

(deftest max-test
  (is (= 9 (d/reduce (dutils/max) [3 1 4 1 5 9 2 6])))
  (is (= 100 (d/reduce (dutils/max 100) [3 1 4 1 5 9 2 6]))))

(deftest max-by-test
  (is (= 4 (d/reduce (dutils/max-by :x) [{:x 3} {:x 1} {:x 4}]))))
