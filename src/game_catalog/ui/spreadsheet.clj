(ns game-catalog.ui.spreadsheet
  (:require [game-catalog.data.db :as db]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as html]
            [ring.util.response :as response]))

(defn- entity-type [config]
  (name (:collection-key config)))

(defn view-row
  ([config entity]
   (view-row config entity nil))
  ([config entity focus-index]
   (h/html
     [:tr.viewing {:data-entity-type (entity-type config)
                   :data-entity-id (:entity/id entity)}
      (map-indexed
        (fn [idx column]
          [:td {:tabindex 0
                :autofocus (= idx focus-index)}
           (get entity (:entity/key column))])
        (:columns config))])))

(defn edit-row
  ([config entity]
   (edit-row config entity nil))
  ([config entity focus-index]
   (let [entity-type (entity-type config)
         form-id (str entity-type "-form-" (:entity/id entity))]
     (h/html
       [:tr.editing {:data-entity-type entity-type
                     :data-entity-id (:entity/id entity)}
        (map-indexed
          (fn [idx column]
            (let [col-key (:entity/key column)
                  read-only? (:column/read-only? column)]
              (h/html [:td (when read-only?
                             {:tabindex 0
                              :autofocus (= idx focus-index)})
                       (when (zero? idx)
                         [:form {:id form-id}])
                       (if read-only?
                         (get entity col-key)
                         [:input {:type "text"
                                  :form form-id
                                  :name (subs (str col-key) 1)
                                  :value (get entity col-key)
                                  :autofocus (= idx focus-index)
                                  :data-1p-ignore true}])])))
          (:columns config))]))))

(defn table [config]
  (let [entities (->> (db/get-all (:collection-key config))
                      (sort-by (:sort-by config)))]
    (h/html
      [:table.spreadsheet
       [:thead
        [:tr
         (for [column (:columns config)]
           [:th (:column/name column)])]]
       [:tbody
        (for [entity entities]
          (view-row config entity))]])))

(defn view-row-handler [config]
  (fn [request]
    (let [collection-key (:collection-key config)
          entity-id (get-in request [:path-params :entity-id])
          focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
          entity (db/get-by-id collection-key entity-id)]
      (if entity
        (html/response (view-row config entity focus-index))
        (-> (html/response "Row not found")
            (response/status 404))))))

(defn edit-row-handler [config]
  (fn [request]
    (let [collection-key (:collection-key config)
          entity-id (get-in request [:path-params :entity-id])
          focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
          entity (db/get-by-id collection-key entity-id)]
      (if entity
        (html/response (edit-row config entity focus-index))
        (-> (html/response "Row not found")
            (response/status 404))))))

(defn save-row-handler [config]
  (fn [request]
    (let [collection-key (:collection-key config)
          entity-id (get-in request [:path-params :entity-id])
          focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
          old-entity (db/get-by-id collection-key entity-id)
          _ (assert old-entity) ; TODO: support adding new entities
          entity-keys-whitelist (map :entity/key (:columns config))
          updates (-> (:params request)
                      (update-keys keyword)
                      (select-keys entity-keys-whitelist))
          new-entity (merge old-entity updates)]
      (db/save! collection-key new-entity)
      (println (str "Saved " (name collection-key) " " entity-id ":")
               (pr-str new-entity))
      (html/response (view-row config new-entity focus-index)))))

(defn make-routes [config]
  (let [entity-type (entity-type config)]
    [[(str "/spreadsheet/" entity-type "/:entity-id/view")
      {:post {:handler (view-row-handler config)}}]
     [(str "/spreadsheet/" entity-type "/:entity-id/edit")
      {:post {:handler (edit-row-handler config)}}]
     [(str "/spreadsheet/" entity-type "/:entity-id/save")
      {:post {:handler (save-row-handler config)}}]]))
