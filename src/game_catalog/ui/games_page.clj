(ns game-catalog.ui.games-page
  (:require [game-catalog.data.games :as games]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as html]
            [game-catalog.ui.layout :as layout]
            [ring.util.response :as response]))

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
        games/csv-column-keys)])))

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
                              :name (subs (str col-key) 1) ; namespaced keyword without the ":" prefix
                              :value (get game col-key)
                              :autofocus (= idx focus-index)
                              :data-1p-ignore true}]])) ; for 1Password, https://developer.1password.com/docs/web/compatible-website-design/
          games/csv-column-keys)]))))

(defn games-table [all-games]
  (h/html
    [:table.spreadsheet
     [:thead
      [:tr
       (for [col-key games/csv-column-keys]
         [:th (get-in games/columns [col-key :column/name])])]]
     [:tbody
      (for [game all-games]
        (view-game-row game))]]))

(defn games-page-handler [request]
  (let [all-games (->> (games/get-all-games)
                       (sort-by (comp clojure.string/lower-case :game/name)))]
    (->
      (h/html
        [:h2 "Games"]
        (games-table all-games))
      (layout/page)
      (html/response))))

(defn edit-game-row-handler [request]
  (let [game-id (get-in request [:path-params :game-id])
        focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
        game (games/get-game-by-id game-id)]
    (if game
      (html/response (edit-game-row game focus-index))
      (-> (html/response "Game not found")
          (response/status 404)))))

(defn save-game-row-handler [request]
  (let [game-id (get-in request [:path-params :game-id])
        focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
        new-game (-> (:params request)
                     (update-keys keyword)
                     (select-keys games/csv-column-keys))]
    (games/update-game! game-id new-game)
    (println (str "Saved game " game-id ":")
             (pr-str new-game))
    (html/response (view-game-row new-game focus-index))))

(def routes
  [["/games"
    {:get {:handler games-page-handler}}]
   ["/games/:game-id/edit"
    {:post {:handler edit-game-row-handler}}]
   ["/games/:game-id/save"
    {:post {:handler save-game-row-handler}}]])
