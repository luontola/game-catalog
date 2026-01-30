(ns game-catalog.data.games
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [game-catalog.data.db :as db]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [mount.core :as mount]))

(def config
  {:collection-key :games
   :id-generator spreadsheet/uuid-id-generator
   :sort-by (comp clojure.string/lower-case :game/name)
   :columns [{:column/name "#"
              :column/type :row-number
              :column/read-only? true}
             {:column/name "Name"
              :column/entity-key :game/name}
             {:column/name "Release"
              :column/entity-key :game/release}
             {:column/name "Remake"
              :column/entity-key :game/remake}
             {:column/name "Series"
              :column/entity-key :game/series}
             {:column/name "Tags"
              :column/entity-key :game/tags}
             {:column/name "Purchases"
              :column/entity-key :game/purchases}
             {:column/name "Status"
              :column/entity-key :game/status}
             {:column/name "Content"
              :column/entity-key :game/content}
             {:column/name "DLCs"
              :column/entity-key :game/dlcs}]})

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
      (map #(-> (zipmap csv-column-keys %)
                (assoc :entity/id (spreadsheet/uuid-id-generator nil)))
           rows))))

(mount/defstate games-loader
  :start (db/init-collection! (:collection-key config) (read-games-from-csv)))
