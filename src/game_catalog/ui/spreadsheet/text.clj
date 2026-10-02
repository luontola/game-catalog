(ns game-catalog.ui.spreadsheet.text
  (:require [game-catalog.infra.hiccup :as h]))

(defn form-field-name [column]
  (subs (str (:column/entity-key column)) 1)) ; namespaced keyword without the ":" prefix

;; Attributes which every editor's main form element requires
(defn editor-attrs [{:keys [column value form-id focus?]}]
  {:class "editor-entry-point"
   :form form-id
   :name (form-field-name column)
   :autofocus focus?
   :autocomplete "off"
   :data-test-content (str "[" value "]")})

(defn- value->string [value]
  (str value))

(defn viewer [{:keys [value]}]
  (h/html (value->string value)))

(defn editor [{:keys [value input-attrs] :as ctx}]
  (let [value (value->string value)]
    (h/html
      [:input (merge {:type "text"
                      :value value
                      :data-1p-ignore true} ; for 1Password, https://developer.1password.com/docs/web/compatible-website-design/
                     input-attrs
                     (editor-attrs ctx))])))

(defn parse-form-params [params column]
  (let [field-name (form-field-name column)]
    (when (contains? params field-name)
      (let [value (get params field-name)]
        {(:column/entity-key column) value}))))

(def column-defaults
  {:column/type :text
   :column/viewer viewer
   :column/editor editor
   :column/parse-form-params parse-form-params})
