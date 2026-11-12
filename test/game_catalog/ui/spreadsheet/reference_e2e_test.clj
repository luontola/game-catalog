(ns ^:slow game-catalog.ui.spreadsheet.reference-e2e-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.infra.html :as infra.html]
            [game-catalog.testing.browser :as browser]
            [game-catalog.ui.layout :as layout]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [game-catalog.ui.spreadsheet.reference :as reference])
  (:import (com.microsoft.playwright Dialog Keyboard Locator$WaitForOptions Page$WaitForFunctionOptions TimeoutError)
           (com.microsoft.playwright.options WaitForSelectorState)))

(def things-config
  (let [config {:collection-key :things
                :id-generator spreadsheet/sequential-id-generator
                :sort-by :thing/name
                :reference-label :thing/name
                :columns [{:column/name "Name"
                           :column/entity-key :thing/name}
                          (assoc reference/column-defaults
                            :column/name "Refs"
                            :column/entity-key :thing/refs
                            :column/target-collection :things)]}]
    (assoc config :target-configs {:things config})))

(defn things-page-handler [_request]
  (-> (spreadsheet/table things-config)
      (layout/page)
      (infra.html/response)))

(def test-routes
  [["/things"
    {:get {:handler things-page-handler}}]
   (spreadsheet/make-routes things-config)])

(use-fixtures :once (partial browser/fixture test-routes))

(use-fixtures :each (fn [f]
                      (db/init-collection! :things [{:entity/id "1"
                                                     :thing/name "Alfa"
                                                     :thing/refs ["3" "2"]}
                                                    {:entity/id "2"
                                                     :thing/name "Bravo"}
                                                    {:entity/id "3"
                                                     :thing/name "Charlie"}])
                      (browser/navigate! "/things")
                      (f)))

