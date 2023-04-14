(ns game-catalog.spreadsheet
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [reagent.core :as r]))

(defn- format-multi-select-value [value]
  (str/join ", " value))

(defn- parse-id [s]
  (or (parse-long s)
      (parse-uuid s)
      s))

(defn update-back-references [data {:keys [old-value new-value self-id
                                           reference-collection reference-foreign-key]}]
  (let [old-value (set old-value)
        new-value (set new-value)
        added-ids (set/difference new-value old-value)
        removed-ids (set/difference old-value new-value)
        data (reduce (fn [data added-id]
                       (update-in data [reference-collection :documents added-id]
                                  (fn [document]
                                    (let [new-values (-> (vec (get document reference-foreign-key))
                                                         (conj self-id))]
                                      (assoc document reference-foreign-key new-values)))))
                     data
                     added-ids)
        data (reduce (fn [data removed-id]
                       (update-in data [reference-collection :documents removed-id]
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
  (let [data-path [self-collection :documents self-id self-field]
        old-value (get-in data data-path)
        data (cond-> data
               (= :reference data-type)
               (update-back-references (assoc context
                                         :old-value old-value)))
        data (assoc-in data data-path new-value)]
    data))

(defn data-cell [*data {:keys [data-type
                               self-collection self-id self-field
                               reference-collection reference-foreign-key]
                        :as context}]
  (r/with-let [*self (r/atom nil)
               *editing? (r/atom false)
               *form-value (r/atom nil)
               *parsed-value (r/atom nil)]
    (let [data-path [self-collection :documents self-id self-field]
          data-value (get-in @*data data-path)]
      [:td {:tab-index (if @*editing? -1 0)
            :ref #(reset! *self %)
            :on-double-click (fn [_event]
                               ;; TODO: extract conversion between internal and UI representations
                               (reset! *form-value (case data-type
                                                     :multi-select (str/join "; " data-value)
                                                     :reference (str/join "; " data-value)
                                                     data-value))
                               (reset! *parsed-value data-value)
                               (reset! *editing? true))}
       (if @*editing?
         [:input {:type "text"
                  :auto-focus true
                  :value @*form-value
                  :on-blur (fn [_event]
                             (swap! *data update-field (assoc context
                                                         :new-value @*parsed-value))
                             (reset! *editing? false))
                  :on-change (fn [event]
                               (let [form-value (str (.. event -target -value))
                                     ;; TODO: extract conversion between internal and UI representations
                                     parsed-value (case data-type
                                                    :multi-select (->> (str/split form-value #";")
                                                                       (mapv str/trim))
                                                    :reference (->> (str/split form-value #";")
                                                                    (map str/trim)
                                                                    (remove str/blank?)
                                                                    (mapv parse-id))
                                                    form-value)]
                                 (reset! *form-value form-value)
                                 (reset! *parsed-value parsed-value)))
                  :style {:width "100%"
                          :height "36px"
                          :border 0
                          :margin-right "-1px"
                          :padding "6px 5px 6px 6px"
                          :background-color "transparent"
                          :outline "3px solid #4884f9"
                          :outline-offset "-1px"}}]

         [:div.data-cell {:style (when (= :money data-type)
                                   {:text-align "right"})}
          (case data-type
            :multi-select (format-multi-select-value data-value)
            :reference (->> data-value
                            (map (fn [id]
                                   (if-some [reference-document (get-in @*data [reference-collection :documents id])]
                                     ;; FIXME: the record should determine itself that how to format it
                                     ;; TODO: field types as metadata
                                     ;; TODO: primary display field as metadata
                                     (case reference-collection
                                       :stuffs (str (:name reference-document))
                                       :games (str (:name reference-document))
                                       :purchases (format-multi-select-value (:shop reference-document))
                                       (str reference-document))
                                     (str id))))
                            (str/join "; "))
            (str data-value))])])))

(defn table [{:keys [*data self-collection sort-key columns]}]
  (let [documents-by-id (->> (get-in @*data [self-collection :documents])
                             (sort-by (comp sort-key val)))]
    [:table.spreadsheet
     [:thead
      (into [:tr]
            (for [column columns]
              [:th (str (:title column))]))]
     [:tbody
      (for [[self-id _document] documents-by-id]
        (into [:tr {:key (str self-id)}]
              (for [column columns]
                [data-cell *data {:data-type (:data-type column)
                                  :self-collection self-collection
                                  :self-id self-id
                                  :self-field (:field column)
                                  :reference-collection (:reference-collection column)
                                  :reference-foreign-key (:reference-foreign-key column)}])))]]))
