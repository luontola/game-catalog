(ns game-catalog.data.purchases
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [game-catalog.data.db :as db]
            [mount.core :as mount]))

(def columns
  {:entity/id {:column/name "#"
               :column/display-order 1}
   :purchase/shop {:column/name "Shop"
                   :column/display-order 2}
   :purchase/date {:column/name "Date"
                   :column/display-order 3}
   :purchase/cost {:column/name "Cost"
                   :column/display-order 4}
   :purchase/base-games {:column/name "Base Games"
                         :column/display-order 5}
   :purchase/dlcs {:column/name "DLCs"
                   :column/display-order 6}
   :purchase/bundle-name {:column/name "Bundle Name"
                          :column/display-order 7}})

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
