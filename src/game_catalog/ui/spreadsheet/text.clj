(ns game-catalog.ui.spreadsheet.text
  (:require [game-catalog.infra.hiccup :as h]))

(defn viewer [{:keys [value]}]
  (h/html value))

(defn editor [{:keys [column value form-id focus? input-attrs]}]
  (h/html
    [:input (merge {:type "text"
                    :form form-id
                    :name (subs (str (:column/entity-key column)) 1) ; namespaced keyword without the ":" prefix
                    :value value
                    :data-test-content (str "[" value "]")
                    :autofocus focus?
                    :autocomplete "off"
                    :data-1p-ignore true} ; for 1Password, https://developer.1password.com/docs/web/compatible-website-design/
                   input-attrs)]))

(def column-defaults
  {:column/viewer viewer
   :column/editor editor})
