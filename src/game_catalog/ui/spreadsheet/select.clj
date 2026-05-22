(ns game-catalog.ui.spreadsheet.select
  (:require [game-catalog.infra.hiccup :as h]
            [game-catalog.ui.spreadsheet.text :as text]))

(def form-field-name text/form-field-name)

(def viewer text/viewer)

(defn- option-values [column value]
  (let [options (:column/options column)]
    ;; If the database has a value which is not in the column config,
    ;; don't lose the old value, but add it to this cell's options.
    (cond-> options
      (and (some? value)
           (not (some #(= value %) options)))
      (conj value))))

(defn editor [{:keys [column value form-id focus?]}]
  (h/html
    [:select {:form form-id
              :name (form-field-name column)
              :autofocus focus?
              :autocomplete "off"
              :data-test-content (str "[" value "]")}
     (for [option (option-values column value)]
       [:option {:value option
                 :selected (= option value)}
        option])]))

(defn parse-form-params [params column]
  (let [field-name (form-field-name column)]
    (when (contains? params field-name)
      (let [value (get params field-name)]
        {(:column/entity-key column) value}))))

(def column-defaults
  {:column/type :select
   :column/viewer viewer
   :column/editor editor
   :column/parse-form-params parse-form-params
   :column/options []})
