(ns drains.test-runner
  (:require  [clojure.test :as t]
             drains.core-test
             drains.utils-test))

(defn -main []
  (let [{:keys [fail error]} (t/run-tests 'drains.core-test
                                          'drains.utils-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
