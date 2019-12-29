(defproject ktb "0.1.0-SNAPSHOT"
  :description "Kaktus Telegram bot to notify about extra actions"
  :url "https://www.github.com/klapuch/kaktus-telegram-bot"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clj-http "3.10.0"]
                 [org.jsoup/jsoup "1.12.1"]
                 [digest "1.4.9"]
                 [cheshire "5.9.0"]]
  :main ^:skip-aot ktb.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
