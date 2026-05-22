(ns game-catalog.ui.spreadsheet-render-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [game-catalog.ui.spreadsheet.numeric :as numeric]))

(def things-config
  {:collection-key :things
   :columns [{:column/name "Texty"
              :column/entity-key :thing/texty}
             (assoc numeric/column-defaults
               :column/name "Numbery"
               :column/entity-key :thing/numbery)]})

(deftest view-row-test
  (testing "renders column type classes on cells"
    (let [html (str (spreadsheet/view-row things-config
                                          {:entity/id "1"
                                           :thing/texty "Words"
                                           :thing/numbery 123}))]
      (is (str/includes? html "<td class=\"column-text\" tabindex=\"0\">Words</td>"))
      (is (str/includes? html "<td class=\"column-numeric\" tabindex=\"0\">123</td>")))))

(deftest edit-row-test
  (testing "renders column type classes on editable cells"
    (let [html (str (spreadsheet/edit-row things-config
                                          {:entity/id "1"
                                           :thing/texty "Words"
                                           :thing/numbery 123}))]
      (is (str/includes? html "<td class=\"column-text\"><input"))
      (is (str/includes? html "<td class=\"column-numeric\"><input")))))
