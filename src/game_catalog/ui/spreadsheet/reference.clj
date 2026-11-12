(ns game-catalog.ui.spreadsheet.reference
  (:require [clojure.string :as str]
            [game-catalog.data.db :as db]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.json :as json]
            [game-catalog.ui.spreadsheet.text :as text]))

;; The value is entity IDs referring to the :column/target-collection.
;; With :column/multiple? it's a vector of IDs, otherwise a single ID.
;; The target collection's config determines how the entities are shown,
;; using its :reference-label and :reference-tooltip functions.
;;
;; With :column/back-reference, the value is computed on read: it's the
;; target entities whose :column/back-reference field refers to this entity.
;; Back reference columns should also be :column/read-only?

(def form-field-name text/form-field-name)

(defn- value->ids [value]
  (cond
    (nil? value) []
    (sequential? value) (->> value
                             (map str)
                             (remove str/blank?)
                             vec)
    (str/blank? value) []
    :else [(str value)]))

(defn- back-reference-ids [{:keys [column entity]}]
  (let [target-collection (:column/target-collection column)
        back-reference (:column/back-reference column)]
    (when-some [id (:entity/id entity)]
      (db/referring-ids target-collection back-reference id))))

(defn value [{:keys [column entity] :as ctx}]
  (if (:column/back-reference column)
    (back-reference-ids ctx)
    (get entity (:column/entity-key column))))

(defn- target-config [{:keys [column config]}]
  (get-in config [:target-configs (:column/target-collection column)]))

(defn- entity-label [target-config entity]
  (let [label-fn (or (:reference-label target-config) :entity/id)
        label (str (label-fn entity))]
    (if (str/blank? label)
      "(no label)"
      label)))

(defn- entity-tooltip [target-config entity]
  (when-some [tooltip-fn (:reference-tooltip target-config)]
    (tooltip-fn entity)))

(defn- sort-entities [target-config entities]
  (if-some [sort-key (:sort-by target-config)]
    (sort-by sort-key entities)
    entities))

