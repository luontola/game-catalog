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
   The first row is highlighted, because the Enter key picks the highlighted row."
  [config entities]
  (h/html
    [:table.spreadsheet-picker
     [:thead
      [:tr
       (for [column (pick-columns config)]
         [:th (:column/name column)])]]
     [:tbody
      (for [[idx entity] (map-indexed vector entities)]
        [:tr {:class (when (zero? idx) "highlighted")
              :data-entity-id (:entity/id entity)}
         (for [column (pick-columns config)]
           (view-cell {:mode :pick
                       :config config
                       :entity entity
                       :column column}
                      {}))])]]))

(defn- form-id [config entity]
  (str (entity-type config) "-form-" (:entity/id entity)))

(defn edit-row
  ([config entity]
   (edit-row config entity nil))
  ([config entity focus-index]
   (let [entity-type (entity-type config)
         entity-id (:entity/id entity)
         form-id (form-id config entity)]
     (h/html
       [:tr.editing {:id entity-id ; for linking to the row
                     :data-entity-type entity-type
                     :data-entity-id entity-id}
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
         [:form {:id form-id
                 ;; Every request from the form saves the whole row, so the requests
                 ;; are sent one at a time. Otherwise an old save, which arrives late,
                 ;; could overwrite a newer one. Of the waiting requests, only the last
                 ;; one is sent, because it has the newest values of all the fields.
                 :hx-sync "queue last"}]]]))))

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
          (view-row config entity nil (inc idx)))]]
      [:button.add-row-button {:type "button"
                               :aria-label "Add row"
                               :title "Add row"
                               :hx-post (str "/spreadsheet/" (entity-type config) "/create")
                               :hx-target ".spreadsheet > tbody"
                               :hx-swap "beforeend"}
       "+"]
      ;; Shows the error message of a failed request
      [:div.status-bar {:role "alert"
                        :hidden true}
       [:span.status-bar-message]
       [:button.status-bar-close {:type "button"
                                  :aria-label "Close"}
        "×"]]
      [:menu#context-menu {:class "context-menu"}
       [:li
        [:button#context-menu-delete {:onclick "this.clickHandler()"}
         "Delete row"]]])))

(defn- editable-columns [config]
  (->> (:columns config)
       (map with-defaults)
       (remove :column/read-only?)))

(defn- form-updates [config params]
  (->> (editable-columns config)
       (mapcat (fn [column]
                 ((:column/parse-form-params column) params column)))
       (into {})))

(defn- save-updates!
  "Saves the updated fields of the entity. Returns the up-to-date entity,
   or nil if the entity doesn't exist."
  [config entity-id updates]
  (let [collection-key (:collection-key config)]
    (when-some [old-entity (db/get-by-id collection-key entity-id)]
      (let [new-entity (merge old-entity updates)]
        ;; Rows are saved often and usually only one field has changed, or none.
        ;; Every write would invalidate the database's reverse indexes, so avoid
        ;; writes if the data has not changed.
        (when (not= old-entity new-entity)
          (db/save! collection-key new-entity)
          (log/info (str "Saved " (name collection-key) " " entity-id ":")
                    (pr-str new-entity)))
        new-entity))))

(defn- save-form!
  "Saves the form fields which the request has. Returns the up-to-date entity,
   or nil if the entity doesn't exist."
  [config request]
  (save-updates! config
                 (get-in request [:path-params :entity-id])
                 (form-updates config (:params request))))

(defn- error-response
  "Error messages are plain text, which the frontend shows in the status bar."
  [status message]
  (-> (response/response message)
      (response/status status)
      (response/content-type "text/plain; charset=utf-8")))

(defn- row-not-found []
  (error-response 404 "Row not found. It was probably deleted."))

(defn save-and-continue-handler
  "Saves the row, without changing what the user sees."
  [config]
  (fn [request]
    (if (save-form! config request)
      (http-response/no-content)
      (row-not-found))))

