(ns game-catalog.repl
  (:require [clojure.pprint :refer [pp pprint]]
            [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :refer [refresh]]
            [game-catalog.main :as main]))
#_pp #_pprint ; prevent the IDE from removing unused requires

(defn start []
  (main/start-app))

(defn stop []
  (main/stop-app))

(defn reset []
  (stop)
  (refresh :after 'game-catalog.repl/start))
