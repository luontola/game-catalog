(ns game-catalog.data.catalog
  (:require [clojure.tools.logging :as log]
            [game-catalog.data.baserow :as baserow]
            [game-catalog.data.db :as db]
            [game-catalog.data.dlcs :as dlcs]
            [game-catalog.data.games :as games]
            [game-catalog.data.purchases :as purchases]
            [mount.core :as mount]))

(defn- column-values [entity column]
  (let [value (get entity (:column/entity-key column))]
    (if (sequential? value)
      value
      [value])))

(defn validate-options!
  "Throws if the entities have values which are not in the options of the
   config's columns. The options must be updated before importing new values."
  [config entities]
  (doseq [column (:columns config)
          :when (seq (:column/options column))
          :let [options (set (:column/options column))]
          entity entities
          value (column-values entity column)
          :when (and (some? value)
                     (not (contains? options value)))]
    (throw (ex-info (str "Unknown option in " (:column/entity-key column) ": " (pr-str value))
                    {:column (:column/entity-key column), :value value, :entity entity}))))

(defn read-catalog [database]
  (let [catalog {:games (games/read-games database)
                 :dlcs (dlcs/read-dlcs database)
                 :purchases (purchases/read-purchases database)}]
    (validate-options! games/config (:games catalog))
    (validate-options! dlcs/config (:dlcs catalog))
    (validate-options! purchases/config (:purchases catalog))
    catalog))

(defn load-catalog! []
  (let [file (baserow/find-export-file "data")
        _ (log/info "Importing" (str file))
        {:keys [games dlcs purchases]} (read-catalog (baserow/read-export file))]
    (db/init-collection! (:collection-key games/config) games)
    (db/init-collection! (:collection-key dlcs/config) dlcs)
    (db/init-collection! (:collection-key purchases/config) purchases)))

(mount/defstate catalog-loader
  :start (load-catalog!))
