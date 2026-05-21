(ns game-catalog.ui.spreadsheet.numeric
  (:require [game-catalog.ui.spreadsheet.text :as text]))

(def column-defaults
  {:column/viewer text/viewer
   :column/editor (fn [ctx]
                    (text/editor (assoc ctx :input-attrs {:type "number"})))})
