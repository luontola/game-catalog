(ns game-catalog.data.csv-fields-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.csv-fields :as csv-fields]))

(deftest parse-string-vector-test
  (testing "parses comma-separated values"
    (is (= ["foo" "bar"]
           (csv-fields/parse-string-vector "foo,bar"))))

  (testing "preserves commas inside quotes"
    (is (= ["foo,bar" "baz"]
           (csv-fields/parse-string-vector "\"foo,bar\",baz"))))

  (testing "trims whitespace around values"
    (is (= ["foo" "bar"]
           (csv-fields/parse-string-vector "foo, bar"))))

  (testing "parses blank values as an empty vector"
    (is (= []
           (csv-fields/parse-string-vector "")))))
