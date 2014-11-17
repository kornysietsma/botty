(defproject com.sietsma.korny/botty "0.1.0-SNAPSHOT"
  :description "Basic IRC bot library for simple bots"
  :url "http://github.com/korny/botty"
  :license {:name "Do What The Fuck You Want To Public License (WTFPL)"
            :url "http://www.wtfpl.net/"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [irclj "0.5.0-alpha4"]
                 [org.clojure/tools.reader "0.8.5"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]
  :main botty.core
  :profiles {:uberjar {:main botty.core
                       :aot :all}})
