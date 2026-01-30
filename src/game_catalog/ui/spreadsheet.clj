(ns game-catalog.ui.spreadsheet
  (:require [clojure.tools.logging :as log]
            [game-catalog.data.db :as db]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as html]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response])
  (:import (java.util UUID)))

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
          (let [focus? (= idx focus-index)
                row-number? (= :row-number (:column/type column))]
            [:td {:class (when row-number? "row-number")
                  :tabindex 0
                  :autofocus focus?
                  :auto-scroll-into-view focus?}
             (when-not row-number?
               (get entity (:column/entity-key column)))]))
        (:columns config))])))

(defn edit-row
  ([config entity]
   (edit-row config entity nil))
  ([config entity focus-index]
   (let [entity-type (entity-type config)
         adding? (nil? entity)
         entity-id (if adding? "new" (:entity/id entity))
         form-id (str entity-type "-form-" entity-id)]
     (h/html
       [:tr.editing {:data-entity-type entity-type
                     :data-entity-id entity-id
                     :class (when adding?
                              "adding")}
        (map-indexed
          (fn [idx column]
            (let [col-key (:column/entity-key column)
                  read-only? (:column/read-only? column)
                  row-number? (= :row-number (:column/type column))
                  value (get entity col-key)]
              (h/html [:td (merge
                             (when row-number? {:class "row-number"})
                             (when read-only? {:tabindex 0
                                               :autofocus (= idx focus-index)}))
                       (when (zero? idx)
                         [:form {:id form-id}])
                       (when-not row-number?
                         (if read-only?
                           value
                           [:input {:type "text"
                                    :form form-id
                                    :name (subs (str col-key) 1) ; namespaced keyword without the ":" prefix
                                    :value value
                                    :data-test-content (str "[" value "]")
                                    :autofocus (= idx focus-index)
                                    :autocomplete "off"
                                    :data-1p-ignore true}]))]))) ; for 1Password, https://developer.1password.com/docs/web/compatible-website-design/
          (:columns config))]))))

(defn add-row
  ([config]
   (add-row config nil))
  ([config focus-index]
   (edit-row config nil focus-index)))

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
          (view-row config entity))
        (add-row config nil)]]
      [:menu#context-menu {:class "context-menu"}
       [:li
        [:button#context-menu-delete {:onclick "this.clickHandler()"}
         "Delete row"]]])))

(defn view-row-handler [config]
  (fn [request]
    (let [collection-key (:collection-key config)
          entity-id (get-in request [:path-params :entity-id])
          focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
          entity (db/get-by-id collection-key entity-id)]
      (if (= entity-id "new")
        (html/response (add-row config focus-index))
        (if entity
          (html/response (view-row config entity focus-index))
          (-> (html/response "Row not found")
              (response/status 404)))))))

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

(defn sequential-id-generator [collection-key]
  (let [entities (db/get-all collection-key)
        max-id (->> entities
                    (map :entity/id)
                    (map parse-long)
                    (apply max 0))]
    (str (inc max-id))))

(defn uuid-id-generator [_collection-key]
  (str (UUID/randomUUID)))

(defn save-row-handler [config]
  (fn [request]
    (let [collection-key (:collection-key config)
          entity-id (get-in request [:path-params :entity-id])
          focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
          writeable-entity-keys (->> (:columns config)
                                     (remove :column/read-only?)
                                     (map :column/entity-key))
          updates (-> (:params request)
                      (update-keys keyword)
                      (select-keys writeable-entity-keys))
          adding? (= "new" entity-id)
          old-entity (db/get-by-id collection-key entity-id)
          new-entity (cond
                       adding? (assoc updates :entity/id ((:id-generator config) collection-key))
                       old-entity (merge old-entity updates)
                       :else (http-response/not-found! "Row not found"))]
      (db/save! collection-key new-entity)
      (log/info (str "Saved " (name collection-key) " " (:entity/id new-entity) ":")
                (pr-str new-entity))
      (html/response (h/html
                       (view-row config new-entity focus-index)
                       (when adding?
                         (add-row config)))))))

(defn delete-row-handler [config]
  (fn [request]
    (let [collection-key (:collection-key config)
          entity-id (get-in request [:path-params :entity-id])
          entity (db/get-by-id collection-key entity-id)]
      (if entity
        (do
          (db/delete! collection-key entity-id)
          (log/info (str "Deleted " (name collection-key) " " entity-id))
          (http-response/ok ""))
        (-> (html/response "Row not found")
            (response/status 404))))))

(defn make-routes [config]
  (let [entity-type (entity-type config)]
    [[(str "/spreadsheet/" entity-type "/:entity-id/view")
      {:post {:handler (view-row-handler config)}}]
     [(str "/spreadsheet/" entity-type "/:entity-id/edit")
      {:post {:handler (edit-row-handler config)}}]
     [(str "/spreadsheet/" entity-type "/:entity-id/save")
      {:post {:handler (save-row-handler config)}}]
     [(str "/spreadsheet/" entity-type "/:entity-id/delete")
      {:post {:handler (delete-row-handler config)}}]]))
