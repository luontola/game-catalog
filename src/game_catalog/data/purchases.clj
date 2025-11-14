(ns game-catalog.data.purchases
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [game-catalog.data.db :as db]
            [mount.core :as mount]))

(def columns
  [{:entity/key :entity/id
    :column/name "#"}
   {:entity/key :purchase/shop
    :column/name "Shop"}
   {:entity/key :purchase/date
    :column/name "Date"}
   {:entity/key :purchase/cost
    :column/name "Cost"}
   {:entity/key :purchase/base-games
    :column/name "Base Games"}
   {:entity/key :purchase/dlcs
    :column/name "DLCs"}
   {:entity/key :purchase/bundle-name
    :column/name "Bundle Name"}])

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
      (map #(zipmap csv-column-keys %)
           rows))))

(mount/defstate purchases-loader
  :start (db/init-collection! :purchases (read-purchases-from-csv)))
