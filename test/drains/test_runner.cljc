(ns drains.test-runner
  (:require  [clojure.test :as t]
             drains.core-test))

(defn -main []
  (t/run-tests 'drains.core-test))
