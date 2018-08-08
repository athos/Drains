(ns drains.test-runner
  (:require [clojure.test :as t]
            drains.core-test
            drains.utils-test))

(defn exit-with [{:keys [fail error]}]
  (let [code (if (zero? (+ fail error)) 0 1)]
    #?(:clj (System/exit code)
       ;; AFAIK JavaScript doesn't have the implementation-independent `exit` function,
       ;; so we have to depend on Nashorn-specific one here
       :cljs (js/exit code))))

#?(:cljs
   (defmethod t/report [::t/default :end-run-tests] [summary]
     (exit-with summary)))

(defn -main []
  (let [summary (t/run-tests 'drains.core-test
                             'drains.utils-test)]
    #?(:clj (exit-with summary))))
