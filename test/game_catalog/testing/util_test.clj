(ns game-catalog.testing.util-test
  (:require [clojure.test :refer :all]
            [game-catalog.testing.util :refer [with-fixtures]]))

(deftest with-fixtures-test
  (testing "calls fixtures and body in the right order"
    (let [calls (atom [])
          spy! #(swap! calls conj %)
          fixture-1 (fn [f]
                      (spy! :fixture-1-before)
                      (f)
                      (spy! :fixture-1-after))
          fixture-2 (fn [f]
                      (spy! :fixture-2-before)
                      (f)
                      (spy! :fixture-2-after))]
      (with-fixtures [fixture-1 fixture-2]
        (spy! :body-1)
        (spy! :body-2))

      (is (= [:fixture-1-before
              :fixture-2-before
              :body-1
              :body-2
              :fixture-2-after
              :fixture-1-after]
             @calls)))))
