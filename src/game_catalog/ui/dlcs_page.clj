(ns game-catalog.ui.dlcs-page
  (:require [game-catalog.data.collections :as collections]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as html]
            [game-catalog.ui.layout :as layout]
            [game-catalog.ui.spreadsheet :as spreadsheet]))

(defn dlcs-page-handler [request]
  (-> (h/html
        [:h2 "DLCs"]
        (spreadsheet/table (:dlcs collections/configs)))
      (layout/page)
      (html/response)))

(def routes
  [["/dlcs"
    {:get {:handler dlcs-page-handler}}]
   (spreadsheet/make-routes (:dlcs collections/configs))])
