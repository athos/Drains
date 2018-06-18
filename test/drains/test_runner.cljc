(ns drains.test-runner
  (:require  [clojure.test :as t]
             drains.core-test
             drains.utils-test))

(defn -main []
  (t/run-tests 'drains.core-test
               'drains.utils-test))
