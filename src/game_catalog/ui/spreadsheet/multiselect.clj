(ns game-catalog.ui.spreadsheet.multiselect
  (:require [clojure.string :as str]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.ui.spreadsheet.text :as text]))

(def form-field-name text/form-field-name)

(defn- value->vector [value]
  (cond
    (nil? value) []
    (sequential? value) (->> value
                             (map str)
                             (remove str/blank?)
                             vec)
    (str/blank? value) []
    :else [(str value)]))

(defn viewer [{:keys [value]}]
  (h/html (str/join ", " (value->vector value))))

(defn- option-values [column value]
  (->> (concat (value->vector (:column/options column))
               (value->vector value))
       distinct
       vec))

(defn editor [{:keys [column value form-id focus?]}]
  (let [field-name (form-field-name column)
        selected-values (set (value->vector value))]
    (h/html
      ;; Ensures that clearing all selections submits an empty value;
      ;; otherwise browsers omit the field entirely, which means "no change".
      [:input {:type "hidden"
               :form form-id
               :name field-name
               :value ""}]
      [:select {:form form-id
                :name field-name
                :multiple true
                :size 1
                :autofocus focus?
                :autocomplete "off"
                :data-test-content (str "[" value "]")}
       (for [option (option-values column value)]
         [:option {:value option
                   :selected (contains? selected-values option)}
          option])])))

(defn parse-form-params [params column]
  (let [field-name (form-field-name column)]
    (when (contains? params field-name)
      {(:column/entity-key column) (value->vector (get params field-name))})))

(def column-defaults
  {:column/type :multiselect
   :column/viewer viewer
   :column/editor editor
   :column/parse-form-params parse-form-params
   :column/options []})
