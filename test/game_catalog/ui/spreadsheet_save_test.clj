(ns game-catalog.ui.spreadsheet-save-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [game-catalog.ui.spreadsheet.numeric :as numeric]
            [game-catalog.ui.spreadsheet.select :as select]
            [mount.core :as mount]))

;; Covers every column type, to serve as an integration test
;; that covers the persistence flow from request to database.
(def things-config
  {:collection-key :things
   :id-generator spreadsheet/sequential-id-generator
   :sort-by :thing/texty
   :columns [{:column/name "Texty"
              :column/entity-key :thing/texty}
             (assoc numeric/column-defaults
               :column/name "Numbery"
               :column/entity-key :thing/numbery)
             (assoc select/column-defaults
               :column/name "Selecty"
               :column/entity-key :thing/selecty
               :column/options ["" "Good" "Bad"])]})

(defn reset-collections-fixture [f]
  (mount/start #'db/*collections)
  (try
    (f)
    (finally
      (mount/stop))))

(use-fixtures :each reset-collections-fixture)

(deftest save-row-handler-test
  (testing "parses form params using each column component"
    (db/init-collection! :things [{:entity/id "1"
                                   :thing/texty "Old"
                                   :thing/numbery 100
                                   :thing/selecty "Bad"}])

    ((spreadsheet/save-row-handler things-config)
     {:path-params {:entity-id "1"}
      :params {"thing/texty" "New"
               "thing/numbery" "200"
               "thing/selecty" "Good"}})

    (is (= {:entity/id "1"
            :thing/texty "New"
            :thing/numbery 200
            :thing/selecty "Good"}
           (db/get-by-id :things "1")))))
