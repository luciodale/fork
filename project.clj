(defproject fork "2.1.5"
  :description "Reagent & Re-Frame form library"
  :url "https://github.com/luciodale/fork"
  :license {:name "MIT"}
  :source-paths ["src"]
  :profiles {:uberjar {:aot :all}}
  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]])
