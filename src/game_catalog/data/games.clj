(ns game-catalog.data.games
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [game-catalog.data.db :as db]
            [mount.core :as mount]))

(def config
  {:collection-key :games
   :columns [{:column/name "#"
              :entity/key :entity/id}
             {:column/name "Name"
              :entity/key :game/name}
             {:column/name "Release"
              :entity/key :game/release}
             {:column/name "Remake"
              :entity/key :game/remake}
             {:column/name "Series"
              :entity/key :game/series}
             {:column/name "Tags"
              :entity/key :game/tags}
             {:column/name "Purchases"
              :entity/key :game/purchases}
             {:column/name "Status"
              :entity/key :game/status}
             {:column/name "Content"
              :entity/key :game/content}
             {:column/name "DLCs"
              :entity/key :game/dlcs}]})

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
  :start (db/init-collection! (:collection-key config) (read-games-from-csv)))
