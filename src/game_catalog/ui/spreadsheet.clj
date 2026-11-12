(ns game-catalog.ui.spreadsheet
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [game-catalog.data.db :as db]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as html]
            [game-catalog.ui.spreadsheet.reference :as reference]
            [game-catalog.ui.spreadsheet.text :as text]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response])
  (:import (java.util UUID)))

(defn- entity-type [config]
  (name (:collection-key config)))

(defn- with-defaults [column]
  (merge text/column-defaults column))

(defn- column-type-class [column]
  (when-some [column-type (:column/type column)]
    (str "column-" (name column-type))))

(defn- with-value
  "Adds the cell's value to ctx. Columns may compute it with :column/value-fn,
   for example from other collections, instead of reading it from the entity."
  [{:keys [column entity] :as ctx}]
  (assoc ctx :value (if-some [value-fn (:column/value-fn column)]
                      (value-fn ctx)
                      (get entity (:column/entity-key column)))))

(defn- view-cell [ctx td-attrs]
  (let [ctx (with-value (update ctx :column with-defaults))
        column (:column ctx)
        viewer (:column/viewer column)]
    (h/html
      [:td (merge {:class (column-type-class column)}
                  td-attrs)
       (viewer ctx)])))

(defn view-row
  ([config entity]
   (view-row config entity nil))
  ([config entity focus-index]
   (view-row config entity focus-index nil))
  ([config entity focus-index row-number]
   (h/html
     [:tr.viewing {:id (:entity/id entity) ; for linking to the row
                   :data-entity-type (entity-type config)
                   :data-entity-id (:entity/id entity)
                   ;; Rows after this one can continue the numbering with CSS
                   :style (when row-number
                            (str "counter-set: row-number " row-number))}
      (map-indexed
        (fn [idx column]
          (let [focus? (= idx focus-index)]
            (view-cell {:mode :view
                        :config config
                        :entity entity
                        :column column
                        :row-number row-number}
                       {:tabindex 0
                        :autofocus focus?
                        :auto-scroll-into-view focus?})))
        (:columns config))])))

(defn- pick-columns [config]
  ;; Row numbers would be meaningless in search results
  (remove #(= :row-number (:column/type %)) (:columns config)))

(defn pick-table
  "Renders the entities for picking a whole row, without editing support.
   The pick-template function returns content for each row's <template>."
  [config entities pick-template]
  (h/html
    [:table.spreadsheet-picker
     [:thead
      [:tr
       (for [column (pick-columns config)]
         [:th (:column/name column)])]]
     [:tbody
      (for [entity entities]
        [:tr {:data-entity-id (:entity/id entity)}
         [:template (pick-template entity)]
         (for [column (pick-columns config)]
           (view-cell {:mode :pick
                       :config config
                       :entity entity
                       :column column}
                      {}))])]]))

(defn edit-row
  ([config entity]
   (edit-row config entity nil))
  ([config entity focus-index]
   (let [entity-type (entity-type config)
         adding? (nil? entity)
         entity-id (if adding? "new" (:entity/id entity))
         form-id (str entity-type "-form-" entity-id)]
     (h/html
       [:tr.editing {:id (when-not adding?
                           (:entity/id entity))
                     :data-entity-type entity-type
                     :data-entity-id entity-id
                     :class (when adding?
                              "adding")}
        (map-indexed
          (fn [idx column]
            (let [column (with-defaults column)
                  read-only? (:column/read-only? column)
                  focus? (= idx focus-index)
                  ctx (with-value {:mode :edit
                                   :config config
                                   :entity entity
                                   :column column
                                   :form-id form-id
                                   :focus? focus?})
                  editor (if read-only?
                           (:column/viewer column)
                           (:column/editor column))]
              (h/html
                [:td (cond-> {:class (column-type-class column)}
                       read-only? (assoc :tabindex 0
                                         :autofocus focus?))
                 (editor ctx)])))
          (:columns config))
        ;; HTML doesn't allow <form> between <table> and <td> elements,
        ;; so it must be inside one of the <td>s and referred using IDs.
        ;; It must be the last <td>, so that the cell indexes match the column
        ;; indexes, which the focusIndex parameter is based on.
        [:td.form-element-container
         [:form {:id form-id}]]]))))

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
        (for [[idx entity] (map-indexed vector entities)]
          (view-row config entity nil (inc idx)))
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

(defn- form-updates [config params]
  (->> (:columns config)
       (map with-defaults)
       (remove :column/read-only?)
       (mapcat (fn [column]
                 ((:column/parse-form-params column) params column)))
       (into {})))

(defn save-row-handler [config]
  (fn [request]
    (let [collection-key (:collection-key config)
          entity-id (get-in request [:path-params :entity-id])
          focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
          updates (form-updates config (:params request))
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
          entity (db/get-by-id collection-key entity-id)
          referring-entities (when entity
                               (reference/referring-entities config entity-id))]
      (cond
        (nil? entity)
        (-> (html/response "Row not found")
            (response/status 404))

        (seq referring-entities)
        (-> (response/response (str "Cannot delete this row, because other rows refer to it:\n"
                                    (->> referring-entities
                                         (map (fn [{:keys [collection-key label]}]
                                                (str "- " (name collection-key) ": " label)))
                                         (str/join "\n"))))
            (response/status 409)
            (response/content-type "text/plain; charset=utf-8"))

        :else
        (do
          (db/delete! collection-key entity-id)
          (log/info (str "Deleted " (name collection-key) " " entity-id))
          (http-response/ok ""))))))

(defn- find-reference-column [config field-name]
  (->> (:columns config)
       (map with-defaults)
       (filter #(and (= :reference (:column/type %))
                     (not (:column/read-only? %))
                     (= field-name (text/form-field-name %))))
       first))

(defn reference-search-handler [config]
  (fn [request]
    (let [params (:params request)
          column (or (find-reference-column config (:column params))
                     (http-response/not-found! "Column not found"))
          target-config (-> (get-in config [:target-configs (:column/target-collection column)])
                            ;; For the reference columns in the search results
                            (assoc :target-configs (:target-configs config)))
          ctx {:config config
               :column column
               :form-id (:form params)}
          {:keys [entities more]} (reference/search ctx (:q params) (get params (text/form-field-name column)))]
      (html/response
        (h/html
          (if (empty? entities)
            [:p.reference-no-matches "No matches"]
            (pick-table target-config entities #(reference/pill ctx (reference/entity-item target-config %))))
          (when (pos? more)
            [:p.reference-more more " more, type to refine"]))))))

(defn make-routes [config]
  (let [entity-type (entity-type config)]
    [[(str "/spreadsheet/" entity-type "/reference-search")
      {:post {:handler (reference-search-handler config)}}]
     [(str "/spreadsheet/" entity-type "/:entity-id/view")
      {:post {:handler (view-row-handler config)}}]
     [(str "/spreadsheet/" entity-type "/:entity-id/edit")
      {:post {:handler (edit-row-handler config)}}]
     [(str "/spreadsheet/" entity-type "/:entity-id/save")
      {:post {:handler (save-row-handler config)}}]
     [(str "/spreadsheet/" entity-type "/:entity-id/delete")
      {:post {:handler (delete-row-handler config)}}]]))
