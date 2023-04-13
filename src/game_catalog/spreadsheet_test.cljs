(ns game-catalog.spreadsheet-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [game-catalog.react-tester :as rt]
            [game-catalog.spreadsheet :as spreadsheet]
            [reagent.core :as r]))

(use-fixtures :each rt/fixture)

(defn data-cell [opts]
  [:table [:tbody [:tr [spreadsheet/data-cell opts]]]])

(deftest data-cell-test
  (testing "view text"
    (let [*data (r/atom {:things {100 {:stuff "Something"}}})
          hiccup [data-cell {:*data *data
                             :data-path [:things 100 :stuff]
                             :data-type :text}]]
      (is (= "Something"
             (-> (rt/render hiccup)
                 (rt/inner-text))))))

  (testing "view multi-select"
    (let [*data (r/atom {:things {100 {:stuff ["Foo" "Bar"]}}})
          hiccup [data-cell {:*data *data
                             :data-path [:things 100 :stuff]
                             :data-type :multi-select}]]
      (is (= "Foo, Bar"
             (-> (rt/render hiccup)
                 (rt/inner-text))))))

  (testing "view reference"
    (let [*data (r/atom {:things {100 {:stuff [200 300]}}
                         ;; TODO: don't rely on the hard-coded support for rendering :games
                         :games {200 {:name "Foo"}
                                 300 {:name "Bar"}}})
          hiccup [data-cell {:*data *data
                             :data-path [:things 100 :stuff]
                             :data-type :reference
                             :reference-path [:games]}]]
      (is (= "Foo; Bar"
             (-> (rt/render hiccup)
                 (rt/inner-text))))))

  (testing "start editing: double-click")

  (testing "stop editing: blur")

  (testing "edit text")

  (testing "edit multi-select")

  (testing "edit reference"))
