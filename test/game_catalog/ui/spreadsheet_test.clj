(ns ^:slow game-catalog.ui.spreadsheet-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.infra.html :as infra.html]
            [game-catalog.testing.browser :as browser]
            [game-catalog.testing.html :as html]
            [game-catalog.testing.util :refer [with-fixtures]]
            [game-catalog.ui.layout :as layout]
            [game-catalog.ui.routes :as routes]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [reitit.ring :as ring]
            [ring.util.http-response :as http-response])
  (:import (com.microsoft.playwright Locator$WaitForOptions)
           (com.microsoft.playwright.options WaitForSelectorState)))

(def things-config
  {:collection-key :things
   :sort-by (comp clojure.string/lower-case :thing/alfa)
   :columns [{:column/name "#"
              :column/entity-key :entity/id
              :column/read-only? true}
             {:column/name "Alfa"
              :column/entity-key :thing/alfa}
             {:column/name "Bravo"
              :column/entity-key :thing/bravo}
             {:column/name "Charlie"
              :column/entity-key :thing/charlie}]})

(defn things-page-handler [_request]
  (-> (spreadsheet/table things-config)
      (layout/page)
      (infra.html/response)))

(def test-routes
  [["/things"
    {:get {:handler things-page-handler}}]
   (spreadsheet/make-routes things-config)])

(defn browser-fixture [f]
  (with-redefs [routes/ring-handler (ring/ring-handler
                                      (ring/router test-routes)
                                      (constantly (http-response/not-found "Not found")))]
    (browser/fixture f)))

(defn data-fixture [f]
  (db/init-collection! :things
                       [{:entity/id "1"
                         :thing/alfa "Cell 1A"
                         :thing/bravo "Cell 1B"
                         :thing/charlie "Cell 1C"}
                        {:entity/id "2"
                         :thing/alfa "Cell 2A"
                         :thing/bravo "Cell 2B"
                         :thing/charlie "Cell 2C"}
                        {:entity/id "3"
                         :thing/alfa "Cell 3A"
                         :thing/bravo "Cell 3B"
                         :thing/charlie "Cell 3C"}])
  (browser/navigate! "/things")
  (f))

(use-fixtures :once browser-fixture)
(use-fixtures :each data-fixture)

(defn wait-for-edit-mode []
  (.waitFor (browser/locator "tr.editing:not(.adding)")))

(defn wait-for-view-mode []
  (.waitFor (browser/locator "tr.editing:not(.adding)")
            (-> (Locator$WaitForOptions.)
                (.setState WaitForSelectorState/HIDDEN))))


