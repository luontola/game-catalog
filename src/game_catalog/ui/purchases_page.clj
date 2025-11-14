(ns game-catalog.ui.purchases-page
  (:require [game-catalog.data.db :as db]
            [game-catalog.data.purchases :as purchases]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as html]
            [game-catalog.ui.layout :as layout]
            [ring.util.response :as response]))

(defn view-purchase-row
  ([purchase] (view-purchase-row purchase nil))
  ([purchase focus-index]
   (h/html
     [:tr.viewing {:data-entity-type "purchases"
                   :data-entity-id (:entity/id purchase)}
      (map-indexed
        (fn [idx col-key]
          [:td {:tabindex 0, :autofocus (= idx focus-index)}
           (get purchase col-key)])
        purchases/csv-column-keys)])))

(defn edit-purchase-row
  ([purchase] (edit-purchase-row purchase nil))
  ([purchase focus-index]
   (let [form-id (str "purchase-form-" (:entity/id purchase))]
     (h/html
       [:tr.editing {:data-entity-type "purchases"
                     :data-entity-id (:entity/id purchase)}
        (map-indexed
          (fn [idx col-key]
            (h/html [:td
                     (when (zero? idx)
                       [:form {:id form-id}])
                     [:input {:type "text"
                              :form form-id
                              :name (subs (str col-key) 1) ; namespaced keyword without the ":" prefix
                              :value (get purchase col-key)
                              :autofocus (= idx focus-index)
                              :data-1p-ignore true}]])) ; for 1Password, https://developer.1password.com/docs/web/compatible-website-design/
          purchases/csv-column-keys)]))))

(defn purchases-table [all-purchases]
  (h/html
    [:table.spreadsheet
     [:thead
      [:tr
       (for [col-key purchases/csv-column-keys]
         [:th (get-in purchases/columns [col-key :column/name])])]]
     [:tbody
      (for [purchase all-purchases]
        (view-purchase-row purchase))]]))

(defn purchases-page-handler [request]
  (let [all-purchases (->> (db/get-all :purchases)
                           (sort-by (comp clojure.string/lower-case :purchase/date)))]
    (->
      (h/html
        [:h2 "Purchases"]
        (purchases-table all-purchases))
      (layout/page)
      (html/response))))

(defn view-purchase-row-handler [request]
  (let [purchase-id (get-in request [:path-params :purchase-id])
        focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
        purchase (db/get-by-id :purchases purchase-id)]
    (if purchase
      (html/response (view-purchase-row purchase focus-index))
      (-> (html/response "Purchase not found")
          (response/status 404)))))

(defn edit-purchase-row-handler [request]
  (let [purchase-id (get-in request [:path-params :purchase-id])
        focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
        purchase (db/get-by-id :purchases purchase-id)]
    (if purchase
      (html/response (edit-purchase-row purchase focus-index))
      (-> (html/response "Purchase not found")
          (response/status 404)))))

(defn save-purchase-row-handler [request]
  (let [purchase-id (get-in request [:path-params :purchase-id])
        focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
        new-purchase (-> (:params request)
                         (update-keys keyword)
                         (select-keys purchases/csv-column-keys))]
    (db/save! :purchases new-purchase)
    (println (str "Saved purchase " purchase-id ":")
             (pr-str new-purchase))
    (html/response (view-purchase-row new-purchase focus-index))))

(def routes
  [["/purchases"
    {:get {:handler purchases-page-handler}}]
   ["/purchases/:purchase-id/view"
    {:post {:handler view-purchase-row-handler}}]
   ["/purchases/:purchase-id/edit"
    {:post {:handler edit-purchase-row-handler}}]
   ["/purchases/:purchase-id/save"
    {:post {:handler save-purchase-row-handler}}]])