(defn save-row-handler
  "Saves the row, if the request has its form fields, and exits edit mode."
  [config]
  (fn [request]
    (let [focus-index (some-> (get-in request [:params :focusIndex]) parse-long)]
      (if-some [entity (save-form! config request)]
        (html/response (view-row config entity focus-index))
        (row-not-found)))))

(defn edit-row-handler [config]
  (fn [request]
    (let [collection-key (:collection-key config)
          entity-id (get-in request [:path-params :entity-id])
          focus-index (some-> (get-in request [:params :focusIndex]) parse-long)
          entity (db/get-by-id collection-key entity-id)]
      (if entity
        (html/response (edit-row config entity focus-index))
        (row-not-found)))))

(defn sequential-id-generator [collection-key]
  (let [entities (db/get-all collection-key)
        max-id (->> entities
                    (map :entity/id)
                    (map parse-long)
                    (apply max 0))]
    (str (inc max-id))))

(defn uuid-id-generator [_collection-key]
  (str (UUID/randomUUID)))

(defn- blank-entity
  "The same field values as saving a form where the user entered nothing."
  [config]
  (let [blank-params (->> (editable-columns config)
                          (map (fn [column]
                                 [(text/form-field-name column) ""]))
                          (into {}))]
    (form-updates config blank-params)))

(defn- first-editable-index [config]
  (->> (:columns config)
       (map with-defaults)
       (keep-indexed (fn [idx column]
                       (when-not (:column/read-only? column)
                         idx)))
       first))

(defn create-row-handler [config]
  (fn [request]
    (let [collection-key (:collection-key config)
          focus-index (or (some-> (get-in request [:params :focusIndex]) parse-long)
                          (first-editable-index config))
          entity (assoc (blank-entity config)
                   :entity/id ((:id-generator config) collection-key))]
      (db/save! collection-key entity)
      (log/info (str "Created " (name collection-key) " " (:entity/id entity)))
      (html/response (edit-row config entity focus-index)))))

