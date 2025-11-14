(ns game-catalog.ui.games-page
  (:require [game-catalog.data.db :as db]
            [game-catalog.data.games :as games]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as html]
            [game-catalog.ui.layout :as layout]
            [game-catalog.ui.spreadsheet :as spreadsheet]))

(defn games-page-handler [request]
  (let [all-games (->> (db/get-all :games)
                       (sort-by (comp clojure.string/lower-case :game/name)))]
    (-> (h/html
          [:h2 "Games"]
          (spreadsheet/table :games all-games games/columns games/csv-column-keys))
        (layout/page)
        (html/response))))

(def routes
  [["/games"
    {:get {:handler games-page-handler}}]
   (spreadsheet/make-routes :games games/csv-column-keys)])
