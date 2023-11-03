(ns game-catalog.db-test
  (:require ["@firebase/util" :refer [FirebaseError]]
            [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [game-catalog.db :as db]
            [game-catalog.firebase :as firebase]
            [game-catalog.testing :as testing]
            [kitchen-async.promise :as p]))

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

        (firebase/close! ctx)))))

(defn- assert-permission-denied [error]
  (is (some? error))
  (is (instance? FirebaseError error))
  (when (some? error)
    (is (str/starts-with? (.-message error) "PERMISSION_DENIED"))))

(def regular-user-id "4cwvdKFkbjKqW91UkQHyYyHlG5cK")
(def editor-user-id "irPVL4SRSRx965mKQC2tjL0EmM5j")

(deftest firestore-security-rules-test
  (testing/async
    (p/do
      (let [admin-ctx (firebase/init-emulator "owner")
            admin-db (:firestore admin-ctx)]
        (p/do
          (firebase/empty-firestore-test-database!)
          (db/update-collections! admin-db [[:users editor-user-id {:editor true}]
                                            [:games "id1" {:dummy "game 1"}]
                                            [:purchases "id1" {:dummy "purchase 1"}]])
          (firebase/close! admin-ctx)))

      (let [ctx (firebase/init-emulator)
            db (:firestore ctx)]
        (p/do

          ;;; Anonymous Users

          (firebase/sign-out! ctx)

          (testing "anonymous users cannot write games"
            (p/try
              (db/update-collections! db [[:games "id1" {:dummy "updated by anonymous user"}]])
              (is false "should have thrown an exception")
              (p/catch :default error
                (assert-permission-denied error))))

          (testing "anonymous users cannot write purchases"
            (p/try
              (db/update-collections! db [[:purchases "id1" {:dummy "updated by anonymous user"}]])
              (is false "should have thrown an exception")
              (p/catch :default error
                (assert-permission-denied error))))

          (testing "anonymous users can read games and purchases"
            (p/let [data (db/read-collections! db [:games :purchases])]
              (is (= {:games {"id1" {:dummy "game 1"}}
                      :purchases {"id1" {:dummy "purchase 1"}}}
                     data))))


          ;;; Regular Users

          (p/let [user-id (firebase/sign-in-as-regular-user! ctx)]
            (is (= regular-user-id user-id)))

          (testing "regular users cannot write games"
            (p/try
              (db/update-collections! db [[:games "id1" {:dummy "updated by regular user"}]])
              (is false "should have thrown an exception")
              (p/catch :default error
                (assert-permission-denied error))))

          (testing "regular users cannot write purchases"
            (p/try
              (db/update-collections! db [[:purchases "id1" {:dummy "updated by regular user"}]])
              (is false "should have thrown an exception")
              (p/catch :default error
                (assert-permission-denied error))))

          (testing "regular users can read games and purchases"
            (p/let [data (db/read-collections! db [:games :purchases])]
              (is (= {:games {"id1" {:dummy "game 1"}}
                      :purchases {"id1" {:dummy "purchase 1"}}}
                     data))))


          ;;; Editors

          (p/let [user-id (firebase/sign-in-as-editor! ctx)]
            (is (= editor-user-id user-id)))

          (testing "editors can write games"
            (p/do
              (db/update-collections! db [[:games "id1" {:dummy "updated by editor"}]])))

          (testing "editors can write purchases"
            (p/do
              (db/update-collections! db [[:purchases "id1" {:dummy "updated by editor"}]])))

          (testing "editors can read games and purchases"
            (p/let [data (db/read-collections! db [:games :purchases])]
              (is (= {:games {"id1" {:dummy "updated by editor"}}
                      :purchases {"id1" {:dummy "updated by editor"}}}
                     data))))

          (firebase/close! ctx))))))
