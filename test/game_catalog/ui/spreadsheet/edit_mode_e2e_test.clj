(ns ^:slow game-catalog.ui.spreadsheet.edit-mode-e2e-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.infra.html :as infra.html]
            [game-catalog.testing.browser :as browser]
            [game-catalog.ui.layout :as layout]
            [game-catalog.ui.spreadsheet :as spreadsheet])
  (:import (com.microsoft.playwright Keyboard Locator$WaitForOptions Page$WaitForFunctionOptions TimeoutError)
           (com.microsoft.playwright.options WaitForSelectorState)))

(def things-config
  {:collection-key :things
   :id-generator spreadsheet/sequential-id-generator
   :sort-by :thing/foo
   :columns [{:column/name "Foo"
              :column/entity-key :thing/foo}
             {:column/name "Bar"
              :column/entity-key :thing/bar}]})

(defn things-page-handler [_request]
  (-> (spreadsheet/table things-config)
      (layout/page)
      (infra.html/response)))

(def test-routes
  [["/"
    {:get {:handler things-page-handler}}]
   (spreadsheet/make-routes things-config)])

(use-fixtures :once (partial browser/fixture test-routes))

(use-fixtures :each (fn [f]
                      (db/init-collection! :things [{:entity/id "1"
                                                     :thing/foo "Baz"
                                                     :thing/bar "Qux"}
                                                    {:entity/id "2"
                                                     :thing/foo "Quux"
                                                     :thing/bar "Corge"}])
                      (browser/navigate! "/")
                      (reset! browser/*request-log [])
                      (f)))

(defn- keyboard ^Keyboard []
  (.keyboard browser/*page*))

(defn- press! [key]
  (.press (keyboard) key))

(defn wait-for-edit-mode []
  (.waitFor (browser/locator "tr.editing")))

(defn wait-for-view-mode []
  (.waitFor (browser/locator "tr.editing")
            (-> (Locator$WaitForOptions.)
                (.setState WaitForSelectorState/HIDDEN))))

(def ^:private focused-js
  "() => {
     const el = document.activeElement;
     const cell = el.closest('td');
     if (!cell) {
       return el.tagName;
     }
     const row = cell.closest('tr');
     return row.dataset.entityId + ':' + Array.from(row.children).indexOf(cell) + ':' + el.tagName;
   }")

(defn focused
  "Describes the focused element as \"entity-id:column-index:tag-name\"."
  []
  (.evaluate browser/*page* focused-js))

(defn wait-for-focus
  "Waits until the focus is on the expected element, and returns the focused element."
  [expected]
  (try
    (.waitForFunction browser/*page*
                      (str "expected => (" focused-js ")() === expected")
                      expected
                      (-> (Page$WaitForFunctionOptions.)
                          (.setTimeout 1000)))
    (catch TimeoutError _))
  (focused))

(defn post-requests []
  (filterv #(= "POST" (:method %)) @browser/*request-log))

(deftest arrow-keys-in-edit-mode-test
  (testing "up/down arrows don't move focus away from the editing row"
    (.dblclick (browser/locator "td:text-is('Baz')"))
    (wait-for-edit-mode)
    (is (= "1:0:INPUT" (wait-for-focus "1:0:INPUT")))
    (reset! browser/*request-log [])

    (press! "ArrowDown")
    (is (= "1:0:INPUT" (focused)))
    (press! "ArrowUp")
    (is (= "1:0:INPUT" (focused)))
    (is (= "1" (.getAttribute (browser/locator "tr.editing") "data-entity-id"))))

  (testing "after exiting edit mode, arrows move between rows"
    (press! "Enter")
    (wait-for-view-mode)
    (is (= "1:0:TD" (wait-for-focus "1:0:TD")))
    (is (= [{:method "POST", :path "/spreadsheet/things/1/view"}]
           (post-requests)))

    (press! "ArrowDown")
    (is (= "2:0:TD" (focused)))))

(deftest cmd-enter-adds-a-row-test
  (testing "focuses the first editable column when nothing is focused"
    (is (= "BODY" (focused)))

    (press! "ControlOrMeta+Enter")
    (is (= "3:0:INPUT" (wait-for-focus "3:0:INPUT")))
    (is (= [{:method "POST", :path "/spreadsheet/things/create"}]
           (post-requests))))

  (testing "keeps the column of the focused cell"
    (.click (browser/locator "td:text-is('Qux')"))
    (wait-for-view-mode)
    (is (= "1:1:TD" (focused)))

    (press! "ControlOrMeta+Enter")
    (is (= "4:1:INPUT" (wait-for-focus "4:1:INPUT"))))

  (testing "saves the row which was being edited"
    (press! "Enter")
    (wait-for-view-mode)
    (.dblclick (browser/locator "td:text-is('Baz')"))
    (wait-for-edit-mode)
    (is (= "1:0:INPUT" (wait-for-focus "1:0:INPUT")))
    (.type (keyboard) "Grault")
    (reset! browser/*request-log [])

    (press! "ControlOrMeta+Enter")
    (is (= "5:0:INPUT" (wait-for-focus "5:0:INPUT")))
    (.waitFor (browser/locator "tr.viewing[id='1']"))
    (is (= [{:method "POST", :path "/spreadsheet/things/create"}
            {:method "POST", :path "/spreadsheet/things/1/save"}]
           (post-requests)))
    (is (= "Grault" (:thing/foo (db/get-by-id :things "1"))))
    (is (= "5:0:INPUT" (focused)) "focus stays in the new row")))

(deftest modified-detection-test
  (testing "a value which was changed back to the original is not saved"
    (.dblclick (browser/locator "td:text-is('Baz')"))
    (wait-for-edit-mode)
    (is (= "1:0:INPUT" (wait-for-focus "1:0:INPUT")))
    (press! "ArrowRight")
    (.type (keyboard) "x")
    (is (= "true" (.getAttribute (browser/locator "tr.editing") "data-modified"))
        "highlights the modified row")
    (press! "Backspace")
    (is (= "false" (.getAttribute (browser/locator "tr.editing") "data-modified"))
        "removes the highlight when the change is reverted")
    (reset! browser/*request-log [])

    (press! "Enter")
    (wait-for-view-mode)
    (is (= [{:method "POST", :path "/spreadsheet/things/1/view"}]
           (post-requests))))

  (testing "inputs which are not part of the form don't modify the row"
    (.dblclick (browser/locator "td:text-is('Baz')"))
    (wait-for-edit-mode)
    (is (= "1:0:INPUT" (wait-for-focus "1:0:INPUT")))
    (.evaluate browser/*page* "() => {
      const input = document.createElement('input');
      document.querySelector('tr.editing td').append(input);
      input.focus();
    }")
    (.type (keyboard) "Grault")
    (reset! browser/*request-log [])

    (press! "Enter")
    (wait-for-view-mode)
    (is (= [{:method "POST", :path "/spreadsheet/things/1/view"}]
           (post-requests))))

  (testing "changes made by scripts are saved, even without input events"
    (.dblclick (browser/locator "td:text-is('Baz')"))
    (wait-for-edit-mode)
    (is (= "1:0:INPUT" (wait-for-focus "1:0:INPUT")))
    (.evaluate browser/*page* "() => document.activeElement.value = 'Grault'")
    (reset! browser/*request-log [])

    (press! "Enter")
    (wait-for-view-mode)
    (is (= [{:method "POST", :path "/spreadsheet/things/1/save"}]
           (post-requests)))
    (is (= "Grault" (:thing/foo (db/get-by-id :things "1"))))))
