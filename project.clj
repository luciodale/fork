(defproject fork "2.4.3"
  :description "Headless form management for Re-frame and Reagent. Declarative validation, submission handling, dirty tracking, field arrays, and async server validation for ClojureScript."
  :url "https://github.com/luciodale/fork"
  :license {:name "MIT"}
  :source-paths ["src"]
  :profiles {:uberjar {:aot :all}}
  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]])
