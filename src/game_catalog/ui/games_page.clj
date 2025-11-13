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

(defn view-game-row
  ([game] (view-game-row game nil))
  ([game focus-index]
   (h/html
     [:tr.viewing {:data-game-id (:game/id game)}
      (map-indexed
        (fn [idx col-key]
          [:td {:tabindex 0
                :autofocus (= idx focus-index)}
           (get game col-key)])
        csv-column-keys)])))

(defn edit-game-row
  ([game] (edit-game-row game nil))
  ([game focus-index]
   (let [form-id (str "game-form-" (:game/id game))]
     (h/html
       [:tr.editing {:data-game-id (:game/id game)}
        (map-indexed
          (fn [idx col-key]
            (h/html [:td
                     (when (zero? idx)
                       [:form {:id form-id}])
                     [:input {:type "text"
                              :form form-id
                              :name (name col-key)
                              :value (get game col-key)
                              :autofocus (= idx focus-index)
                              :data-1p-ignore true}]])) ; for 1Password, https://developer.1password.com/docs/web/compatible-website-design/
          csv-column-keys)]))))

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
        focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
        games (read-games)
        game (->> games
                  (filter #(= (:game/id %) game-id))
                  (first))]
    (if game
      (html/response (edit-game-row game focus-index))
      (-> (html/response "Game not found")
          (response/status 404)))))

(defn save-game-row-handler [request]
  (let [game-id (get-in request [:path-params :game-id])
        focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
        form-data (select-keys (:params request)
                               (map #(keyword (name %)) csv-column-keys))
        games (read-games)
        game (->> games
                  (filter #(= (:game/id %) game-id))
                  (first))]
    (println (str "Form data submitted for game " game-id ":")
             (pr-str form-data))
    (if game
      (html/response (view-game-row game focus-index))
      (-> (html/response "Game not found")
          (response/status 404)))))

(def routes
  [["/games"
    {:get {:handler games-page-handler}}]
   ["/games/:game-id/edit"
    {:post {:handler edit-game-row-handler}}]
   ["/games/:game-id/save"
    {:post {:handler save-game-row-handler}}]])
