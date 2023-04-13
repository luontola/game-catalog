(ns game-catalog.spreadsheet
  (:require [clojure.string :as str]
            [reagent.core :as r]))

(defn- format-multi-select-value [value]
  (str/join ", " value))

(defn data-cell [{:keys [*data data-path data-type reference-path]}]
  (r/with-let [*self (r/atom nil)
               *editing? (r/atom false)
               *edited-value (r/atom nil)]
    (let [value (get-in @*data data-path)]
      [:td {:tab-index (if @*editing? -1 0)
            :ref #(reset! *self %)
            :on-double-click (fn [_event]
                               (reset! *edited-value value)
                               (reset! *editing? true))}
       (if @*editing?
         [:input {:type "text"
                  :auto-focus true
                  :value @*edited-value
                  :on-blur (fn [_event]
                             (swap! *data assoc-in data-path @*edited-value)
                             (reset! *editing? false))
                  :on-change (fn [event]
                               (reset! *edited-value (str (.. event -target -value))))
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
            :multi-select (format-multi-select-value value)
            :reference (->> value
                            (map (fn [id]
                                   (if-some [record (get-in @*data (conj reference-path id))]
                                     ;; TODO: the record should determine itself that how to format it
                                     (case (first reference-path)
                                       :games (str (:name record))
                                       :purchases (format-multi-select-value (:shop record))
                                       (str record))
                                     (str id))))
                            (str/join "; "))
            (str value))])])))

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
