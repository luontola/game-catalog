(ns game-catalog.data.db
  (:require [mount.core :as mount]))

(mount/defstate *collections
  :start (atom {}))

(defn get-all [collection-key]
  (vals (get @*collections collection-key)))

(defn get-by-id [collection-key entity-id]
  (get-in @*collections [collection-key entity-id]))

(defn save! [collection-key entity]
  (let [entity-id (:entity/id entity)]
    (assert entity-id "entity was missing :entity/id")
    (swap! *collections assoc-in [collection-key entity-id] entity)))

(defn init-collection! [collection-key entities]
  (let [entities-by-id (->> entities
                            (map (fn [entity]
                                   (let [entity-id (:entity/id entity)]
                                     (assert entity-id "entity was missing :entity/id")
                                     [entity-id entity])))
                            (into {}))]
    (swap! *collections assoc collection-key entities-by-id)))
