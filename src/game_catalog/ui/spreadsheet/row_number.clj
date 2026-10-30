(ns game-catalog.ui.spreadsheet.row-number
  (:require [game-catalog.infra.hiccup :as h]))

;; The row number is known only when rendering the full table.
;; Rows rendered individually leave it empty, so that CSS will show it
;; using the row-number counter.
(defn viewer [ctx]
  (h/html [:span.row-number (:row-number ctx)]))

(def column-defaults
  {:column/type :row-number
   :column/viewer viewer
   :column/editor nil
   :column/read-only? true})
