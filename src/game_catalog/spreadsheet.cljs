(ns game-catalog.spreadsheet
  (:require [clojure.string :as str]))

(defn data-cell [{:keys [*data data-path data-type]}]
  (let [value (get-in @*data data-path)]
    [:td [:div.data-cell {:style (when (= :money data-type)
                                   {:text-align "right"})}
          (case data-type
            :multi-select (str/join ", " value)
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
                              :data-type (:data-type column)}]))))]]))
