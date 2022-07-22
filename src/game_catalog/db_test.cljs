(ns game-catalog.db-test
  (:require [cljs.test :refer [deftest is testing]]
            [game-catalog.db :as db]))

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
