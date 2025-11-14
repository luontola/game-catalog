(ns game-catalog.data.db-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [mount.core :as mount]))

(defn reset-collections-fixture [f]
  (mount/start #'db/*collections)
  (f)
  (mount/stop))

(use-fixtures :each reset-collections-fixture)

(deftest init-collection-test
  (testing "puts entities into the collection"
    (let [entities [{:entity/id "1", :name "Thing 1"}
                    {:entity/id "2", :name "Thing 2"}
                    {:entity/id "3", :name "Thing 3"}]]
      (db/init-collection! :things entities)
      (is (= {:things {"1" {:entity/id "1", :name "Thing 1"}
                       "2" {:entity/id "2", :name "Thing 2"}
                       "3" {:entity/id "3", :name "Thing 3"}}}
             @db/*collections))))

  (testing "overwrites existing collection"
    (db/init-collection! :things [{:entity/id "1", :name "Old"}])
    (db/init-collection! :things [{:entity/id "2", :name "New"}])
    (is (= {:things {"2" {:entity/id "2", :name "New"}}}
           @db/*collections)))

  (testing "doesn't affect unrelated collections"
    (db/init-collection! :things [{:entity/id "1", :name "Thing"}])
    (db/init-collection! :stuff [{:entity/id "2", :name "Stuff"}])
    (is (= {:things {"1" {:entity/id "1", :name "Thing"}}
            :stuff {"2" {:entity/id "2", :name "Stuff"}}}
           @db/*collections)))

  (testing "error: missing :entity/id"
    (is (thrown? AssertionError
                 (db/init-collection! :things [{:name "No ID"}])))))

(deftest get-all-test
  (testing "non-existent collection"
    (is (empty? (db/get-all :nonexistent))))

  (testing "empty collection"
    (db/init-collection! :things [])
    (is (empty? (db/get-all :things))))

  (testing "returns all entities in the collection"
    (let [entities [{:entity/id "1", :name "A"}
                    {:entity/id "2", :name "B"}
                    {:entity/id "3", :name "C"}]]
      (db/init-collection! :things entities)
      (is (= (set entities) (set (db/get-all :things)))))))

(deftest get-by-id-test
  (let [entity-1 {:entity/id "1", :name "A"}
        entity-2 {:entity/id "2", :name "B"}]
    (db/init-collection! :things [entity-1
                                  entity-2])

    (testing "non-existent collection"
      (is (nil? (db/get-by-id :nonexistent "1"))))

    (testing "non-existent entity"
      (is (nil? (db/get-by-id :things "666"))))

    (testing "returns the specified entity"
      (is (= entity-1 (db/get-by-id :things "1")))
      (is (= entity-2 (db/get-by-id :things "2"))))))

(deftest save-test
  (testing "adds new entity"
    (let [entity-new {:entity/id "1", :name "New"}]
      (db/save! :things entity-new)
      (is (= entity-new (db/get-by-id :things "1")))))

  (testing "updates existing entity"
    (let [entity-old {:entity/id "1", :name "Old"}
          entity-new {:entity/id "1", :name "New"}]
      (db/init-collection! :things [entity-old])
      (db/save! :things entity-new)
      (is (= entity-new (db/get-by-id :things "1")))))

  (testing "doesn't affect other entities"
    (let [entity-1 {:entity/id "1", :name "A"}
          entity-2 {:entity/id "2", :name "B"}]
      (db/init-collection! :things [entity-1])
      (db/save! :things entity-2)
      (is (= (set [entity-1 entity-2])
             (set (db/get-all :things))))))

  (testing "doesn't affect other collections"
    (let [thing-1 {:entity/id "1", :name "Thing"}
          stuff-1 {:entity/id "1", :name "Stuff"}]
      (db/init-collection! :things [thing-1])
      (db/save! :stuff stuff-1)
      (is (= [thing-1] (db/get-all :things)))
      (is (= [stuff-1] (db/get-all :stuff)))))

  (testing "error: missing :entity/id"
    (is (thrown? AssertionError
                 (db/save! :things {:name "No ID"})))))
