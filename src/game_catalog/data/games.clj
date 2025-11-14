(ns game-catalog.data.games
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [game-catalog.data.db :as db]
            [mount.core :as mount]))

(def columns
  {:entity/id {:column/name "#"
               :column/display-order 1}
   :game/name {:column/name "Name"
               :column/display-order 2}
   :game/release {:column/name "Release"
                  :column/display-order 3}
   :game/remake {:column/name "Remake"
                 :column/display-order 4}
   :game/series {:column/name "Series"
                 :column/display-order 5}
   :game/tags {:column/name "Tags"
               :column/display-order 6}
   :game/purchases {:column/name "Purchases"
                    :column/display-order 7}
   :game/status {:column/name "Status"
                 :column/display-order 8}
   :game/content {:column/name "Content"
                  :column/display-order 9}
   :game/dlcs {:column/name "DLCs"
               :column/display-order 10}})

(def csv-column-keys
  [:entity/id
   :game/name
   :game/release
   :game/remake
   :game/series
   :game/tags
   :game/purchases
   :game/status
   :game/content
   :game/dlcs])

(defn read-games-from-csv []
  (with-open [reader (io/reader "data/Games.csv")]
    (let [csv-data (doall (csv/read-csv reader))
          rows (rest csv-data)]
      (map #(zipmap csv-column-keys %)
           rows))))

(mount/defstate games-loader
  :start (db/init-collection! :games (read-games-from-csv)))
