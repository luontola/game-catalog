(ns game-catalog.spreadsheet-test
  (:require [cljs.test :refer [async deftest is testing use-fixtures]]
            [game-catalog.react-tester :as rt]
            [game-catalog.spreadsheet :as spreadsheet]
            [promesa.core :as p]
            [reagent.core :as r]))

(use-fixtures :each rt/fixture)

(defn data-cell [*data context]
  [:table [:tbody [:tr [spreadsheet/data-cell *data context]]]])

(defn- enter-edit-mode! [ctx]
  (p/do
    (rt/simulate! :dblClick (rt/query-selector ctx "td"))
    (let [input (rt/query-selector ctx "input")]
      (is (some? input)
          "assume field is editable")
      (is (= js/document.activeElement input)
          "assume field is focused"))))

(defn- exit-edit-mode! [ctx]
  (p/do
    (rt/simulate! :keyboard "{Tab}")
    (is (nil? (rt/query-selector ctx "input"))
        "assume input field is removed")))

(deftest data-cell-test
  (async done
    (p/do
      (testing "view text"
        (let [*data (r/atom {:things {:documents {100 {:stuff "Something"}}}})
              ctx (rt/render [data-cell *data {:data-type :text
                                               :self-collection :things
                                               :self-id 100
                                               :self-field :stuff}])]
          (is (= "Something"
                 (rt/inner-text ctx)))))

      (testing "view multi-select"
        (let [*data (r/atom {:things {:documents {100 {:stuff ["Foo" "Bar"]}}}})
              ctx (rt/render [data-cell *data {:data-type :multi-select
                                               :self-collection :things
                                               :self-id 100
                                               :self-field :stuff}])]
          (is (= "Foo, Bar"
                 (rt/inner-text ctx)))))

      (testing "view reference"
        (let [*data (r/atom {:things {:documents {100 {:stuff [200 300]}}}
                             :stuffs {:documents {200 {:name "Foo"
                                                       :thingies [100]}
                                                  300 {:name "Bar"
                                                       :thingies [100]}}}})
              ctx (rt/render [data-cell *data {:data-type :reference
                                               :self-collection :things
                                               :self-id 100
                                               :self-field :stuff
                                               :reference-collection :stuffs
                                               :reference-foreign-key :thingies}])]
          (is (= "Foo; Bar"
                 (rt/inner-text ctx)))))

      (testing "start editing: double-click"
        (p/let [*data (r/atom {:things {:documents {100 {:stuff "Something"}}}})
                ctx (rt/render [data-cell *data {:data-type :text
                                                 :self-collection :things
                                                 :self-id 100
                                                 :self-field :stuff}])]

          (rt/simulate! :dblClick (rt/query-selector ctx "td"))

          (let [input (rt/query-selector ctx "input")]
            (is (some? input)
                "displays an input field")
            (is (= "Something" (.-value input))
                "input field contains the value")
            (is (= js/document.activeElement input)
                "input field is focused"))))

      (testing "stop editing: press tab"
        (p/let [*data (r/atom {:things {:documents {100 {:stuff "Something"}}}})
                ctx (rt/render [data-cell *data {:data-type :text
                                                 :self-collection :things
                                                 :self-id 100
                                                 :self-field :stuff}])]
          (enter-edit-mode! ctx)

          (rt/simulate! :keyboard "123{Tab}")

          (is (nil? (rt/query-selector ctx "input"))
              "removes the input field")
          (is (= "Something123" (rt/inner-text ctx))
              "displays the updated value")
          (is (= {:things {:documents {100 {:stuff "Something123"}}}}
                 @*data)
              "updates the database")))

      (testing "edit text"
        (p/let [*data (r/atom {:things {:documents {100 {:stuff "Old Text"}}}})
                ctx (rt/render [data-cell *data {:data-type :text
                                                 :self-collection :things
                                                 :self-id 100
                                                 :self-field :stuff}])]
          (enter-edit-mode! ctx)

          (let [input (rt/query-selector ctx "input")]
            (is (= "Old Text" (.-value input)))
            (rt/fire-event! :change input {:target {:value "New Text"}})
            (is (= "New Text" (.-value input))))

          (exit-edit-mode! ctx)
          (is (= {:things {:documents {100 {:stuff "New Text"}}}}
                 @*data))))

      (testing "edit multi-select"
        (p/let [*data (r/atom {:things {:documents {100 {:stuff ["Thing 1"
                                                                 "Thing 2"]}}}})
                ctx (rt/render [data-cell *data {:data-type :multi-select
                                                 :self-collection :things
                                                 :self-id 100
                                                 :self-field :stuff}])]
          (enter-edit-mode! ctx)

          (let [input (rt/query-selector ctx "input")]
            (is (= "Thing 1; Thing 2" (.-value input)))
            (rt/fire-event! :change input {:target {:value "Thing 2; Thing 3"}})
            (is (= "Thing 2; Thing 3" (.-value input))))

          (exit-edit-mode! ctx)
          (is (= {:things {:documents {100 {:stuff ["Thing 2"
                                                    "Thing 3"]}}}}
                 @*data))))

      (testing "edit reference"
        (p/let [*data (r/atom {:things {:documents {100 {:stuff [200 300]}}}
                               :stuffs {:documents {200 {:name "Foo"
                                                         :thingies [100]}
                                                    300 {:name "Bar"
                                                         :thingies [100]}
                                                    400 {:name "Gazonk"}}}})
                ctx (rt/render [data-cell *data {:data-type :reference
                                                 :self-collection :things
                                                 :self-id 100
                                                 :self-field :stuff
                                                 :reference-collection :stuffs
                                                 :reference-foreign-key :thingies}])]
          (enter-edit-mode! ctx)

          (p/let [input (rt/query-selector ctx "td")]
            (is (re-find #"\nFoo\nBar$" (.-innerText input)))
            ;; changes to references: keep 1, remove 1, add 1
            (rt/simulate! :keyboard "{Backspace}gaz{Enter}")
            (is (re-find #"\nFoo\nGazonk$" (.-innerText input))))

          (exit-edit-mode! ctx)
          (is (= {:things {:documents {100 {:stuff [200 400]}}}
                  :stuffs {:documents {200 {:name "Foo"
                                            :thingies [100]}
                                       300 {:name "Bar"}
                                       400 {:name "Gazonk"
                                            :thingies [100]}}}}
                 @*data))))

      (done))))
