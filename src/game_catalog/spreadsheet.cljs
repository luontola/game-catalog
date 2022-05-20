(ns game-catalog.spreadsheet
  (:require [clojure.string :as str]))

(defn data-cell [{:keys [*data data-path data-type]}]
  (let [value (get-in @*data data-path)]
    [:td [:div.data-cell {:style (when (= :money data-type)
                                   {:text-align "right"})}
          (case data-type
            :multi-select (str/join ", " value)
            (str value))]]))
