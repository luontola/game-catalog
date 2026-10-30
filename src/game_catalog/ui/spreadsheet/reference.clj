(ns game-catalog.ui.spreadsheet.reference
  (:require [clojure.string :as str]
            [game-catalog.data.db :as db]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.ui.spreadsheet.text :as text]))

;; The value is entity IDs referring to the :column/target-collection.
;; With :column/multiple? it's a vector of IDs, otherwise a single ID.
;; The target collection's config determines how the entities are shown,
;; using its :reference-label and :reference-tooltip functions.

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

(defn viewer [{:keys [column mode] :as ctx}]
  (let [target-config (target-config ctx)
        target-collection (:column/target-collection column)
        {:keys [entities missing-ids]} (resolve-references ctx)
        items (concat (for [entity entities]
                        {:id (:entity/id entity)
                         :label (entity-label target-config entity)
                         :tooltip (entity-tooltip target-config entity)})
                      (for [id missing-ids]
                        {:id id
                         :missing? true}))
        ;; Links in edit mode would make it too easy to lose unsaved changes
        links? (not= :edit mode)]
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

(defn- options [ctx]
  (let [target-config (target-config ctx)
        target-collection (get-in ctx [:column :column/target-collection])
        {:keys [missing-ids]} (resolve-references ctx)]
    (concat (for [entity (sort-entities target-config (db/get-all target-collection))]
              {:id (:entity/id entity)
               :label (entity-label target-config entity)})
            ;; Don't lose the old value, even if it refers to a missing entity
            (for [id missing-ids]
              {:id id
               :label id}))))

(defn editor [{:keys [column value form-id] :as ctx}]
  (let [field-name (form-field-name column)
        selected-ids (set (value->ids value))]
    (if (:column/multiple? column)
      (h/html
        ;; Ensures that clearing all selections submits an empty value;
        ;; otherwise browsers omit the field entirely, which means "no change".
        [:input {:type "hidden"
                 :form form-id
                 :name field-name
                 :value ""}]
        [:select (merge (text/editor-attrs ctx)
                        {:multiple true
                         :size 1})
         (for [{:keys [id label]} (options ctx)]
           [:option {:value id
                     :selected (contains? selected-ids id)}
            label])])
      (h/html
        [:select (text/editor-attrs ctx)
         [:option {:value ""}]
         (for [{:keys [id label]} (options ctx)]
           [:option {:value id
                     :selected (contains? selected-ids id)}
            label])]))))

(defn parse-form-params [params column]
  (let [field-name (form-field-name column)]
    (when (contains? params field-name)
      (let [ids (value->ids (get params field-name))]
        {(:column/entity-key column) (if (:column/multiple? column)
                                       ids
                                       (first ids))}))))

(def column-defaults
  {:column/type :reference
   :column/viewer viewer
   :column/editor editor
   :column/parse-form-params parse-form-params
   :column/target-collection nil
   :column/multiple? true})
