(ns game-catalog.data.db
  (:require [mount.core :as mount]))

(mount/defstate *collections
  :start (atom {}))

(defn get-all [collection-key]
  (vals (get @*collections collection-key)))

(defn get-by-id [collection-key entity-id]
  (get-in @*collections [collection-key entity-id]))

(defn update! [collection-key entity-id updated-entity]
  (swap! *collections assoc-in [collection-key entity-id] updated-entity))

(defn init-collection! [collection-key data-map]
  (swap! *collections assoc collection-key data-map))
