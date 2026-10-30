(ns game-catalog.data.purchases
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [game-catalog.data.csv-fields :as csv-fields]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [game-catalog.ui.spreadsheet.multiselect :as multiselect]
            [game-catalog.ui.spreadsheet.reference :as reference]
            [game-catalog.ui.spreadsheet.row-number :as row-number]))

(def known-shops
  [;; primary distributors
   "Steam" ; blue #dae4fd
   "GOG" ; green #d0f6dc
   "Epic Games" ; light orange #fffbf0

   ;; secondary distributors
   "Microsoft Store" ; light red #fff2f0 for all
   "Oculus Store"
   "Origin"
   "Rockstar Games"

   ;; no distributor
   "DDL" ; dark blue #acc8f8

   ;; digital retailers, key resellers and other random places
   "Humble Store" ; light blue #f0f4fc for all
   "Kickstarter"
   "Fanatical"
   "Indiegala"
   "Cloud Imperium Games"
   "DLGamer"
   "Gamesplanet"
   "Obsidian Entertainment"])

(def config
  {:collection-key :purchases
   :id-generator spreadsheet/uuid-id-generator
   :sort-by :purchase/date
   :reference-label (fn [purchase]
                      (str/join ", " (:purchase/shop purchase)))
   :reference-tooltip (fn [purchase]
                        (->> [(:purchase/date purchase) (:purchase/cost purchase)]
                             (remove str/blank?)
                             (str/join ", ")
                             (not-empty)))
   :columns [(assoc row-number/column-defaults
               :column/name "#")
             (assoc multiselect/column-defaults
               :column/name "Shop"
               :column/entity-key :purchase/shop
               :column/options known-shops)
             {:column/name "Date"
              :column/entity-key :purchase/date}
             {:column/name "Cost"
              :column/entity-key :purchase/cost}
             (assoc reference/column-defaults
               :column/name "Base Games"
               :column/entity-key :purchase/base-games
               :column/target-collection :games)
             (assoc reference/column-defaults
               :column/name "DLCs"
               :column/entity-key :purchase/dlcs
               :column/target-collection :dlcs)
             {:column/name "Bundle Name"
              :column/entity-key :purchase/bundle-name}]})

(def csv-column-keys
  [:entity/id
   :purchase/shop
   :purchase/date
   :purchase/cost
   :purchase/base-games
   :purchase/dlcs
   :purchase/bundle-name])

(defn read-purchases-from-csv []
  (with-open [reader (io/reader "data/Purchases.csv")]
    (let [csv-data (doall (csv/read-csv reader))
          rows (rest csv-data)]
      (map #(-> (zipmap csv-column-keys %)
                (update :purchase/shop csv-fields/parse-string-vector)
                (assoc :entity/id (spreadsheet/uuid-id-generator nil)))
           rows))))
