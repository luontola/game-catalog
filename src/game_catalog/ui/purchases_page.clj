(ns game-catalog.ui.purchases-page
  (:require [game-catalog.data.db :as db]
            [game-catalog.data.purchases :as purchases]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as html]
            [game-catalog.ui.layout :as layout]
            [game-catalog.ui.spreadsheet :as spreadsheet]))

(defn purchases-page-handler [request]
  (let [all-purchases (->> (db/get-all :purchases)
                           (sort-by (comp clojure.string/lower-case :purchase/date)))]
    (-> (h/html
          [:h2 "Purchases"]
          (spreadsheet/table :purchases all-purchases purchases/columns))
        (layout/page)
        (html/response))))

(def routes
  [["/purchases"
    {:get {:handler purchases-page-handler}}]
   (spreadsheet/make-routes :purchases purchases/columns)])
