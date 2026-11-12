(ns game-catalog.data.dlcs
  (:require [game-catalog.data.baserow :as baserow]
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

(def baserow-field-keys
  {"Name" :dlc/name
   "Base Game" :dlc/base-game
   "Year" :dlc/year
   "Purchases" nil}) ; back reference

(defn- single-reference [entity field]
  (let [ids (get entity field)]
    (when (< 1 (count ids))
      (throw (ex-info (str "Expected at most one reference in " field ": " (pr-str ids))
                      {:field field, :entity entity})))
    (assoc entity field (first ids))))

(defn read-dlcs [database]
  (->> (baserow/read-table database "DLCs" baserow-field-keys)
       (mapv #(single-reference % :dlc/base-game))))
