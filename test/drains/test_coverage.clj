(ns drains.test-coverage
  (:require [cloverage.coverage :as coverage]))

(defn -main []
  (coverage/run-project {:src-ns-path ["src"]
                         :test-ns-path ["test"]
                         :test-ns-regex [#"drains\..*-test"]
                         :codecov? true}))
