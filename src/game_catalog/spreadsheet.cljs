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

(defn data-cell [{:keys [*data data-path data-type reference-path]}]
  (r/with-let [*self (r/atom nil)
               *editing? (r/atom false)
               *form-value (r/atom nil)
               *parsed-value (r/atom nil)]
    (let [data-value (get-in @*data data-path)]
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
                             (let [old-value (get-in @*data data-path)
                                   new-value @*parsed-value]
                               ;; TODO: extract updating the back references to a function
                               (when (and (= :reference data-type)
                                          [:stuffs] reference-path) ; FIXME: remove hard-coded paths
                                 (let [old-value (set old-value)
                                       new-value (set new-value)
                                       added-ids (set/difference new-value old-value)
                                       removed-ids (set/difference old-value new-value)
                                       self-id (second data-path)] ; TODO: a direct way of getting the current record's ID
                                   (doseq [added-id added-ids]
                                     (swap! *data update-in [:stuffs added-id] (fn [record]
                                                                                 (let [new-values (-> (vec (:thingies record))
                                                                                                      (conj self-id))]
                                                                                   (assoc record :thingies new-values)))))
                                   (doseq [removed-id removed-ids]
                                     (swap! *data update-in [:stuffs removed-id] (fn [record]
                                                                                   (let [new-values (->> (:thingies record)
                                                                                                         (remove #(= self-id %))
                                                                                                         (vec))]
                                                                                     (if (empty? new-values)
                                                                                       (dissoc record :thingies)
                                                                                       (assoc record :thingies new-values))))))))
                               (swap! *data assoc-in data-path new-value))
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
                                   (if-some [record (get-in @*data (conj reference-path id))]
                                     ;; FIXME: the record should determine itself that how to format it
                                     (case (first reference-path)
                                       :stuffs (str (:name record))
                                       :games (str (:name record))
                                       :purchases (format-multi-select-value (:shop record))
                                       (str record))
                                     (str id))))
                            (str/join "; "))
            (str data-value))])])))

(defn table [{:keys [*data data-path sort-key columns]}]
  (let [records (->> (get-in @*data data-path)
                     (sort-by (comp sort-key val)))]
    [:table.spreadsheet
     [:thead
      (into [:tr]
            (for [column columns]
              [:th (str (:title column))]))]
     [:tbody
      (for [[id _record] records]
        (let [data-path (conj data-path id)]
          (into [:tr {:key (str id)}]
                (for [column columns]
                  [data-cell {:*data *data
                              :data-path (conj data-path (:data-path column))
                              :data-type (:data-type column)
                              :reference-path (:reference-path column)}]))))]]))
