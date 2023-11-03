(ns game-catalog.db-test
  (:require [cljs.test :refer [deftest is testing]]
            [game-catalog.db :as db]
            [game-catalog.firebase :as firebase]
            [game-catalog.testing :as testing]
            [promesa.core :as p]))

(deftest diff-test
  (let [before {:stuff {"id1" {:description "v1, should stay as is"}
                        "id2" {:description "v1, should be updated"}
                        "id3" {:description "v1, should be removed"}}}
        after {:stuff {"id1" {:description "v1, should stay as is"}
                       "id2" {:description "v2, should be updated"}
                       "id4" {:description "v2, should be added"}}}
        changes (db/diff before after)]
    (is (= [[:stuff "id2" {:description "v2, should be updated"}]
            [:stuff "id3" nil]
            [:stuff "id4" {:description "v2, should be added"}]]
           changes))))

(deftest firestore-test
  (testing/async
    (p/let [ctx (firebase/init-emulator "owner")
            db (:firestore ctx)]
      (p/do
        (firebase/empty-firestore-test-database!)

        (p/let [data (db/read-collections! db [:foo :bar])]
          (is (= {:foo {}
                  :bar {}}
                 data)
              "collections start empty"))

        (db/update-collections! db [[:foo "id1" {:dummy "foo 1"}]
                                    [:foo "id2" {:dummy "foo 2"}]
                                    [:bar "id1" {:dummy "bar 1"}]])
        (p/let [data (db/read-collections! db [:foo :bar])]
          (is (= {:foo {"id1" {:dummy "foo 1"}
                        "id2" {:dummy "foo 2"}}
                  :bar {"id1" {:dummy "bar 1"}}}
                 data)
              "create documents"))

        (db/update-collections! db [[:foo "id1" nil]
                                    [:bar "id1" {:dummy "bar 1 v2"}]])
        (p/let [data (db/read-collections! db [:foo :bar])]
          (is (= {:foo {"id2" {:dummy "foo 2"}}
                  :bar {"id1" {:dummy "bar 1 v2"}}}
                 data)
              "update/delete documents"))

        (firebase/terminate! ctx)))))