(defn- resolve-references
  "Returns the referred entities in the target collection's order,
   followed by the IDs of missing entities."
  [{:keys [column value] :as ctx}]
  (let [target-collection (:column/target-collection column)
        ids (value->ids value)
        entities (keep #(db/get-by-id target-collection %) ids)
        found-ids (set (map :entity/id entities))]
    {:entities (sort-entities (target-config ctx) entities)
     :missing-ids (remove found-ids ids)}))

(defn entity-item [target-config entity]
  {:id (:entity/id entity)
   :label (entity-label target-config entity)
   :tooltip (entity-tooltip target-config entity)})

(defn- items [ctx]
  (let [target-config (target-config ctx)
        {:keys [entities missing-ids]} (resolve-references ctx)]
    (concat (for [entity entities]
              (entity-item target-config entity))
            (for [id missing-ids]
              {:id id
               :missing? true}))))

(defn viewer [{:keys [column mode] :as ctx}]
  (let [target-collection (:column/target-collection column)
        items (items ctx)
        ;; Links in edit mode, or in the picker of edit mode, would make it
        ;; too easy to lose unsaved changes
        links? (= :view mode)]
    (h/html
      (for [[idx {:keys [id label tooltip missing?]}] (map-indexed vector items)]
        (list
          (when (pos? idx)
            ", ")
          (if missing?
            [:span.missing {:title "Missing reference"} id]
            (if links?
              [:a {:href (str "/" (name target-collection) "#" id)
                   :title tooltip}
               label]
              label)))))))

(defn pill [{:keys [column form-id]} {:keys [id label tooltip missing?]}]
  (let [label (if missing? id label)]
    (h/html
      [:span.pill {:class (when missing? "missing")
                   :title (if missing? "Missing reference" tooltip)}
       [:input {:type "hidden"
                :form form-id
                :name (form-field-name column)
                :value id}]
       label
       [:button.pill-remove {:type "button"
                             :aria-label (str "Remove " label)}
        "×"]])))

(def search-limit 50)

(defn search
  "Returns the target entities whose label contains the query, excluding
   the already referred entities. Returns at most search-limit entities,
   and the number of the other matches."
  [ctx query exclude-ids]
  (let [target-config (target-config ctx)
        target-collection (get-in ctx [:column :column/target-collection])
        exclude-ids (set (value->ids exclude-ids))
        query (str/lower-case (str/trim (str query)))
        matches (->> (db/get-all target-collection)
                     (remove #(contains? exclude-ids (:entity/id %)))
                     (filter #(str/includes? (str/lower-case (entity-label target-config %)) query))
                     (sort-entities target-config))]
    {:entities (take search-limit matches)
     :more (max 0 (- (count matches) search-limit))}))

(defn creatable?
  "New target entities can be created from the search query, when the label
   is a field which the query can populate."
  [ctx]
  (keyword? (:reference-label (target-config ctx))))

(defn new-entity [ctx query]
  {(:reference-label (target-config ctx)) (str/trim (str query))})

(defn create-button [{:keys [config column form-id] :as ctx} query]
  (let [query (str/trim (str query))]
    (when (and (creatable? ctx)
               (not (str/blank? query)))
      (h/html
        [:button.reference-create {:type "button"
                                   :data-url (str "/spreadsheet/" (name (:collection-key config)) "/reference-create")
                                   :hx-vals (json/write-value-as-string {:column (form-field-name column)
                                                                         :form form-id
                                                                         :q query})}
         "Create " [:span.pill query]]))))

(defn- picker [{:keys [config column form-id]}]
  (h/html
    [:dialog.reference-picker {:aria-label (str "Search " (name (:column/target-collection column)))}
     [:div.reference-picker-content
      [:input.reference-search {:type "search"
                                :name "q"
                                :placeholder "Search"
                                :aria-label "Search"
                                :autocomplete "off"
                                :data-1p-ignore true
                                :hx-post (str "/spreadsheet/" (name (:collection-key config)) "/reference-search")
                                :hx-trigger "input changed delay:150ms, reference-picker-open"
                                ;; Includes the referred entities, so that the search can exclude them
                                :hx-include "closest .reference-editor"
                                :hx-vals (json/write-value-as-string {:column (form-field-name column)
                                                                      :form form-id})
                                :hx-target "next .reference-results"
                                :hx-swap "innerHTML"}]
      [:div.reference-results]]]))

(defn editor [{:keys [column form-id focus?] :as ctx}]
  (h/html
    [:span.reference-editor {:data-multiple (str (boolean (:column/multiple? column)))}
     ;; Ensures that removing all references submits an empty value;
     ;; otherwise browsers omit the field entirely, which means "no change".
     [:input {:type "hidden"
              :form form-id
              :name (form-field-name column)
              :value ""}]
     (for [item (items ctx)]
       (pill ctx item))
     [:button.pill-button.editor-entry-point {:type "button"
                                              :autofocus focus?}
      ;; CSS shows only one icon, depending on whether a single-value column has a value
      [:span.reference-add {:role "img", :aria-label "Add"} "+"]
      (when-not (:column/multiple? column)
        [:span.reference-replace {:role "img", :aria-label "Replace"} "⇄"])]
     (picker ctx)]))

(defn parse-form-params [params column]
  (let [field-name (form-field-name column)]
    (when (contains? params field-name)
      (let [ids (value->ids (get params field-name))]
        {(:column/entity-key column) (if (:column/multiple? column)
                                       ids
                                       (first ids))}))))

(defn referring-entities
  "Returns descriptions of the entities whose forward references refer
   to the given entity. Deleting the entity would break those references."
  [config entity-id]
  (let [collection-key (:collection-key config)]
    (distinct
      (for [[source-collection source-config] (:target-configs config)
            column (:columns source-config)
            :when (and (= collection-key (:column/target-collection column))
                       (not (:column/back-reference column)))
            referring-id (db/referring-ids source-collection (:column/entity-key column) entity-id)
            :let [entity (db/get-by-id source-collection referring-id)]]
        {:collection-key source-collection
         :entity-id (:entity/id entity)
         :label (entity-label source-config entity)}))))

(def column-defaults
  {:column/type :reference
   :column/value-fn value
   :column/viewer viewer
   :column/editor editor
   :column/parse-form-params parse-form-params
   :column/target-collection nil
   :column/multiple? true})
