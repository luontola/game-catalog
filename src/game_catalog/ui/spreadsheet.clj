(ns game-catalog.ui.spreadsheet
  (:require [game-catalog.data.db :as db]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as html]
            [ring.util.response :as response]))

(defn view-row
  ([collection-key entity csv-column-keys]
   (view-row collection-key entity csv-column-keys nil))
  ([collection-key entity csv-column-keys focus-index]
   (h/html
     [:tr.viewing {:data-entity-type (name collection-key)
                   :data-entity-id (:entity/id entity)}
      (map-indexed
        (fn [idx col-key]
          [:td {:tabindex 0
                :autofocus (= idx focus-index)}
           (get entity col-key)])
        csv-column-keys)])))

(defn edit-row
  ([collection-key entity csv-column-keys]
   (edit-row collection-key entity csv-column-keys nil))
  ([collection-key entity csv-column-keys focus-index]
   (let [entity-type (name collection-key)
         form-id (str entity-type "-form-" (:entity/id entity))]
     (h/html
       [:tr.editing {:data-entity-type entity-type
                     :data-entity-id (:entity/id entity)}
        (map-indexed
          (fn [idx col-key]
            (h/html [:td
                     (when (zero? idx)
                       [:form {:id form-id}])
                     [:input {:type "text"
                              :form form-id
                              :name (subs (str col-key) 1)
                              :value (get entity col-key)
                              :autofocus (= idx focus-index)
                              :data-1p-ignore true}]]))
          csv-column-keys)]))))

(defn table [collection-key entities columns csv-column-keys]
  (h/html
    [:table.spreadsheet
     [:thead
      [:tr
       (for [col-key csv-column-keys]
         [:th (get-in columns [col-key :column/name])])]]
     [:tbody
      (for [entity entities]
        (view-row collection-key entity csv-column-keys))]]))

(defn view-row-handler [collection-key csv-column-keys]
  (fn [request]
    (let [entity-id (get-in request [:path-params :entity-id])
          focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
          entity (db/get-by-id collection-key entity-id)]
      (if entity
        (html/response (view-row collection-key entity csv-column-keys focus-index))
        (-> (html/response (str (name collection-key) " not found"))
            (response/status 404))))))

(defn edit-row-handler [collection-key csv-column-keys]
  (fn [request]
    (let [entity-id (get-in request [:path-params :entity-id])
          focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
          entity (db/get-by-id collection-key entity-id)]
      (if entity
        (html/response (edit-row collection-key entity csv-column-keys focus-index))
        (-> (html/response (str (name collection-key) " not found"))
            (response/status 404))))))

(defn save-row-handler [collection-key csv-column-keys]
  (fn [request]
    (let [entity-id (get-in request [:path-params :entity-id])
          focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
          new-entity (-> (:params request)
                         (update-keys keyword)
                         (select-keys csv-column-keys))]
      (db/save! collection-key new-entity)
      (println (str "Saved " (name collection-key) " " entity-id ":")
               (pr-str new-entity))
      (html/response (view-row collection-key new-entity csv-column-keys focus-index)))))

(defn make-routes [collection-key csv-column-keys]
  [[(str "/spreadsheet/" (name collection-key) "/:entity-id/view")
    {:post {:handler (view-row-handler collection-key csv-column-keys)}}]
   [(str "/spreadsheet/" (name collection-key) "/:entity-id/edit")
    {:post {:handler (edit-row-handler collection-key csv-column-keys)}}]
   [(str "/spreadsheet/" (name collection-key) "/:entity-id/save")
    {:post {:handler (save-row-handler collection-key csv-column-keys)}}]])
