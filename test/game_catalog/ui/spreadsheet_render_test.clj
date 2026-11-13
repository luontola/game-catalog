(ns game-catalog.ui.spreadsheet-render-test
  (:require [clojure.test :refer :all]
            [game-catalog.testing.html :as html]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [game-catalog.ui.spreadsheet.numeric :as numeric])
  (:import (org.jsoup.nodes Element)))

;; Covers only a few column types, to cover generic spreadsheet functionality
;; instead of column type specifics.
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

(def computed-config
  {:collection-key :things
   :columns [{:column/name "Computed"
              :column/entity-key :thing/computed
              :column/value-fn (fn [{:keys [entity config column mode]}]
                                 (str (:thing/texty entity) " "
                                      (name (:collection-key config)) " "
                                      (:column/name column) " "
                                      (name mode)))}]})

(deftest computed-value-test
  (testing "view mode uses the column's value function"
    (is (= "Words things Computed view"
           (html/visualize-html (str (spreadsheet/view-row computed-config entity))))))

  (testing "edit mode uses the column's value function"
    (is (= "Words things Computed edit"
           (-> (html/parse-table-fragment (spreadsheet/edit-row computed-config entity))
               (.selectFirst "input")
               (.attr "value"))))))

(deftest view-row-test
  (testing "the row ID is the entity ID, for linking to the row"
    (let [doc (html/parse-table-fragment
                (spreadsheet/view-row config entity))]
      (is (= "1" (.attr (.selectFirst doc "tr") "id")))))

  (testing "renders column type classes on cells"
    (let [doc (html/parse-table-fragment
                (spreadsheet/view-row config entity))]
      (is (some? (.selectFirst doc "td.column-text")))
      (is (some? (.selectFirst doc "td.column-numeric"))))))

(deftest edit-row-test
  (testing "the row ID is the entity ID, for linking to the row"
    (let [doc (html/parse-table-fragment
                (spreadsheet/edit-row config entity))]
      (is (= "1" (.attr (.selectFirst doc "tr") "id")))))

  (testing "renders column type classes on editable cells"
    (let [doc (html/parse-table-fragment
                (spreadsheet/edit-row config entity))]
      (is (some? (.selectFirst doc "td.column-text")))
      (is (some? (.selectFirst doc "td.column-numeric"))))))

(deftest pick-table-test
  (let [entities [entity
                  (assoc entity :entity/id "2")
                  (assoc entity :entity/id "3")]
        doc (html/parse-fragment (spreadsheet/pick-table config entities (fn [entity]
                                                                           {:data-pick (:entity/id entity)})))]

    (testing "the rows know their entities"
      (is (= ["1" "2" "3"]
             (map #(.attr ^Element % "data-entity-id") (.select doc "tbody tr")))))

    (testing "the rows have the attributes for picking their entities"
      (is (= ["1" "2" "3"]
             (map #(.attr ^Element % "data-pick") (.select doc "tbody tr")))))

    (testing "the first row is highlighted, because the Enter key picks the highlighted row"
      (is (= [true false false]
             (map #(.hasClass ^Element % "highlighted") (.select doc "tbody tr")))))))
