(ns game-catalog.spreadsheet-test
  (:require ["@testing-library/react" :as rtl]
            ["@testing-library/user-event$default" :as user-event]
            ["react-dom$flushSync" :as flushSync]
            [cljs.test :refer [async deftest is testing use-fixtures]]
            [game-catalog.react-tester :as rt]
            [game-catalog.spreadsheet :as spreadsheet]
            [promesa.core :as p]
            [reagent.core :as r]))

(use-fixtures :each rt/fixture)

(defn data-cell [opts]
  [:table [:tbody [:tr [spreadsheet/data-cell opts]]]])

(deftest data-cell-test
  (async done
    (p/do
      (testing "view text"
        (let [*data (r/atom {:things {100 {:stuff "Something"}}})
              ctx (rt/render [data-cell {:*data *data
                                         :data-path [:things 100 :stuff]
                                         :data-type :text}])]
          (is (= "Something"
                 (rt/inner-text ctx)))))

      (testing "view multi-select"
        (let [*data (r/atom {:things {100 {:stuff ["Foo" "Bar"]}}})
              ctx (rt/render [data-cell {:*data *data
                                         :data-path [:things 100 :stuff]
                                         :data-type :multi-select}])]
          (is (= "Foo, Bar"
                 (rt/inner-text ctx)))))

      (testing "view reference"
        (let [*data (r/atom {:things {100 {:stuff [200 300]}}
                             ;; TODO: don't rely on the hard-coded support for rendering :games
                             :games {200 {:name "Foo"}
                                     300 {:name "Bar"}}})
              ctx (rt/render [data-cell {:*data *data
                                         :data-path [:things 100 :stuff]
                                         :data-type :reference
                                         :reference-path [:games]}])]
          (is (= "Foo; Bar"
                 (rt/inner-text ctx)))))

      (testing "start editing: double-click"
        (p/let [user (.setup user-event)                    ; TODO: move to fixture
                *data (r/atom {:things {100 {:stuff "Something"}}})
                ctx (rt/render [data-cell {:*data *data
                                           :data-path [:things 100 :stuff]
                                           :data-type :text}])
                cell (rt/query-selector ctx "td")]
          ;; TODO: wrapper for sending events
          ((.-dblClick user) cell)
          (rtl/act (fn []
                     (r/flush)))

          (let [input (rt/query-selector ctx "input")]
            (is (some? input)
                "displays an input field")
            (is (= "Something" (.-value input))
                "input field contains the value")
            (is (= js/document.activeElement input)
                "input field is focused"))))

      (testing "stop editing: blur")

      (testing "edit text")

      (testing "edit multi-select")

      (testing "edit reference")

      (done))))
