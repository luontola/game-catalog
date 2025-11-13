(ns game-catalog.data.games
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [mount.core :as mount]))

(def columns
  {:game/id {:column/name "#"}
   :game/name {:column/name "Name"}
   :game/release {:column/name "Release"}
   :game/remake {:column/name "Remake"}
   :game/series {:column/name "Series"}
   :game/tags {:column/name "Tags"}
   :game/purchases {:column/name "Purchases"}
   :game/status {:column/name "Status"}
   :game/content {:column/name "Content"}
   :game/dlcs {:column/name "DLCs"}})

(def csv-column-keys
  [:game/id
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
      (->> rows
           (map #(zipmap csv-column-keys %))
           (map (fn [game] [(:game/id game) game]))
           (into {})))))

(mount/defstate *games-db
  :start (atom (read-games-from-csv)))

(defn get-all-games []
  (vals @*games-db))

(defn get-game-by-id [game-id]
  (get @*games-db game-id))

(defn update-game! [game-id updated-game]
  (swap! *games-db assoc game-id updated-game))
