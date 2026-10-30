(ns game-catalog.ui.purchases-page
  (:require [game-catalog.data.collections :as collections]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as html]
            [game-catalog.ui.layout :as layout]
            [game-catalog.ui.spreadsheet :as spreadsheet]))

(defn purchases-page-handler [request]
  (-> (h/html
        [:h2 "Purchases"]
        (spreadsheet/table (:purchases collections/configs)))
      (layout/page)
      (html/response)))

(def routes
  [["/purchases"
    {:get {:handler purchases-page-handler}}]
   (spreadsheet/make-routes (:purchases collections/configs))])
