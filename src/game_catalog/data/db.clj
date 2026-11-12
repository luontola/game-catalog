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

(defn delete! [collection-key entity-id]
  (swap! *collections update collection-key dissoc entity-id))

(defn init-collection! [collection-key entities]
  (let [entities-by-id (->> entities
                            (map (fn [entity]
                                   (let [entity-id (:entity/id entity)]
                                     (assert entity-id "entity was missing :entity/id")
                                     [entity-id entity])))
                            (into {}))]
    (swap! *collections assoc collection-key entities-by-id)))


;;;; Reverse indexes

;; An index is a pure function of the collection's immutable map, so it can be
;; cached for as long as the collection map is the identical object. Every
;; write creates a new collection map, so the index can't get out of sync.
(def ^:private *reverse-indexes (atom {}))

(defn- build-reverse-index [entities field]
  (reduce (fn [index entity]
            (let [value (get entity field)
                  target-ids (if (sequential? value) value [value])]
              (reduce (fn [index target-id]
                        (update index target-id (fnil conj []) (:entity/id entity)))
                      index
                      (distinct (remove nil? target-ids)))))
          {}
          entities))

(defn- reverse-index [collection-key field]
  (let [collection (get @*collections collection-key)
        cache-key [collection-key field]
        cached (get @*reverse-indexes cache-key)]
    (if (and cached (identical? collection (:collection cached)))
      (:index cached)
      (let [index (build-reverse-index (vals collection) field)]
        (swap! *reverse-indexes assoc cache-key {:collection collection
                                                 :index index})
        index))))

(defn referring-ids
  "Returns the IDs of the entities in the collection whose field refers to
   the target ID. The field may contain a single ID or a sequence of IDs."
  [collection-key field target-id]
  (get (reverse-index collection-key field) target-id []))
