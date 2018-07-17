(defproject drains "0.1.0"
  :description "A new abstraction for flexible and efficient sequence aggregation in Clojure(Script)"
  :url "https://github.com/athos/Drains"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:provided
             {:dependencies [[org.clojure/clojure "1.9.0"]
                             [org.clojure/clojurescript "1.10.238"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}})
