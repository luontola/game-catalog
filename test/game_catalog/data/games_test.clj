(ns game-catalog.data.games-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [game-catalog.data.games :as games])
  (:import (java.io StringReader)))

(deftest read-games-from-csv-test
  (testing "parses tags as a vector of strings"
    (let [csv (str "id,Name,Release,Remake,Series,Tags,Purchases,Status,Content,DLCs\n"
                   "1,Example,,,,\"\"\"foo,bar\"\",baz\",,,,\n")]
      (with-redefs [io/reader (fn [_] (StringReader. csv))]
        (is (= ["foo,bar" "baz"]
               (:game/tags (first (games/read-games-from-csv)))))))))

(deftest config-test
  (testing "tags options include all known CSV values"
    (is (= (->> (games/read-games-from-csv)
                (mapcat :game/tags)
                distinct
                sort)
           (sort games/known-tags)))))
