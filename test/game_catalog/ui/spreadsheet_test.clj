(ns ^:slow game-catalog.ui.spreadsheet-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.infra.html :as infra.html]
            [game-catalog.testing.browser :as browser]
            [game-catalog.testing.html :as html]
            [game-catalog.testing.util :refer [with-fixtures]]
            [game-catalog.ui.layout :as layout]
            [game-catalog.ui.spreadsheet :as spreadsheet])
  (:import (com.microsoft.playwright Locator$WaitForOptions)
           (com.microsoft.playwright.options WaitForSelectorState)))

(def things-config
  {:collection-key :things
   :id-generator spreadsheet/sequential-id-generator
   :sort-by (comp clojure.string/lower-case :thing/alfa)
   :columns [{:column/name "ID"
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
  [["/"
    {:get {:handler things-page-handler}}]
   (spreadsheet/make-routes things-config)])

(def default-entities
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

(defn data-fixture
  ([f] (data-fixture default-entities f))
  ([entities f]
   (db/init-collection! :things entities)
   (browser/navigate! "/")
   (f)))

(use-fixtures :once (partial browser/fixture test-routes))
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
              ID  Alfa     Bravo    Charlie
              1   Cell 1A  Cell 1B  Cell 1C
              2   Cell 2A  Cell 2B  Cell 2C
              3   Cell 3A  Cell 3B  Cell 3C
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

      (testing "cannot move beyond the bottom edge"
        (.click (browser/locator "tr.adding input >> nth=0"))
        (.press keyboard "ArrowDown")
        (is (= "[]" (html/visualize-html (browser/focused-element))))))))

(deftest editor-navigation-test
  (let [keyboard (.keyboard browser/*page*)
        reset-editor! (fn []
                        (.press keyboard "Escape")
                        (wait-for-view-mode))]

    (testing "up arrow key moves focus to cell in row above"
      (.dblclick (browser/locator "text=Cell 2B"))
      (wait-for-edit-mode)

      (.press keyboard "ArrowUp")
      (wait-for-view-mode)

      (is (= "Cell 1B" (html/visualize-html (browser/focused-element)))))

    (testing "down arrow key moves focus to cell in row below"
      (.dblclick (browser/locator "text=Cell 2B"))
      (wait-for-edit-mode)

      (.press keyboard "ArrowDown")
      (wait-for-view-mode)

      (is (= "Cell 3B" (html/visualize-html (browser/focused-element)))))

    (testing "up arrow key cannot move focus beyond top edge"
      (.dblclick (browser/locator "text=Cell 1B"))
      (wait-for-edit-mode)

      (.press keyboard "ArrowUp")

      (is (= "[Cell 1B]" (html/visualize-html (browser/focused-element))))
      (reset-editor!))

    (testing "down arrow key moves focus from bottom row to adding row"
      (.dblclick (browser/locator "text=Cell 3B"))
      (wait-for-edit-mode)

      (.press keyboard "ArrowDown")
      (wait-for-view-mode)

      (is (= "[]" (html/visualize-html (browser/focused-element))))
      (is (= "thing/bravo" (.evaluate browser/*page* "document.activeElement.name"))
          "should focus the same column"))

    (testing "left and right arrow keys do default cursor movement in input fields (unlike in view mode)"
      (.dblclick (browser/locator "text=Cell 2B"))
      (wait-for-edit-mode)

      (.type keyboard "1")
      (.press keyboard "ArrowLeft")
      (.type keyboard "2")
      (.press keyboard "ArrowRight")
      (.type keyboard "3")

      (is (= "213" (.evaluate browser/*page* "document.activeElement.value")))
      (reset-editor!))))

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
              ID  Alfa       Bravo      Charlie
              1   [Cell 1A]  [Cell 1B]  [Cell 1C]
              2   Cell 2A    Cell 2B    Cell 2C
              3   Cell 3A    Cell 3B    Cell 3C
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
              ID  Alfa      Bravo    Charlie
              1   Modified  Cell 1B  Cell 1C
              2   Cell 2A   Cell 2B  Cell 2C
              3   Cell 3A   Cell 3B  Cell 3C
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
        (is (= "Cell 1A" (html/visualize-html (browser/focused-element))))))

    (testing "double-submit guard:"
      (with-fixtures [data-fixture]
        (testing "pressing Enter twice quickly only submits once"
          (.dblclick cell-1a)
          (wait-for-edit-mode)
          (.type keyboard "Modified")
          (reset! browser/*request-log [])

          (.press keyboard "Enter")
          (.press keyboard "Enter")
          (wait-for-view-mode)

          (is (= saved @browser/*request-log))))

      (with-fixtures [data-fixture]
        (testing "pressing Escape twice quickly only cancels once"
          (.dblclick cell-1a)
          (wait-for-edit-mode)
          (.type keyboard "Discarded")
          (reset! browser/*request-log [])

          (.press keyboard "Escape")
          (.press keyboard "Escape")
          (wait-for-view-mode)

          (is (= cancelled @browser/*request-log)))))))

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
              ID  Alfa     Bravo    Charlie
              1   Cell 1A  Cell 1B  Cell 1C
              2   Cell 2A  Cell 2B  Cell 2C
              3   Cell 3A  Cell 3B  Cell 3C
              4   Added A  Added B  Added C
                  []       []       []")
             (html/visualize-html (browser/locator "table")))))

    (testing "the added row is focused"
      (is (= "Added C" (html/visualize-html (browser/focused-element)))))

    (testing "reloading the page will sort the added row to its place"
      (.reload browser/*page*)
      (is (= (html/normalize-whitespace "
              ID  Alfa     Bravo    Charlie
              4   Added A  Added B  Added C
              1   Cell 1A  Cell 1B  Cell 1C
              2   Cell 2A  Cell 2B  Cell 2C
              3   Cell 3A  Cell 3B  Cell 3C
                  []       []       []")
             (html/visualize-html (browser/locator "table")))))))

(deftest adding-row-scroll-into-view-test
  (let [keyboard (.keyboard browser/*page*)
        many-entities (for [i (range 1 25)]
                        {:entity/id (str i)
                         :thing/alfa ""
                         :thing/bravo ""
                         :thing/charlie ""})]
    (with-fixtures [(partial data-fixture many-entities)]

      (testing "focusing the adding row scrolls to the page bottom"
        (let [viewport-height (.evaluate browser/*page* "window.innerHeight")
              scrollable-height (.evaluate browser/*page* "document.documentElement.scrollHeight")
              scroll-before (.evaluate browser/*page* "window.scrollY")]
          (is (< viewport-height scrollable-height (* 2 viewport-height))
              "the test should have more rows than fits on the screen") ; but not unnecessarily many, to have a faster test
          (is (= 0 scroll-before)
              "the test should start scrolled to the top")

          (.click (browser/locator "tr.adding input >> nth=0"))

          (let [scroll-after (.evaluate browser/*page* "window.scrollY")]
            (is (< scroll-before scroll-after))
            (is (= (- scrollable-height viewport-height) scroll-after)))))

      (testing "new row is scrolled into view, so it won't hide behind the adding row"
        (.type keyboard "New")
        (.press keyboard "Enter")
        (.waitFor (browser/locator "text=New"))

        (let [new-row (.boundingBox (browser/locator "text=New"))
              new-row-bottom (+ (.y new-row) (.height new-row))
              adding-row (.boundingBox (browser/locator "tr.adding"))
              adding-row-top (.y adding-row)]
          (is (<= new-row-bottom adding-row-top)
              "new row should not overlap with the adding row"))))))

(defn context-menu-visible? []
  (.isVisible (browser/locator "#context-menu")))

(defn wait-for-row-deleted [cell-text]
  (.waitFor (browser/locator (str "text=" cell-text))
            (-> (Locator$WaitForOptions.)
                (.setState WaitForSelectorState/HIDDEN))))

(deftest context-menu-test
  (let [keyboard (.keyboard browser/*page*)]

    (testing "right-clicking a cell shows the context menu"
      (is (not (context-menu-visible?)))

      (browser/right-click (browser/locator "text=Cell 2A"))

      (is (context-menu-visible?)))

    (testing "clicking outside hides the context menu"
      (is (context-menu-visible?))

      (.click (browser/locator "text=Cell 1A"))

      (is (not (context-menu-visible?))))

    (testing "Escape key hides the context menu"
      (browser/right-click (browser/locator "text=Cell 1A"))
      (is (context-menu-visible?))

      (.press keyboard "Escape")

      (is (not (context-menu-visible?))))

    (testing "context menu does not appear on the adding row"
      (is (not (context-menu-visible?)))

      (browser/right-click (browser/locator "tr.adding td >> nth=1"))

      (is (not (context-menu-visible?))))))

(deftest delete-row-test
  (let [delete-button (browser/locator "#context-menu-delete")]

    (testing "clicking 'Delete row' removes the row from the table"
      (browser/right-click (browser/locator "text=Cell 2A"))
      (reset! browser/*request-log [])

      (.click delete-button)
      (wait-for-row-deleted "Cell 2A")

      (is (= [{:method "POST", :path "/spreadsheet/things/2/delete"}]
             @browser/*request-log))
      (is (= (html/normalize-whitespace "
              ID  Alfa     Bravo    Charlie
              1   Cell 1A  Cell 1B  Cell 1C
              3   Cell 3A  Cell 3B  Cell 3C
                  []       []       []")
             (html/visualize-html (browser/locator "table")))))

    (testing "context menu is hidden after clicking delete"
      (browser/right-click (browser/locator "text=Cell 1A"))
      (is (context-menu-visible?))

      (.click delete-button)
      (wait-for-row-deleted "Cell 1A")

      (is (not (context-menu-visible?))))))
