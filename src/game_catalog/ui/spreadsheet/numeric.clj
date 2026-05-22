(ns game-catalog.ui.spreadsheet.numeric
  (:require [clojure.string :as str]
            [game-catalog.ui.spreadsheet.text :as text]))

(def form-field-name text/form-field-name)

(def viewer text/viewer)

(def editor (fn [ctx]
              (text/editor (assoc ctx :input-attrs {:type "number"}))))

(defn parse-form-params [params column]
  (let [field-name (form-field-name column)]
    (when (contains? params field-name)
      (let [value (get params field-name)
            parsed (when-not (str/blank? value)
                     (parse-long value))]
        {(:column/entity-key column) parsed}))))

(def column-defaults
  {:column/viewer viewer
   :column/editor editor
   :column/parse-form-params parse-form-params})
