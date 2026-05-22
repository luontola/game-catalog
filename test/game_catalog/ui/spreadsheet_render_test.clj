(ns game-catalog.ui.spreadsheet-render-test
  (:require [clojure.test :refer :all]
            [game-catalog.testing.html :as html]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [game-catalog.ui.spreadsheet.numeric :as numeric]))

(def config
  {:collection-key :things
   :columns [{:column/name "Texty"
              :column/entity-key :thing/texty}
             (assoc numeric/column-defaults
               :column/name "Numbery"
               :column/entity-key :thing/numbery)]})

(def entity
  {:entity/id "1"
   :thing/texty "Words"
   :thing/numbery 123})

(deftest view-row-test
  (testing "renders column type classes on cells"
    (let [doc (html/parse-table-fragment
                (spreadsheet/view-row config entity))]
      (is (some? (.selectFirst doc "td.column-text")))
      (is (some? (.selectFirst doc "td.column-numeric"))))))

(deftest edit-row-test
  (testing "renders column type classes on editable cells"
    (let [doc (html/parse-table-fragment
                (spreadsheet/edit-row config entity))]
      (is (some? (.selectFirst doc "td.column-text")))
      (is (some? (.selectFirst doc "td.column-numeric"))))))
