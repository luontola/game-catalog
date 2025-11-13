(ns game-catalog.ui.games-page
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as html]
            [game-catalog.ui.layout :as layout]))

(defn read-games []
  (with-open [reader (io/reader "data/Games.csv")]
    (let [csv-data (doall (csv/read-csv reader))
          headers (first csv-data)
          rows (rest csv-data)]
      {:headers headers
       :rows (doall rows)})))

(defn games-table [{:keys [headers rows]}]
  [:table
   [:thead
    [:tr
     (for [header headers]
       [:th header])]]
   [:tbody
    (for [row rows]
      [:tr
       (for [cell row]
         [:td cell])])]])

(defn games-page-handler [request]
  (let [games-data (read-games)]
    (->
      (h/html
        [:h2 "Games"]
        (games-table games-data))
      (layout/page)
      (html/response))))

(def routes
  [["/games"
    {:get {:handler games-page-handler}}]])
