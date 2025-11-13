(ns game-catalog.ui.games-page
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as html]
            [game-catalog.ui.layout :as layout]
            [ring.util.response :as response]))

(def columns
  {:game/id {:column/name "#"}
   :game/name {:column/name "Name"}
   :game/release {:column/name "Release"}
   :game/remake {:column/name "Remake"}
   :game/series {:column/name "Series"}
   :game/tags {:column/name "Tags"}
   :game/purchases {:column/name "Purchases"}
   :game/status {:column/name "Status"}
   :game/content {:column/name "Content"}
   :game/dlcs {:column/name "DLCs"}})

(def csv-column-keys
  [:game/id
   :game/name
   :game/release
   :game/remake
   :game/series
   :game/tags
   :game/purchases
   :game/status
   :game/content
   :game/dlcs])

(defn read-games []
  (with-open [reader (io/reader "data/Games.csv")]
    (let [csv-data (doall (csv/read-csv reader))
          rows (rest csv-data)]
      (mapv #(zipmap csv-column-keys %) rows))))

(defn view-game-row [game]
  (h/html
    [:tr {:hx-post (str "/games/" (:game/id game) "/edit")
          :hx-trigger "edit"
          :hx-swap "outerHTML"}
     (for [col-key csv-column-keys]
       [:td {:tabindex 0}
        (get game col-key)])]))

(defn edit-game-row [game]
  (h/html
    [:tr {:hx-post (str "/games/" (:game/id game) "/view")
          :hx-trigger "view"
          :hx-swap "outerHTML"}
     (for [col-key csv-column-keys]
       [:td
        [:input {:type "text"
                 :name (name col-key)
                 :value (get game col-key)}]])]))

(defn games-table [games]
  (h/html
    [:table.spreadsheet
     [:thead
      [:tr
       (for [col-key csv-column-keys]
         [:th (get-in columns [col-key :column/name])])]]
     [:tbody
      (for [game games]
        (view-game-row game))]]))

(defn games-page-handler [request]
  (let [games (read-games)]
    (->
      (h/html
        [:h2 "Games"]
        (games-table games))
      (layout/page)
      (html/response))))

(defn edit-game-row-handler [request]
  (let [game-id (get-in request [:path-params :game-id])
        games (read-games)
        game (->> games
                  (filter #(= (:game/id %) game-id))
                  (first))]
    (if game
      (html/response (edit-game-row game))
      (-> (html/response "Game not found")
          (response/status 404)))))

(defn view-game-row-handler [request]
  (let [game-id (get-in request [:path-params :game-id])
        games (read-games)
        game (->> games
                  (filter #(= (:game/id %) game-id))
                  (first))]
    (if game
      (html/response (view-game-row game))
      (-> (html/response "Game not found")
          (response/status 404)))))

(def routes
  [["/games"
    {:get {:handler games-page-handler}}]
   ["/games/:game-id/edit"
    {:post {:handler edit-game-row-handler}}]
   ["/games/:game-id/view"
    {:post {:handler view-game-row-handler}}]])