(defn- press! [key]
  (.press ^Keyboard (.keyboard browser/*page*) key))

(def ^:private focused-js
  "() => {
     const el = document.activeElement;
     const row = el.closest('tr');
     if (!row) {
       return el.tagName;
     }
     return row.dataset.entityId + ':' + el.tagName + ':' + el.textContent;
   }")

(defn- focused []
  (.evaluate browser/*page* focused-js))

(defn- wait-for-focus
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

(defn- url []
  (.evaluate browser/*page* "() => location.pathname + location.hash"))

(defn- wait-for-url
  "Waits until the URL path and hash are the expected, and returns them."
  [expected]
  (try
    (.waitForFunction browser/*page*
                      "expected => location.pathname + location.hash === expected"
                      expected
                      (-> (Page$WaitForFunctionOptions.)
                          (.setTimeout 1000)))
    (catch TimeoutError _))
  (url))

(deftest keyboard-navigation-to-links-test
  (.click (browser/locator "td:text-is('Alfa')"))
  (press! "ArrowRight")
  (is (= "1:TD:Bravo, Charlie" (focused)))

  (testing "Tab moves focus to the links inside the cell"
    (press! "Tab")
    (is (= "1:A:Bravo" (focused)))
    (press! "Tab")
    (is (= "1:A:Charlie" (focused))))

  (testing "arrow keys still move between cells when a link has focus"
    (press! "ArrowDown")
    (is (= "2:TD:" (focused)))
    (press! "ArrowUp")
    (is (= "1:TD:Bravo, Charlie" (focused))))

  (testing "Enter on a focused link follows the link"
    (press! "Tab")
    (press! "Tab")
    (is (= "1:A:Charlie" (focused)))
    (press! "Enter")
    (.waitForURL browser/*page* "**/things#3")
    (is (= "3:TD:Charlie" (wait-for-focus "3:TD:Charlie")))))

(deftest keyboard-edit-mode-test
  (.click (browser/locator "td:text-is('Alfa')"))
  (press! "ArrowRight")

  (testing "Enter on the cell itself still enters edit mode"
    (press! "Enter")
    (.waitFor (browser/locator "tr.editing:not(.adding)"))
    (is (= "/things#1" (wait-for-url "/things#1")))))

(deftest focus-in-history-test
  (testing "a link to a row on the same page focuses the row's first cell"
    (browser/navigate! "/things#2")
    (is (= "2:TD:Bravo" (wait-for-focus "2:TD:Bravo"))))

  (testing "a link to a row on another page focuses the row's first cell"
    (.navigate browser/*page* "about:blank")
    (browser/navigate! "/things#3")
    (is (= "3:TD:Charlie" (wait-for-focus "3:TD:Charlie"))))

  (testing "moving the focus changes the URL hash and history state, without new history entries"
    (let [history-length (.evaluate browser/*page* "() => history.length")]
      (.click (browser/locator "td:text-is('Alfa')"))
      (is (= "/things#1" (wait-for-url "/things#1")))
      (press! "ArrowDown")
      (press! "ArrowRight")
      (is (= "/things#2" (wait-for-url "/things#2")))
      (.waitForFunction browser/*page* "() => history.state?.spreadsheetCellIndex === 1")
      (is (= history-length (.evaluate browser/*page* "() => history.length")))))

  (testing "reloading the page focuses the same cell"
    (.reload browser/*page*)
    (is (= "2:TD:" (wait-for-focus "2:TD:"))))

  (testing "going back after following a link focuses the cell which had the link"
    (.click (browser/locator "td:text-is('Alfa')"))
    (press! "ArrowRight")
    (press! "Tab")
    (press! "Tab")
    (is (= "1:A:Charlie" (focused)))

    (press! "Enter")
    (is (= "3:TD:Charlie" (wait-for-focus "3:TD:Charlie")))
    (.goBack browser/*page*)

    (is (= "/things#1" (wait-for-url "/things#1")))
    (is (= "1:TD:Bravo, Charlie" (wait-for-focus "1:TD:Bravo, Charlie"))))

  (testing "the history is not modified on every focus change, because browsers rate limit it"
    (.evaluate browser/*page* "() => {
      window.replaceStateCount = 0;
      const replaceState = history.replaceState.bind(history);
      history.replaceState = (...args) => {
        window.replaceStateCount++;
        return replaceState(...args);
      };
    }")
    (.click (browser/locator "td:text-is('Alfa')"))
    (press! "ArrowDown")
    (dotimes [_ 10]
      (press! "ArrowUp")
      (press! "ArrowDown"))
    (is (= "/things#2" (wait-for-url "/things#2")))
    (is (<= (.evaluate browser/*page* "() => window.replaceStateCount")
            2)))

  (testing "the adding row has no hash"
    (.press ^Keyboard (.keyboard browser/*page*) "ControlOrMeta+Enter")
    (.waitForFunction browser/*page* "() => document.activeElement.closest('tr.adding') !== null")
    (is (= "/things" (wait-for-url "/things"))))

  (testing "a link to an unknown row focuses nothing"
    (browser/navigate! "/things#nope")
    (.reload browser/*page*)
    (is (= "BODY" (focused)))))

(deftest blocked-delete-test
  (let [*dialog-messages (atom [])]
    (.onceDialog browser/*page* (fn [^Dialog dialog]
                                  (swap! *dialog-messages conj (.message dialog))
                                  (.accept dialog)))

    (testing "deleting a row which other rows refer to shows an error, and the row stays"
      (browser/right-click (browser/locator "td:text-is('Bravo')"))
      (.click (browser/locator "#context-menu-delete"))
      (.waitForCondition browser/*page* #(boolean (seq @*dialog-messages)))
      (is (= ["Cannot delete this row, because other rows refer to it:\n- things: Alfa"]
             @*dialog-messages))
      (is (= 1 (.count (browser/locator "tr[id='2']")))))

    (reset! *dialog-messages [])
    (testing "deleting a row which no other rows refer to works"
      (browser/right-click (browser/locator "td:text-is('Alfa')"))
      (.click (browser/locator "#context-menu-delete"))
      (.waitFor (browser/locator "tr[id='1']")
                (-> (Locator$WaitForOptions.)
                    (.setState WaitForSelectorState/DETACHED)))
      (is (= 0 (count @*dialog-messages))))))
