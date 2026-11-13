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

(deftest save-and-continue-handler-test
  (let [save! (fn [entity-id params]
                ((spreadsheet/save-and-continue-handler things-config)
                 {:path-params {:entity-id entity-id}
                  :params params}))]
    (db/init-collection! :things [{:entity/id "1"
                                   :thing/texty "Old"
                                   :thing/numbery 100
                                   :thing/selecty "Bad"
                                   :thing/multiy ["Foo"]
                                   :thing/refy ["1"]}])

    (testing "parses form params using each column component"
      (let [response (save! "1" {"thing/texty" "New"
                                 "thing/numbery" "200"
                                 "thing/selecty" "Good"
                                 "thing/multiy" ["Foo" "Bar"]
                                 "thing/refy" ["" "1" "2"]
                                 ;; back references are computed on read, so they are never saved
                                 "thing/back-refy" ["" "1"]})]
        (is (= {:entity/id "1"
                :thing/texty "New"
                :thing/numbery 200
                :thing/selecty "Good"
                :thing/multiy ["Foo" "Bar"]
                :thing/refy ["1" "2"]}
               (db/get-by-id :things "1")))
        (is (= 204 (:status response))
            "returns no content, so that the row which the user is editing does not change")))

    (testing "saves only the fields which the form has"
      (save! "1" {"thing/texty" "Newer"})
      (is (= {:entity/id "1"
              :thing/texty "Newer"
              :thing/numbery 200
              :thing/selecty "Good"
              :thing/multiy ["Foo" "Bar"]
              :thing/refy ["1" "2"]}
             (db/get-by-id :things "1"))))

    (testing "does not write to the database when nothing changed"
      (let [collections-before @db/*collections
            response (save! "1" {"thing/texty" "Newer"
                                 "thing/numbery" "200"})]
        (is (= 204 (:status response)))
        (is (identical? collections-before @db/*collections))))

    (testing "unknown rows get an error message as plain text, which the frontend can show"
      (is (= {:status 404
              :headers {"Content-Type" "text/plain; charset=utf-8"}
              :body "Row not found. It was probably deleted."}
             (save! "2" {"thing/texty" "New"})))
      (is (nil? (db/get-by-id :things "2"))))))

(deftest save-row-handler-test
  (let [save! (fn [entity-id params]
                ((spreadsheet/save-row-handler things-config)
                 {:path-params {:entity-id entity-id}
                  :params params}))]
    (db/init-collection! :things [{:entity/id "1"
                                   :thing/texty "Old"
                                   :thing/numbery 100}])

    (testing "saves the form params, and returns the saved row in view mode"
      (let [response (save! "1" {"thing/texty" "New"})
            row (.selectFirst (html/parse-table-fragment (:body response)) "tr")]
        (is (= {:entity/id "1"
                :thing/texty "New"
                :thing/numbery 100}
               (db/get-by-id :things "1")))
        (is (.hasClass row "viewing"))
        (is (= "New 100" (html/visualize-html (str row))))))

    (testing "without form params, only returns the row in view mode"
      (let [collections-before @db/*collections
            response (save! "1" {})
            row (.selectFirst (html/parse-table-fragment (:body response)) "tr")]
        (is (identical? collections-before @db/*collections))
        (is (= "New 100" (html/visualize-html (str row))))))

    (testing "focuses the requested column"
      (let [response (save! "1" {:focusIndex "1"})
            cells (.select (html/parse-table-fragment (:body response)) "td")]
        (is (= [false true false false false false]
               (map #(.hasAttr ^Element % "autofocus") cells)))))

    (testing "unknown rows"
      (is (= 404 (:status (save! "2" {})))))))

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

(deftest reference-handler-errors-test
  (let [creatable-config (assoc things-config :reference-label :thing/texty)
        config (fn [target-config]
                 (assoc things-config :target-configs {:things target-config}))
        error (fn [handler params]
                (-> (handler {:params params})
                    (select-keys [:status :body])))]
    (db/init-collection! :things [])

    (testing "searching an unknown column"
      (is (= {:status 404, :body "Column not found"}
             (error (spreadsheet/reference-search-handler (config creatable-config))
                    {:column "thing/nope", :q "Foo"}))))

    (testing "creating to an unknown column"
      (is (= {:status 404, :body "Column not found"}
             (error (spreadsheet/reference-create-handler (config creatable-config))
                    {:column "thing/nope", :q "Foo"}))))

    (testing "creating to a target collection whose label cannot be set"
      (is (= {:status 400, :body "Cannot create entities in the target collection"}
             (error (spreadsheet/reference-create-handler (config things-config))
                    {:column "thing/refy", :q "Foo"}))))

    (testing "creating without a label"
      (is (= {:status 400, :body "Cannot create an entity without a label"}
             (error (spreadsheet/reference-create-handler (config creatable-config))
                    {:column "thing/refy", :q " "}))))

    (testing "errors don't create entities"
      (is (empty? (db/get-all :things))))))

(deftest reference-picker-handler-test
  (let [target-config (assoc things-config :reference-label :thing/texty)
        config (assoc things-config :target-configs {:things target-config})
        open-picker! (fn [entity-id params]
                       ((spreadsheet/reference-picker-handler config)
                        {:path-params {:entity-id entity-id}
                         :params params}))]
    (db/init-collection! :things [{:entity/id "1", :thing/texty "Foo", :thing/refy ["2"]}
                                  {:entity/id "2", :thing/texty "Bar"}
                                  {:entity/id "3", :thing/texty "Gazonk"}])

    (testing "saves the row, and returns the editor's content with the picker open"
      (let [response (open-picker! "1" {:column "thing/refy"
                                        "thing/texty" "Foo 2"
                                        "thing/refy" ["" "2" "3"]})
            doc (html/parse-fragment (:body response))]
        (is (= {:entity/id "1", :thing/texty "Foo 2", :thing/refy ["2" "3"]}
               (db/get-by-id :things "1")))
        (is (= ["Bar ×" "Gazonk ×"]
               (map #(.text ^Element %) (.select doc "body > .pill"))))
        (is (some? (.selectFirst doc "body > dialog.reference-picker[auto-show-modal]")))

        (testing "with the entities which the saved row doesn't refer to"
          (is (= ["1"]
                 (map #(.attr ^Element % "data-entity-id") (.select doc ".reference-results tbody tr")))))))

    (testing "unknown columns"
      (is (= {:status 404, :body "Column not found"}
             (select-keys (open-picker! "1" {:column "thing/nope"}) [:status :body]))))

    (testing "unknown rows"
      (is (= 404 (:status (open-picker! "4" {:column "thing/refy"})))))))
