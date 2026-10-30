(ns game-catalog.ui.games-page
  (:require [game-catalog.data.collections :as collections]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as html]
            [game-catalog.ui.layout :as layout]
            [game-catalog.ui.spreadsheet :as spreadsheet]))

(defn games-page-handler [request]
  (-> (h/html
        [:h2 "Games"]
        (spreadsheet/table (:games collections/configs)))
      (layout/page)
      (html/response)))

(def routes
  [["/games"
    {:get {:handler games-page-handler}}]
   (spreadsheet/make-routes (:games collections/configs))])
