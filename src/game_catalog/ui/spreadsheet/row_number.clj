(ns game-catalog.ui.spreadsheet.row-number
  (:require [game-catalog.infra.hiccup :as h]))

(defn viewer [_ctx]
  (h/html [:span.row-number]))

(def column-defaults
  {:column/type :row-number
   :column/viewer viewer
   :column/editor nil
   :column/read-only? true})
