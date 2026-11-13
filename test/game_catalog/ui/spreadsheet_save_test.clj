(ns game-catalog.ui.spreadsheet-save-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.testing.html :as html]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [game-catalog.ui.spreadsheet.multiselect :as multiselect]
            [game-catalog.ui.spreadsheet.numeric :as numeric]
            [game-catalog.ui.spreadsheet.reference :as reference]
            [game-catalog.ui.spreadsheet.select :as select]
            [mount.core :as mount])
  (:import (org.jsoup.nodes Document Element)))

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
               :column/options ["" "Good" "Bad"])
             (assoc multiselect/column-defaults
               :column/name "Multiy"
               :column/entity-key :thing/multiy
               :column/options ["Foo" "Bar" "Gazonk"])
             (assoc reference/column-defaults
               :column/name "Refy"
               :column/entity-key :thing/refy
               :column/target-collection :things)
             (assoc reference/column-defaults
               :column/name "Back Refy"
               :column/entity-key :thing/back-refy
               :column/target-collection :things
               :column/back-reference :thing/refy
               :column/read-only? true)]})

(defn reset-collections-fixture [f]
  (mount/start #'db/*collections)
  (try
    (f)
    (finally
      (mount/stop))))

(use-fixtures :each reset-collections-fixture)

(deftest create-row-handler-test
  (db/init-collection! :things [{:entity/id "1"
                                 :thing/texty "Old"}])
  (let [config (update things-config :columns #(into [{:column/name "ID"
                                                       :column/entity-key :entity/id
                                                       :column/read-only? true}]
                                                     %))
        create! (fn [params]
                  (-> ((spreadsheet/create-row-handler config) {:params params})
                      :body
                      (html/parse-table-fragment)))
        autofocused (fn [^Document doc]
                      (->> (.select doc "[autofocus]")
                           (map #(.attr ^Element % "name"))))]

    (testing "saves a blank entity, which has the same values as an empty form"
      (create! {})
      (is (= {:entity/id "2"
              :thing/texty ""
              :thing/numbery nil
              :thing/selecty ""
              :thing/multiy []
              :thing/refy []}
             (db/get-by-id :things "2"))))

    (testing "returns the new row in edit mode"
      (let [row (.selectFirst ^Document (create! {}) "tr")]
        (is (.hasClass row "editing"))
        (is (= "3" (.attr row "data-entity-id")))))

    (testing "focuses the first editable column by default"
      (is (= ["thing/texty"]
             (autofocused (create! {})))))

    (testing "focuses the requested column"
      (is (= ["thing/numbery"]
             (autofocused (create! {:focusIndex "2"})))))))

(deftest save-row-handler-test
  (testing "parses form params using each column component"
    (db/init-collection! :things [{:entity/id "1"
                                   :thing/texty "Old"
                                   :thing/numbery 100
                                   :thing/selecty "Bad"
                                   :thing/multiy ["Foo"]
                                   :thing/refy ["1"]}])

    ((spreadsheet/save-row-handler things-config)
     {:path-params {:entity-id "1"}
      :params {"thing/texty" "New"
               "thing/numbery" "200"
               "thing/selecty" "Good"
               "thing/multiy" ["Foo" "Bar"]
               "thing/refy" ["" "1" "2"]
               ;; back references are computed on read, so they are never saved
               "thing/back-refy" ["" "1"]}})

    (is (= {:entity/id "1"
            :thing/texty "New"
            :thing/numbery 200
            :thing/selecty "Good"
            :thing/multiy ["Foo" "Bar"]
            :thing/refy ["1" "2"]}
           (db/get-by-id :things "1")))))

(deftest delete-row-handler-test
  (let [config (assoc things-config :target-configs {:things things-config})
        delete! (fn [entity-id]
                  ((spreadsheet/delete-row-handler config)
                   {:path-params {:entity-id entity-id}}))]
    (db/init-collection! :things [{:entity/id "1"
                                   :thing/texty "Foo"
                                   :thing/refy ["2"]}
                                  {:entity/id "2"
                                   :thing/texty "Bar"}])

    (testing "rows which other rows refer to cannot be deleted"
      (let [response (delete! "2")]
        (is (= 409 (:status response)))
        (is (= "Cannot delete this row, because other rows refer to it:\n- things: 1"
               (:body response)))
        (is (some? (db/get-by-id :things "2")))))

    (testing "rows which no other rows refer to can be deleted"
      (is (= 200 (:status (delete! "1"))))
      (is (nil? (db/get-by-id :things "1"))))

    (testing "after the referring row was deleted, the referred row can be deleted"
      (is (= 200 (:status (delete! "2"))))
      (is (nil? (db/get-by-id :things "2"))))

    (testing "unknown rows"
      (is (= 404 (:status (delete! "3")))))))
