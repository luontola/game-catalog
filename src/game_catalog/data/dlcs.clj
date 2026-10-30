(ns game-catalog.data.dlcs
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [game-catalog.ui.spreadsheet.reference :as reference]
            [game-catalog.ui.spreadsheet.row-number :as row-number]))

(def config
  {:collection-key :dlcs
   :id-generator spreadsheet/uuid-id-generator
   :sort-by (comp clojure.string/lower-case :dlc/name)
   :reference-label :dlc/name
   :columns [(assoc row-number/column-defaults
               :column/name "#")
             {:column/name "Name"
              :column/entity-key :dlc/name}
             (assoc reference/column-defaults
               :column/name "Base Game"
               :column/entity-key :dlc/base-game
               :column/target-collection :games
               :column/multiple? false)
             {:column/name "Year"
              :column/entity-key :dlc/year}
             (assoc reference/column-defaults
               :column/name "Purchases"
               :column/entity-key :dlc/purchases
               :column/target-collection :purchases
               :column/back-reference :purchase/dlcs
               :column/read-only? true)]})

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