(deftest table-navigation-test
  (let [keyboard (.keyboard browser/*page*)]

    (testing "renders spreadsheet table"
      (is (= (html/normalize-whitespace "
              #  Alfa     Bravo    Charlie
              1  Cell 1A  Cell 1B  Cell 1C
              2  Cell 2A  Cell 2B  Cell 2C
              3  Cell 3A  Cell 3B  Cell 3C
                 []       []       []")
             (html/visualize-html (browser/locator "table")))))

    (testing "clicking a cell gives it focus"
      (.click (browser/locator "text=Cell 1A"))
      (is (= "Cell 1A" (html/visualize-html (browser/focused-element)))))

    (testing "arrow keys move focus between cells:"
      (.click (browser/locator "text=Cell 1A"))
      (is (= "Cell 1A" (html/visualize-html (browser/focused-element))))

      (testing "right"
        (.press keyboard "ArrowRight")
        (is (= "Cell 1B" (html/visualize-html (browser/focused-element)))))

      (testing "down"
        (.press keyboard "ArrowDown")
        (is (= "Cell 2B" (html/visualize-html (browser/focused-element)))))

      (testing "left"
        (.press keyboard "ArrowLeft")
        (is (= "Cell 2A" (html/visualize-html (browser/focused-element)))))

      (testing "up"
        (.press keyboard "ArrowUp")
        (is (= "Cell 1A" (html/visualize-html (browser/focused-element))))))

    (testing "arrow keys cannot move focus beyond the table edges:"
      (testing "top edge"
        (.click (browser/locator "text=Cell 1A"))
        (.press keyboard "ArrowUp")
        (is (= "Cell 1A" (html/visualize-html (browser/focused-element)))))

      (testing "left edge"
        (.click (.first (browser/locator "td:text-is('2')")))
        (.press keyboard "ArrowLeft")
        (is (= "2" (html/visualize-html (browser/focused-element)))))

      (testing "right edge"
        (.click (browser/locator "text=Cell 2C"))
        (.press keyboard "ArrowRight")
        (is (= "Cell 2C" (html/visualize-html (browser/focused-element))))))

    (testing "arrow keys with modifier keys should not navigate:"
      (.click (browser/locator "text=Cell 2B"))
      (is (= "Cell 2B" (html/visualize-html (browser/focused-element))))

      (testing "meta"
        (.press keyboard "Meta+ArrowRight")
        (is (= "Cell 2B" (html/visualize-html (browser/focused-element)))))

      (testing "ctrl"
        (.press keyboard "Control+ArrowRight")
        (is (= "Cell 2B" (html/visualize-html (browser/focused-element)))))

      (testing "alt"
        (.press keyboard "Alt+ArrowRight")
        (is (= "Cell 2B" (html/visualize-html (browser/focused-element)))))

      (testing "shift"
        (.press keyboard "Shift+ArrowRight")
        (is (= "Cell 2B" (html/visualize-html (browser/focused-element))))))

    (testing "arrow keys can navigate to and from the adding row:"
      (testing "down to adding row"
        (.click (browser/locator "text=Cell 3A"))
        (.press keyboard "ArrowDown")
        (is (= "[]" (html/visualize-html (browser/focused-element)))))

      (testing "up from adding row"
        (.press keyboard "ArrowUp")
        (is (= "Cell 3A" (html/visualize-html (browser/focused-element)))))

      (testing "up from adding row's # column"
        (.click (browser/locator "tr.adding td >> nth=0"))
        (.press keyboard "ArrowUp")
        (is (= "3" (html/visualize-html (browser/focused-element)))))

      (testing "bottom edge"
        (.click (browser/locator "tr.adding input >> nth=0"))
        (.press keyboard "ArrowDown")
        (is (= "[]" (html/visualize-html (browser/focused-element))))))))

(deftest edit-mode-test
  (let [keyboard (.keyboard browser/*page*)
        cell-1a (browser/locator "text=Cell 1A")
        saved [{:method "POST", :path "/spreadsheet/things/1/save"}]
        cancelled [{:method "POST", :path "/spreadsheet/things/1/view"}]]

    (with-fixtures [data-fixture]
      (testing "double-click enters edit mode"
        (.dblclick cell-1a)
        (wait-for-edit-mode)

        (is (= (html/normalize-whitespace "
              #  Alfa       Bravo      Charlie
              1  [Cell 1A]  [Cell 1B]  [Cell 1C]
              2  Cell 2A    Cell 2B    Cell 2C
              3  Cell 3A    Cell 3B    Cell 3C
                 []         []         []")
               (html/visualize-html (browser/locator "table"))))
        (is (= "[Cell 1A]" (html/visualize-html (browser/focused-element)))))

      (.type keyboard "Modified") ; change form input as a setup for testing saving

      (testing "double-clicking on a cell already in edit mode does nothing"
        (.dblclick (browser/locator "tr.editing input >> nth=0"))

        (is (= "[Cell 1A]" (html/visualize-html (browser/focused-element))))) ; (form input change is not reflected in data-test-* attributes)

      (testing "clicking outside saves changes and exits edit mode"
        (reset! browser/*request-log [])

        (.click (browser/locator "text=Cell 2A"))
        (wait-for-view-mode)

        (is (= saved @browser/*request-log))
        (is (= (html/normalize-whitespace "
              #  Alfa      Bravo    Charlie
              1  Modified  Cell 1B  Cell 1C
              2  Cell 2A   Cell 2B  Cell 2C
              3  Cell 3A   Cell 3B  Cell 3C
                 []        []       []")
               (html/visualize-html (browser/locator "table"))))
        (is (= "Cell 2A" (html/visualize-html (browser/focused-element)))))

      (testing "focus loss exits edit mode without saving when there are no changes"
        (.dblclick (browser/locator "text=Modified"))
        (wait-for-edit-mode)
        (reset! browser/*request-log [])

        (.press keyboard "ArrowDown") ; also testing arrow navigation from editing row with down arrow
        (wait-for-view-mode)

        (is (= cancelled @browser/*request-log))
        (is (= "Cell 2A" (html/visualize-html (browser/focused-element))))))

    (with-fixtures [data-fixture]
      (testing "Enter key:"
        (testing "enters edit mode"
          (.click cell-1a)

          (.press keyboard "Enter")
          (wait-for-edit-mode)

          (is (= "[Cell 1A]" (html/visualize-html (browser/focused-element)))))

        (testing "saves changes and exits edit mode"
          (.type keyboard "Modified")
          (reset! browser/*request-log [])

          (.press keyboard "Enter")
          (wait-for-view-mode)

          (is (= saved @browser/*request-log))
          (is (= "Modified" (html/visualize-html (browser/focused-element)))))

        (testing "exits edit mode without saving when there are no changes"
          (.press keyboard "Enter")
          (wait-for-edit-mode)
          (reset! browser/*request-log [])

          (.press keyboard "Enter")
          (wait-for-view-mode)

          (is (= cancelled @browser/*request-log))
          (is (= "Modified" (html/visualize-html (browser/focused-element)))))))

    (with-fixtures [data-fixture]
      (testing "F2 key:"
        (testing "enters edit mode"
          (.click cell-1a)

          (.press keyboard "F2")
          (wait-for-edit-mode)

          (is (= "[Cell 1A]" (html/visualize-html (browser/focused-element)))))

        (testing "saves changes and exits edit mode"
          (.type keyboard "Modified")
          (reset! browser/*request-log [])

          (.press keyboard "F2")
          (wait-for-view-mode)

          (is (= saved @browser/*request-log))
          (is (= "Modified" (html/visualize-html (browser/focused-element)))))

        (testing "exits edit mode without saving when there are no changes"
          (.press keyboard "F2")
          (wait-for-edit-mode)
          (reset! browser/*request-log [])

          (.press keyboard "F2")
          (wait-for-view-mode)

          (is (= cancelled @browser/*request-log))
          (is (= "Modified" (html/visualize-html (browser/focused-element)))))))

    (with-fixtures [data-fixture]
      (testing "Escape key discards changes and exits edit mode"
        (.dblclick cell-1a)
        (wait-for-edit-mode)
        (.type keyboard "Discarded")
        (reset! browser/*request-log [])

        (.press keyboard "Escape")
        (wait-for-view-mode)

        (is (= cancelled @browser/*request-log))
        (is (= "Cell 1A" (html/visualize-html (browser/focused-element))))))))

(deftest adding-rows-test
  (let [keyboard (.keyboard browser/*page*)]
    (.click (browser/locator "tr.adding input >> nth=0"))
    (.type keyboard "Added A")
    (.press keyboard "Tab")
    (.type keyboard "Added B")
    (.press keyboard "Tab")
    (.type keyboard "Added C")
    (.press keyboard "Enter")
    (.waitFor (browser/locator "text=Added A"))

    (testing "the added row goes last in the table"
      (is (= (html/normalize-whitespace "
              #  Alfa     Bravo    Charlie
              1  Cell 1A  Cell 1B  Cell 1C
              2  Cell 2A  Cell 2B  Cell 2C
              3  Cell 3A  Cell 3B  Cell 3C
              4  Added A  Added B  Added C
                 []       []       []")
             (html/visualize-html (browser/locator "table")))))

    (testing "the added row is focused"
      (is (= "Added C" (html/visualize-html (browser/focused-element)))))

    (testing "reloading the page will sort the added row to its place"
      (.reload browser/*page*)
      (is (= (html/normalize-whitespace "
              #  Alfa     Bravo    Charlie
              4  Added A  Added B  Added C
              1  Cell 1A  Cell 1B  Cell 1C
              2  Cell 2A  Cell 2B  Cell 2C
              3  Cell 3A  Cell 3B  Cell 3C
                 []       []       []")
             (html/visualize-html (browser/locator "table")))))))
