(ns game-catalog.data.dlcs
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [game-catalog.data.db :as db]
            [mount.core :as mount]))

(def config
  {:collection-key :dlcs
   :sort-by (comp clojure.string/lower-case :dlc/name)
   :columns [{:column/name "#"
              :entity/key :entity/id
              :column/read-only? true}
             {:column/name "Name"
              :entity/key :dlc/name}
             {:column/name "Base Game"
              :entity/key :dlc/base-game}
             {:column/name "Year"
              :entity/key :dlc/year}
             {:column/name "Purchases"
              :entity/key :dlc/purchases}]})

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
      (map #(zipmap csv-column-keys %)
           rows))))

(mount/defstate dlcs-loader
  :start (db/init-collection! (:collection-key config) (read-dlcs-from-csv)))
