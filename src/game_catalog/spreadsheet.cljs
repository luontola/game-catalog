(ns game-catalog.spreadsheet
  (:require [clojure.string :as str]))

(defn- format-multi-select-value [value]
  (str/join ", " value))

(defn data-cell [{:keys [*data data-path data-type reference-path]}]
  (let [value (get-in @*data data-path)]
    [:td [:div.data-cell {:style (when (= :money data-type)
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
            (str value))]]))

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