(defn delete-row-handler [config]
  (fn [request]
    (let [collection-key (:collection-key config)
          entity-id (get-in request [:path-params :entity-id])
          entity (db/get-by-id collection-key entity-id)
          referring-entities (when entity
                               (reference/referring-entities config entity-id))]
      (cond
        (nil? entity)
        (row-not-found)

        (seq referring-entities)
        (error-response 409 (str "Cannot delete this row, because other rows refer to it:\n"
                                 (->> referring-entities
                                      (map (fn [{:keys [collection-key label]}]
                                             (str "- " (name collection-key) ": " label)))
                                      (str/join "\n"))))

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

(defn- reference-search-results
  "Renders the target entities which match the query, for the reference picker."
  [{:keys [config column] :as ctx} query exclude-ids]
  (let [target-config (-> (get-in config [:target-configs (:column/target-collection column)])
                          ;; For the reference columns in the search results
                          (assoc :target-configs (:target-configs config)))
        {:keys [entities more]} (reference/search ctx query exclude-ids)]
    (h/html
      (if (empty? entities)
        [:p.reference-no-matches "No matches"]
        (pick-table target-config entities))
      (when (pos? more)
        [:p.reference-more more " more, type to refine"])
      (reference/create-button ctx query (empty? entities)))))

(defn reference-search-handler [config]
  (fn [request]
    (let [params (:params request)]
      (if-some [column (find-reference-column config (:column params))]
        (let [ctx {:config config
                   :column column}]
          (html/response (reference-search-results ctx (:q params) (get params (text/form-field-name column)))))
        (error-response 404 "Column not found")))))

(defn- reference-editor-ctx [config entity column]
  (with-value {:mode :edit
               :config config
               :entity entity
               :column column
               :form-id (form-id config entity)}))

(defn reference-picker-handler
  "Saves the row, and returns the content of the reference column's editor
   with the picker open. The picker has the results of an empty search."
  [config]
  (fn [request]
    (let [column (find-reference-column config (get-in request [:params :column]))
          entity (when column
                   (save-form! config request))]
      (cond
        (nil? column)
        (error-response 404 "Column not found")

        (nil? entity)
        (row-not-found)

        :else
        (let [ctx (reference-editor-ctx config entity column)]
          (html/response (reference/editor-content
                           (assoc ctx :picker-results (reference-search-results ctx "" (:value ctx))))))))))

(defn- create-reference-target! [{:keys [config column] :as ctx} label]
  (let [target-collection (:column/target-collection column)
        target-config (get-in config [:target-configs target-collection])
        entity (-> (reference/new-entity ctx label)
                   (assoc :entity/id ((:id-generator target-config) target-collection)))]
    (db/save! target-collection entity)
    (log/info (str "Saved " (name target-collection) " " (:entity/id entity) ":")
              (pr-str entity))
    (:entity/id entity)))

(defn save-reference-handler
  "Saves the row, and returns the content of the reference column's editor
   without the picker, which closes the picker.

   The request can add a reference to the column: the :target parameter
   is the ID of an existing target entity, and the :create parameter is
   the label of a new target entity to create."
  [config]
  (fn [request]
    (let [params (:params request)
          entity-id (get-in request [:path-params :entity-id])
          old-entity (db/get-by-id (:collection-key config) entity-id)
          column (find-reference-column config (:column params))
          target-collection (:column/target-collection column)
          ctx {:config config
               :column column}
          create? (contains? params :create)
          target-id (:target params)]
      ;; All the checks are before the writes, so that a failed request changes nothing
      (cond
        (nil? column)
        (error-response 404 "Column not found")

        (nil? old-entity)
        (row-not-found)

        (and create? (not (reference/creatable? ctx)))
        (error-response 400 "Cannot create entities in the target collection")

        (and create? (str/blank? (:create params)))
        (error-response 400 "Cannot create an entity without a label")

        (and target-id (nil? (db/get-by-id target-collection target-id)))
        (error-response 404 "The picked row was not found. It was probably deleted.")

        :else
        (let [target-id (if create?
                          (create-reference-target! ctx (:create params))
                          target-id)
              entity-key (:column/entity-key column)
              updates (form-updates config params)
              updates (cond-> updates
                        ;; The form has the references which the user sees. The saved
                        ;; references are the fallback for requests without the form.
                        target-id (assoc entity-key (reference/add-reference
                                                      column
                                                      (get updates entity-key (get old-entity entity-key))
                                                      target-id)))
              entity (save-updates! config entity-id updates)]
          (html/response (reference/editor-content
                           (assoc (reference-editor-ctx config entity column)
                             ;; The focus was in the picker, which the response closes
                             :focus? (some? target-id)))))))))

(defn make-routes [config]
  (let [entity-type (entity-type config)]
    [[(str "/spreadsheet/" entity-type "/reference-search")
      {:post {:handler (reference-search-handler config)}}]
     [(str "/spreadsheet/" entity-type "/create")
      {:post {:handler (create-row-handler config)}}]
     [(str "/spreadsheet/" entity-type "/:entity-id/edit")
      {:post {:handler (edit-row-handler config)}}]
     [(str "/spreadsheet/" entity-type "/:entity-id/save")
      {:post {:handler (save-row-handler config)}}]
     [(str "/spreadsheet/" entity-type "/:entity-id/save-and-continue")
      {:post {:handler (save-and-continue-handler config)}}]
     [(str "/spreadsheet/" entity-type "/:entity-id/reference-picker")
      {:post {:handler (reference-picker-handler config)}}]
     [(str "/spreadsheet/" entity-type "/:entity-id/save-reference")
      {:post {:handler (save-reference-handler config)}}]
     [(str "/spreadsheet/" entity-type "/:entity-id/delete")
      {:post {:handler (delete-row-handler config)}}]]))
