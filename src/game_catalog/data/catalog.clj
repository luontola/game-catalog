(ns game-catalog.data.catalog
  (:require [clojure.string :as str]
            [game-catalog.data.csv-fields :as csv-fields]
            [game-catalog.data.db :as db]
            [game-catalog.data.dlcs :as dlcs]
            [game-catalog.data.games :as games]
            [game-catalog.data.purchases :as purchases]
            [mount.core :as mount]))

(defn- index-by-name [entities name-key]
  (-> (group-by #(str/trim (str (get % name-key))) entities)
      (update-vals #(mapv :entity/id %))))

(defn- resolve-name [index name {:keys [entity field]}]
  (let [ids (get index name)]
    (case (count ids)
      1 (first ids)
      0 (throw (ex-info (str "Unknown name in " field ": " (pr-str name))
                        {:field field, :name name, :entity entity}))
      (throw (ex-info (str "Ambiguous name in " field ": " (pr-str name))
                      {:field field, :name name, :entity entity})))))

(defn- resolve-names [entity field index]
  (->> (csv-fields/parse-string-vector (str (get entity field)))
       (mapv #(resolve-name index % {:entity entity, :field field}))))

(defn- resolve-name-vector [entity field index]
  (assoc entity field (resolve-names entity field index)))

(defn- resolve-single-name [entity field index]
  (let [ids (resolve-names entity field index)]
    (when (< 1 (count ids))
      (throw (ex-info (str "Expected at most one name in " field ": " (pr-str (get entity field)))
                      {:field field, :entity entity})))
    (assoc entity field (first ids))))

(defn resolve-references
  "Converts the name references in the CSV data to entity ID references.
   Removes the back reference columns, because they are computed on read."
  [{:keys [games dlcs purchases]}]
  (let [games-by-name (index-by-name games :game/name)
        dlcs-by-name (index-by-name dlcs :dlc/name)]
    {:games (mapv #(dissoc % :game/purchases :game/dlcs) games)
     :dlcs (mapv #(-> %
                      (dissoc :dlc/purchases)
                      (resolve-single-name :dlc/base-game games-by-name))
                 dlcs)
     :purchases (mapv #(-> %
                           (resolve-name-vector :purchase/base-games games-by-name)
                           (resolve-name-vector :purchase/dlcs dlcs-by-name))
                      purchases)}))

(defn read-catalog-from-csv []
  (resolve-references {:games (games/read-games-from-csv)
                       :dlcs (dlcs/read-dlcs-from-csv)
                       :purchases (purchases/read-purchases-from-csv)}))

(defn load-catalog! []
  (let [{:keys [games dlcs purchases]} (read-catalog-from-csv)]
    (db/init-collection! (:collection-key games/config) games)
    (db/init-collection! (:collection-key dlcs/config) dlcs)
    (db/init-collection! (:collection-key purchases/config) purchases)))

(mount/defstate catalog-loader
  :start (load-catalog!))
