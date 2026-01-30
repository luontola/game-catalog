(ns game-catalog.data.dlcs
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [game-catalog.data.db :as db]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [mount.core :as mount]))

(def config
  {:collection-key :dlcs
   :id-generator spreadsheet/uuid-id-generator
   :sort-by (comp clojure.string/lower-case :dlc/name)
   :columns [{:column/name "#"
              :column/type :row-number
              :column/read-only? true}
             {:column/name "Name"
              :column/entity-key :dlc/name}
             {:column/name "Base Game"
              :column/entity-key :dlc/base-game}
             {:column/name "Year"
              :column/entity-key :dlc/year}
             {:column/name "Purchases"
              :column/entity-key :dlc/purchases}]})

(def csv-column-keys
  [:entity/id
   :dlc/name
   :dlc/base-game
   :dlc/year
   :dlc/purchases])

(defn read-dlcs-from-csv []
  (with-open [reader (io/reader "data/DLCs.csv")]
    (let [csv-data (doall (csv/read-csv reader))
          rows (rest csv-data)]
      (map #(-> (zipmap csv-column-keys %)
                (assoc :entity/id (spreadsheet/uuid-id-generator nil)))
           rows))))

(mount/defstate dlcs-loader
  :start (db/init-collection! (:collection-key config) (read-dlcs-from-csv)))
