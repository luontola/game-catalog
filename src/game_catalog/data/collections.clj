(ns game-catalog.data.collections
  (:require [game-catalog.data.dlcs :as dlcs]
            [game-catalog.data.games :as games]
            [game-catalog.data.purchases :as purchases]))

(def configs
  "All collection configs by collection key. Each config has the full map
   as :target-configs, so that reference columns can look up how to show
   the entities of other collections, without circular dependencies."
  (let [configs (->> [games/config dlcs/config purchases/config]
                     (map (juxt :collection-key identity))
                     (into {}))]
    (update-vals configs #(assoc % :target-configs configs))))
