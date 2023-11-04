(ns game-catalog.spreadsheet
  (:require ["react-select/creatable$default" :as CreatableSelect]
            [clojure.set :as set]
            [clojure.string :as str]
            [reagent.core :as r]))

(def ^:dynamic new-id (comp str random-uuid))

(defn- format-multi-select-value [value]
  (str/join ", " value))

(defn update-back-references [data {:keys [old-value new-value self-id
                                           reference-collection reference-foreign-key]}]
  (let [old-value (set old-value)
        new-value (set new-value)
        added-ids (set/difference new-value old-value)
        removed-ids (set/difference old-value new-value)
        data (reduce (fn [data added-id]
                       (update-in data [reference-collection added-id]
                                  (fn [document]
                                    (let [new-values (-> (vec (get document reference-foreign-key))
                                                         (conj self-id))]
                                      (assoc document reference-foreign-key new-values)))))
                     data
                     added-ids)
        data (reduce (fn [data removed-id]
                       (update-in data [reference-collection removed-id]
                                  (fn [document]
                                    (let [new-values (->> (get document reference-foreign-key)
                                                          (remove #(= self-id %))
                                                          (vec))]
                                      (if (empty? new-values)
                                        (dissoc document reference-foreign-key)
                                        (assoc document reference-foreign-key new-values))))))
                     data
                     removed-ids)]
    data))

(defn update-field [data {:keys [new-value data-type
                                 self-collection self-id self-field
                                 reference-collection reference-foreign-key]
                          :as context}]
  (let [data-path [self-collection self-id self-field]
        old-value (get-in data data-path)
        data (cond-> data
               (= :reference data-type)
               (update-back-references (assoc context
                                         :old-value old-value)))
        data (assoc-in data data-path new-value)]
    data))

(defn visualize-document [document collection]
  ;; FIXME: the record should determine itself that how to format it
  ;; TODO: field types as metadata
  (case collection ; TODO: move knowledge about primary field to metadata
    :stuffs (str (:name document))
    :games (str (:name document))
    :purchases (format-multi-select-value (:shop document))
    (str document)))

(defn visualize-reference [data reference-collection id]
  ;; TODO: use reagent.core/cursor to avoid refreshing the whole table on edit
  (if-some [reference-document (get-in data [reference-collection id])]
    (visualize-document reference-document reference-collection)
    (str id)))

(defn create-document [data {:keys [collection id primary-value]}]
  (let [primary-field (case collection ; TODO: move knowledge about primary field to metadata
                        :stuffs :name
                        :games :name)
        document {primary-field primary-value}
        document-path [collection id]]
    (assert (nil? (get-in data document-path))
            (str document-path " already exists"))
    (assoc-in data document-path document)))

(defn init-reference-document! [*data reference-collection form-item]
  (let [{:keys [__isNew__ label]} (js->clj form-item :keywordize-keys true)]
    (if __isNew__
      (let [id (new-id)]
        (swap! *data create-document {:collection reference-collection
                                      :id id
                                      :primary-value label})
        (clj->js {:label (visualize-reference @*data reference-collection id)
                  :value (str id)}))
      form-item)))

(defn reference-editor [*data {:keys [on-exit-editing data-type
                                      self-collection self-id self-field
                                      reference-collection reference-foreign-key]
                               :as context}]
  (r/with-let [data-path [self-collection self-id self-field]
               data-value (get-in @*data data-path)
               *form-value (r/atom (->> data-value
                                        (map (fn [id]
                                               {:label (visualize-reference @*data reference-collection id)
                                                :value (str id)}))
                                        (clj->js)))]
    [:> CreatableSelect {:autoFocus true
                         :isMulti true
                         :isSearchable true
                         :isClearable false
                         :backspaceRemovesValue true
                         :value @*form-value
                         :options (for [[id document] (get @*data reference-collection)]
                                    {:label (visualize-document document reference-collection)
                                     :value (str id)})
                         ;; TODO: exit edit mode with Escape
                         ;; TODO: exit edit mode with Enter
                         :onBlur (fn [_event]
                                   (let [new-value (->> (js->clj @*form-value :keywordize-keys true)
                                                        (mapv :value))]
                                     ;; TODO: move update-field call inside on-exit-editing
                                     (swap! *data update-field (assoc context
                                                                 :new-value new-value)))
                                   (on-exit-editing))
                         :onChange (fn [values]
                                     (reset! *form-value (mapv #(init-reference-document! *data reference-collection %)
                                                               values)))}]))

(defn default-editor [*data {:keys [on-exit-editing data-type
                                    self-collection self-id self-field]
                             :as context}]
  (r/with-let [data-path [self-collection self-id self-field]
               data-value (get-in @*data data-path)
               *form-value (r/atom (case data-type
                                     ;; TODO: use react-select also for :multi-select
                                     :multi-select (str/join "; " data-value)
                                     data-value))]
    [:input {:type "text"
             :auto-focus true
             :value @*form-value
             ;; TODO: exit edit mode with Escape
             ;; TODO: exit edit mode with Enter
             :on-blur (fn [_event]
                        (let [form-value @*form-value
                              ;; TODO: extract conversion between internal and UI representations
                              parsed-value (case data-type
                                             ;; TODO: use react-select also for :multi-select
                                             :multi-select (->> (str/split form-value #";")
                                                                (mapv str/trim))
                                             form-value)]
                          ;; TODO: move update-field call inside on-exit-editing
                          (swap! *data update-field (assoc context
                                                      :new-value parsed-value)))
                        (on-exit-editing))
             :on-change (fn [event]
                          (let [form-value (str (.. event -target -value))
                                ;; TODO: extract conversion between internal and UI representations
                                parsed-value (case data-type
                                               ;; TODO: use react-select also for :multi-select
                                               :multi-select (->> (str/split form-value #";")
                                                                  (mapv str/trim))
                                               form-value)]
                            (reset! *form-value form-value)))
             :style {:width "100%"
                     :height "36px"
                     :border 0
                     :margin-right "-1px"
                     :padding "6px 5px 6px 6px"
                     :background-color "transparent"
                     :outline "3px solid #4884f9"
                     :outline-offset "-1px"}}]))

(defn data-cell [*data {:keys [data-type
                               self-collection self-id self-field
                               reference-collection reference-foreign-key]
                        :as context}]
  (r/with-let [*td-element (r/atom nil) ; TODO: dead code (could perhaps be used for table navigation)
               *editing? (r/atom false)
               on-exit-editing #(reset! *editing? false)]
    (let [data-path [self-collection self-id self-field]
          ;; TODO: use reagent.core/cursor to avoid refreshing the whole table on edit
          data-value (get-in @*data data-path)]
      [:td {:tab-index (if @*editing? -1 0)
            :ref #(reset! *td-element %)
            :on-key-down (fn [event]
                           (when (or (= "F2" (.-key event))
                                     (= "Enter" (.-key event)))
                             (reset! *editing? true)))
            :on-double-click (fn [_event]
                               (reset! *editing? true))}
       (if @*editing?
         (let [context (assoc context
                         :on-exit-editing on-exit-editing)]
           (case data-type
             :reference [reference-editor *data context]
             [default-editor *data context]))

         ;; TODO: move cell focus with arrows
         ;; TODO: extract viewer components for the various data types
         [:div.data-cell {:style (when (= :money data-type)
                                   {:text-align "right"})}
          (case data-type
            :multi-select (format-multi-select-value data-value)
            :reference (->> data-value
                            (map (fn [id]
                                   (visualize-reference @*data reference-collection id)))
                            (str/join "; "))
            (str data-value))])])))

(defn table [{:keys [*data self-collection sort-key columns]}]
  ;; TODO: use reagent.core/track for the sorted list of documents (or just list of IDs) to avoid refreshing the whole table on edit
  (let [documents-by-id (->> (get @*data self-collection)
                             ;; TODO: store new row's ID separately for sorting, until the row is unfocused
                             (sort-by (juxt (comp empty? val)
                                            (comp sort-key val))))]
    [:<>
     [:table.spreadsheet
      [:thead
       (into [:tr]
             (for [column columns]
               [:th (str (:title column))]))]
      [:tbody
       ;; TODO: an empty pseudo row at the bottom for adding new documents
       (for [[self-id _document] documents-by-id]
         (into [:tr {:key (str self-id)}]
               (for [column columns]
                 [data-cell *data {:data-type (:data-type column)
                                   :self-collection self-collection
                                   :self-id self-id
                                   :self-field (:field column)
                                   :reference-collection (:reference-collection column)
                                   :reference-foreign-key (:reference-foreign-key column)}])))]]
     [:p [:button {:type "button"
                   :on-click (fn []
                               (swap! *data assoc-in [self-collection (new-id)] {}))}
          "Add row"]]]))
