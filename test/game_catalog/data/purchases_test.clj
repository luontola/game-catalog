(ns game-catalog.data.purchases-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [game-catalog.data.purchases :as purchases])
  (:import (java.io StringReader)))

(deftest read-purchases-from-csv-test
  (testing "parses shops as a vector of strings"
    (let [csv (str "id,Shop,Date,Cost,Base Games,DLCs,Bundle Name\n"
                   "1,\"\"\"foo,bar\"\",baz\",2024-01-01,0,,,\n")]
      (with-redefs [io/reader (fn [_] (StringReader. csv))]
        (is (= ["foo,bar" "baz"]
               (:purchase/shop (first (purchases/read-purchases-from-csv)))))))))

(deftest config-test
  (testing "shop options include all known CSV values"
    (is (= (->> (purchases/read-purchases-from-csv)
                (mapcat :purchase/shop)
                distinct
                sort)
           (sort purchases/known-shops)))))
