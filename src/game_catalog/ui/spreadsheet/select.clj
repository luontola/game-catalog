(ns game-catalog.ui.spreadsheet.select
  (:require [game-catalog.infra.hiccup :as h]
            [game-catalog.ui.spreadsheet.text :as text]))

(def viewer text/viewer)

(defn- option-values [column value]
  (let [options (:column/options column)]
    ;; If the database has a value which is not in the column config,
    ;; don't lose the old value, but add it to this cell's options.
    (cond-> options
      (and (some? value)
           (not (some #(= value %) options)))
      (conj value))))

(defn editor [{:keys [column value] :as ctx}]
  (h/html
    [:select (merge (text/editor-attrs ctx)
                    {:data-test-content (str "[" value "]")})
     (for [option (option-values column value)]
       [:option {:value option
                 :selected (= option value)}
        option])]))

(def parse-form-params text/parse-form-params)

(def column-defaults
  {:column/type :select
   :column/viewer viewer
   :column/editor editor
   :column/parse-form-params parse-form-params
   :column/options []})
