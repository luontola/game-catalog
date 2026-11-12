(ns game-catalog.data.games
  (:require [game-catalog.data.baserow :as baserow]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [game-catalog.ui.spreadsheet.multiselect :as multiselect]
            [game-catalog.ui.spreadsheet.numeric :as numeric]
            [game-catalog.ui.spreadsheet.reference :as reference]
            [game-catalog.ui.spreadsheet.row-number :as row-number]
            [game-catalog.ui.spreadsheet.select :as select]))

(def known-tags
  ["Local Co-Op"
   "Local Multiplayer"
   "Online Co-Op"
   "LAN Co-Op"
   "PvP"
   "VR"])

(def config
  {:collection-key :games
   :id-generator spreadsheet/uuid-id-generator
   :sort-by (comp clojure.string/lower-case :game/name)
   :reference-label :game/name
   :columns [(assoc row-number/column-defaults
               :column/name "#")
             {:column/name "Name"
              :column/entity-key :game/name}
             (assoc numeric/column-defaults
               :column/name "Release"
               :column/entity-key :game/release)
             (assoc numeric/column-defaults
               :column/name "Remake"
               :column/entity-key :game/remake)
             {:column/name "Series"
              :column/entity-key :game/series}
             (assoc multiselect/column-defaults
               :column/name "Tags"
               :column/entity-key :game/tags
               :column/options known-tags)
             (assoc reference/column-defaults
               :column/name "Purchases"
               :column/entity-key :game/purchases
               :column/target-collection :purchases
               :column/back-reference :purchase/base-games
               :column/read-only? true)
             (assoc select/column-defaults
               :column/name "Status"
               :column/entity-key :game/status
               :column/options ["" "Backlog" "Started" "Completed" "Dropped"])
             {:column/name "Content"
              :column/entity-key :game/content}
             (assoc reference/column-defaults
               :column/name "DLCs"
               :column/entity-key :game/dlcs
               :column/target-collection :dlcs
               :column/back-reference :dlc/base-game
               :column/read-only? true)]})

(def baserow-field-keys
  {"Name" :game/name
   "Release" :game/release
   "Remake" :game/remake
   "Series" :game/series
   "Tags" :game/tags
   "Purchases" nil ; back reference
   "Status" :game/status
   "Content" :game/content
   "DLCs" nil}) ; back reference

(defn read-games [database]
  (baserow/read-table database "Games" baserow-field-keys))
