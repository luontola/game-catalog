(ns ^:slow game-catalog.ui.spreadsheet.multiselect-e2e-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.infra.html :as infra.html]
            [game-catalog.testing.browser :as browser]
            [game-catalog.testing.html :as html]
            [game-catalog.ui.layout :as layout]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [game-catalog.ui.spreadsheet.multiselect :as multiselect])
  (:import (com.microsoft.playwright Locator$WaitForOptions)
           (com.microsoft.playwright.options WaitForSelectorState)))

;; TODO: consider rewriting these tests

(def things-config
  {:collection-key :things
   :id-generator spreadsheet/sequential-id-generator
   :sort-by :thing/name
   :columns [{:column/name "Name"
              :column/entity-key :thing/name}
             (assoc multiselect/column-defaults
               :column/name "Multiy"
               :column/entity-key :thing/multiy
               :column/options ["Foo" "Bar" "Gazonk"])]})

(defn things-page-handler [_request]
  (-> (spreadsheet/table things-config)
      (layout/page)
      (infra.html/response)))

(def test-routes
  [["/"
    {:get {:handler things-page-handler}}]
   (spreadsheet/make-routes things-config)])

(use-fixtures :once (partial browser/fixture test-routes))

(defn wait-for-edit-mode []
  (.waitFor (browser/locator "tr.editing:not(.adding)")))

(defn wait-for-view-mode []
  (.waitFor (browser/locator "tr.editing:not(.adding)")
            (-> (Locator$WaitForOptions.)
                (.setState WaitForSelectorState/HIDDEN))))

(deftest multiselect-edit-mode-test
  (let [keyboard (.keyboard browser/*page*)]
    (db/init-collection! :things [{:entity/id "1"
                                   :thing/name "Example"
                                   :thing/multiy ["Foo"]}])
    (browser/navigate! "/")

    (testing "renders selected values"
      (is (= (html/normalize-whitespace "
              Name     Multiy
              Example  Foo
                       []       []")
             (html/visualize-html (browser/locator "table")))))

    (testing "saves multiple selected values"
      (.dblclick (browser/locator "td.column-multiselect:text-is('Foo')"))
      (wait-for-edit-mode)

      (let [select (browser/locator "tr.editing:not(.adding) select[name='thing/multiy']")]
        (is (some? (.getAttribute select "multiple")))
        (is (= "1" (.getAttribute select "size"))))

      (.evaluate browser/*page* "
        const select = document.querySelector(\"tr.editing:not(.adding) select[name='thing/multiy']\");
        for (const option of select.options) {
          option.selected = ['Foo', 'Bar'].includes(option.value);
        }
        select.dispatchEvent(new Event('change', {bubbles: true}));
      ")
      (reset! browser/*request-log [])

      (.press keyboard "Enter")
      (wait-for-view-mode)

      (is (= [{:method "POST", :path "/spreadsheet/things/1/save"}]
             @browser/*request-log))
      (is (= ["Foo" "Bar"]
             (:thing/multiy (db/get-by-id :things "1"))))
      (is (= "Foo, Bar"
             (html/visualize-html (browser/locator "td.column-multiselect:text-is('Foo, Bar')")))))))
