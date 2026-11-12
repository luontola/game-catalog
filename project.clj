(defproject game-catalog "1.0.0-SNAPSHOT"

  :description "Spreadsheet/MS Access/Airtable replacement"
  :url "https://github.com/luontola/game-catalog"

  :dependencies [[hiccup "2.0.0"]
                 [medley "1.4.0"]
                 [metosin/reitit "0.11.0"]
                 [metosin/ring-http-response "0.9.5"]
                 [mount "0.1.24"]
                 [org.apache.commons/commons-lang3 "3.22.0"]
                 [org.clojure/clojure "1.12.6"]
                 [org.clojure/tools.logging "1.3.1"]
                 [org.jsoup/jsoup "1.24.1"]
                 [ring "1.15.6"]
                 [ring-logger "1.1.1"]
                 [ring-ttl-session "0.3.1"]
                 [ring/ring-defaults "0.7.1"]
                 [spootnik/unilog "0.7.32"]]
  :managed-dependencies [[com.fasterxml.jackson.core/jackson-annotations "2.23"]
                         [org.clojure/java.classpath "1.1.1"]
                         [org.clojure/tools.reader "1.6.0"]
                         [org.slf4j/slf4j-api "2.0.20"]
                         [ring/ring-core "1.15.6"]]
  :pedantic? :warn
  :min-lein-version "2.12.0"

  :source-paths ["src"]
  :test-paths ["test"]
  :resource-paths ^:replace ["resources"]
  :target-path "target/%s/"
  :main ^:skip-aot game-catalog.main
  :global-vars {*warn-on-reflection* true
                *print-namespace-maps* false}

  :plugins [[lein-ancient "1.0.0"]
            [lein-pprint "1.3.2"]]

  :aliases {"autotest" ["kaocha" "--watch"]
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]}
  :test-selectors {:default (fn [m] (not (:e2e m)))
                   :e2e :e2e}

  :profiles {:uberjar {:auto-clean true
                       :omit-source true
                       :aot :all
                       :uberjar-name "game-catalog.jar"}

             :dev {:dependencies [[com.microsoft.playwright/playwright "1.64.0"]
                                  [etaoin "1.1.43"]
                                  [lambdaisland/kaocha "1.91.1392"]
                                  [ring/ring-devel "1.15.6"]
                                  [ring/ring-mock "0.6.2"]]
                   :jvm-opts ^:replace ["-XX:-OmitStackTraceInFastThrow"]
                   :repl-options {:init-ns game-catalog.repl}
                   :resource-paths ["test-resources"]}

             :kaocha [:dev]})
