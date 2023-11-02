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
    (p/let [original-new-id spreadsheet/new-id]
      (p/do
        (set! spreadsheet/new-id (let [*counter (atom 1000)]
                                   #(swap! *counter inc)))

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
          (let [*data (r/atom {:things {:documents {100 {:stuff "Something"}}}})
                ctx (rt/render [data-cell *data {:data-type :text
                                                 :self-collection :things
                                                 :self-id 100
                                                 :self-field :stuff}])]
            (p/do
              (rt/simulate! :dblClick (rt/query-selector ctx "td"))
              (rt/wait-for! #(some? (rt/query-selector ctx "input")))

              (let [input (rt/query-selector ctx "input")]
                (is (some? input)
                    "displays an input field")
                (is (= "Something" (.-value input))
                    "input field contains the value")
                (is (= js/document.activeElement input)
                    "input field is focused")))))

        (testing "stop editing: press tab"
          (let [*data (r/atom {:things {:documents {100 {:stuff "Something"}}}})
                ctx (rt/render [data-cell *data {:data-type :text
                                                 :self-collection :things
                                                 :self-id 100
                                                 :self-field :stuff}])]
            (p/do
              (enter-edit-mode! ctx)

              ;; XXX: typing is flaky - sometimes the first character isn't typed into the just focused input field
              (rt/sleep! 20)
              (rt/simulate! :keyboard "123{Tab}")

              (is (nil? (rt/query-selector ctx "input"))
                  "removes the input field")
              (is (= "Something123" (rt/inner-text ctx))
                  "displays the updated value")
              (is (= {:things {:documents {100 {:stuff "Something123"}}}}
                     @*data)
                  "updates the database"))))

        (testing "edit text"
          (let [*data (r/atom {:things {:documents {100 {:stuff "Old Text"}}}})
                ctx (rt/render [data-cell *data {:data-type :text
                                                 :self-collection :things
                                                 :self-id 100
                                                 :self-field :stuff}])]
            (p/do
              (enter-edit-mode! ctx)

              (let [input (rt/query-selector ctx "input")]
                (is (= "Old Text" (.-value input)))
                (rt/fire-event! :change input {:target {:value "New Text"}})
                (is (= "New Text" (.-value input))))

              (exit-edit-mode! ctx)
              (is (= {:things {:documents {100 {:stuff "New Text"}}}}
                     @*data)))))

        (testing "edit multi-select"
          (let [*data (r/atom {:things {:documents {100 {:stuff ["Thing 1"
                                                                 "Thing 2"]}}}})
                ctx (rt/render [data-cell *data {:data-type :multi-select
                                                 :self-collection :things
                                                 :self-id 100
                                                 :self-field :stuff}])]
            (p/do
              (enter-edit-mode! ctx)

              (let [input (rt/query-selector ctx "input")]
                (is (= "Thing 1; Thing 2" (.-value input)))
                (rt/fire-event! :change input {:target {:value "Thing 2; Thing 3"}})
                (is (= "Thing 2; Thing 3" (.-value input))))

              (exit-edit-mode! ctx)
              (is (= {:things {:documents {100 {:stuff ["Thing 2"
                                                        "Thing 3"]}}}}
                     @*data)))))

        (testing "edit reference"
          (let [*data (r/atom {:things {:documents {100 {:stuff [200 300]}}}
                               :stuffs {:documents {200 {:name "Keep"
                                                         :thingies [100]}
                                                    300 {:name "Remove"
                                                         :thingies [100]}
                                                    400 {:name "Add Existing"}}}})
                ctx (rt/render [data-cell *data {:data-type :reference
                                                 :self-collection :things
                                                 :self-id 100
                                                 :self-field :stuff
                                                 :reference-collection :stuffs
                                                 :reference-foreign-key :thingies}])]
            (p/do
              (enter-edit-mode! ctx)

              (let [input (rt/query-selector ctx "td")]
                (p/do
                  (is (re-find #"\nKeep\nRemove$" (.-innerText input)))
                  ;; changes to references: keep 1, remove 1, add 1, create 1
                  (rt/simulate! :keyboard "{Backspace}exist{Enter}Create New{Enter}")
                  (is (re-find #"\nKeep\nAdd Existing\nCreate New$" (.-innerText input)))))

              (exit-edit-mode! ctx)
              (is (= {:things {:documents {100 {:stuff [200 400 1001]}}}
                      :stuffs {:documents {200 {:name "Keep"
                                                :thingies [100]}
                                           300 {:name "Remove"}
                                           400 {:name "Add Existing"
                                                :thingies [100]}
                                           1001 {:name "Create New"
                                                 :thingies [100]}}}}
                     @*data)))))

        (set! spreadsheet/new-id original-new-id)
        (done)))))
