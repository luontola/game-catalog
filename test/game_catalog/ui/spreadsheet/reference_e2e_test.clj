(ns ^:slow game-catalog.ui.spreadsheet.reference-e2e-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.infra.html :as infra.html]
            [game-catalog.testing.browser :as browser]
            [game-catalog.testing.html :as html]
            [game-catalog.ui.layout :as layout]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [game-catalog.ui.spreadsheet.reference :as reference])
  (:import (com.microsoft.playwright Keyboard Locator Locator$WaitForOptions Page$WaitForConditionOptions Page$WaitForFunctionOptions Route TimeoutError)
           (com.microsoft.playwright.options WaitForSelectorState)
           (java.util.function Consumer)))

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
                            :column/target-collection :things)
                          (assoc reference/column-defaults
                            :column/name "Ref"
                            :column/entity-key :thing/ref
                            :column/target-collection :things
                            :column/multiple? false)]}]
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
                                                     :thing/refs ["3" "2"]
                                                     :thing/ref "2"}
                                                    {:entity/id "2"
                                                     :thing/name "Bravo"}
                                                    {:entity/id "3"
                                                     :thing/name "Charlie"}
                                                    {:entity/id "4"
                                                     :thing/name "Delta"}])
                      (browser/navigate! "/things")
                      (try
                        (f)
                        (finally
                          (.unrouteAll browser/*page*)))))

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
    (.waitFor (browser/locator "tr.editing"))
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
    (is (= "1:TD:Bravo, Charlie" (wait-for-focus "1:TD:Bravo, Charlie")))
    ;; htmx history restore used to replace the page body a few milliseconds after the hashchange event
    (Thread/sleep 20)
    (is (= "1:TD:Bravo, Charlie" (focused))
        "ensure focus was not lost soon after setting it"))

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

  (testing "a new row has a hash"
    (.press ^Keyboard (.keyboard browser/*page*) "ControlOrMeta+Enter")
    (.waitFor (browser/locator "tr.editing"))
    (is (= "/things#5" (wait-for-url "/things#5"))))

  (testing "a link to an unknown row focuses nothing"
    (browser/navigate! "/things#nope")
    (.reload browser/*page*)
    (is (= "BODY" (focused)))))

(deftest blocked-delete-test
  (testing "deleting a row which other rows refer to shows an error, and the row stays"
    (browser/right-click (browser/locator "td:text-is('Bravo')"))
    (.click (browser/locator "#context-menu-delete"))
    (.waitFor (browser/locator ".status-bar"))
    (is (= "Cannot delete this row, because other rows refer to it:\n- things: Alfa"
           (.textContent (browser/locator ".status-bar-message"))))
    (is (= 1 (.count (browser/locator "tr[id='2']")))))

  (testing "the error message can be closed"
    (.click (browser/locator ".status-bar-close"))
    (is (.isHidden (browser/locator ".status-bar"))))

  (testing "deleting a row which no other rows refer to works"
    (browser/right-click (browser/locator "td:text-is('Alfa')"))
    (.click (browser/locator "#context-menu-delete"))
    (.waitFor (browser/locator "tr[id='1']")
              (-> (Locator$WaitForOptions.)
                  (.setState WaitForSelectorState/DETACHED)))
    (is (.isHidden (browser/locator ".status-bar")))))

(defn- wait-for-edit-mode []
  (.waitFor (browser/locator "tr.editing")))

(defn- wait-for-view-mode []
  (.waitFor (browser/locator "tr.editing")
            (-> (Locator$WaitForOptions.)
                (.setState WaitForSelectorState/HIDDEN))))

(defn- post-requests []
  (filterv #(= "POST" (:method %)) @browser/*request-log))

(defn- wait-for-saved
  "Waits until the entity has the expected values, and returns its values."
  [expected]
  (let [saved #(select-keys (db/get-by-id :things "1") (keys expected))]
    (try
      (.waitForCondition browser/*page*
                         #(= expected (saved))
                         (-> (Page$WaitForConditionOptions.)
                             (.setTimeout 1000)))
      (catch TimeoutError _))
    (saved)))

(def ^:private opened-picker {:method "POST", :path "/spreadsheet/things/1/reference-picker"})
(def ^:private saved-reference {:method "POST", :path "/spreadsheet/things/1/save-reference"})

(defn- visible-icons [^Locator cell]
  (mapv #(.getAttribute ^Locator % "aria-label")
        (.all (.locator cell ".pill-button [role=img]:visible"))))

(defn- has-focus-soon?
  "The server renders the reference editor's buttons again after every change
   to the references, and the focus moves to the new button. This waits for it,
   because a check at the time of the change would see the old or the new button."
  [^Locator element]
  (try
    (.waitForCondition browser/*page*
                       #(browser/has-focus? element)
                       (-> (Page$WaitForConditionOptions.)
                           (.setTimeout 1000)))
    true
    (catch TimeoutError _
      false)))

(deftest pills-test
  (.click (browser/locator "td:text-is('Alfa')"))
  (press! "ArrowRight")
  (press! "Enter")
  (wait-for-edit-mode)
  (reset! browser/*request-log [])
  (let [row (browser/locator "tr.editing")
        refs-cell (.locator row ":scope > td.column-reference >> nth=0")
        ref-cell (.locator row ":scope > td.column-reference >> nth=1")]

    (testing "the referred entities are pills, and the add button has the focus"
      (is (= ["Bravo×" "Charlie×"] (vec (.allTextContents (.locator refs-cell ".reference-editor > .pill")))))
      (is (has-focus-soon? (.locator refs-cell ".pill-button")) (browser/focused-element)))

    (testing "the button of a single-value column shows the replace icon when it has a value"
      (is (= ["Replace"] (visible-icons ref-cell))))

    (testing "Enter on a remove button removes the pill, saves the row, and does not exit edit mode"
      (press! "Shift+Tab")
      (is (browser/has-focus? (.locator refs-cell ".pill-remove[aria-label='Remove Charlie']")) (browser/focused-element))
      (press! "Enter")
      (is (= 1 (.count (.locator refs-cell ".reference-editor > .pill"))))
      (is (has-focus-soon? (.locator refs-cell ".pill-button")) (browser/focused-element))
      (is (= {:thing/refs ["2"]} (wait-for-saved {:thing/refs ["2"]})))
      (is (= [saved-reference] (post-requests)))
      (is (has-focus-soon? (.locator refs-cell ".pill-button")) (browser/focused-element)))

    (testing "clicking a remove button removes the pill, saves the row, and does not exit edit mode"
      (.click (.locator ref-cell ".pill-remove"))
      (is (= 0 (.count (.locator ref-cell ".reference-editor > .pill"))))
      (is (has-focus-soon? (.locator ref-cell ".pill-button")) (browser/focused-element))
      (is (= {:thing/ref nil} (wait-for-saved {:thing/ref nil})))
      (is (= [saved-reference saved-reference] (post-requests))))

    (testing "the server renders the button of a single-value column with the add icon when it has no value"
      (.waitFor (.locator ref-cell ".pill-button [aria-label='Add']"))
      (is (= ["Add"] (visible-icons ref-cell)))
      (is (has-focus-soon? (.locator ref-cell ".pill-button")) (browser/focused-element))
      (is (= 1 (.count row))))

    (testing "exiting edit mode keeps the removals"
      (press! "F2")
      (wait-for-view-mode)
      (is (= {:thing/refs ["2"], :thing/ref nil}
             (select-keys (db/get-by-id :things "1") [:thing/refs :thing/ref]))))))

(defn- picker-results []
  (html/visualize-html (browser/locator ".reference-picker[open] .reference-results")))

(defn- wait-for-picker-results [expected]
  (try
    (.waitForFunction browser/*page*
                      "expected => document.querySelector('.reference-picker[open] .reference-results')?.textContent.replace(/\\s+/g, ' ').trim() === expected"
                      expected
                      (-> (Page$WaitForFunctionOptions.)
                          (.setTimeout 1000)))
    (catch TimeoutError _))
  (picker-results))

(defn- highlighted-result []
  (.textContent (browser/locator ".reference-picker[open] tr.highlighted td >> nth=0")))

(defn- wait-for-picker-open []
  (.waitFor (browser/locator ".reference-picker[open]")))

(defn- wait-for-picker-closed []
  (.waitFor (browser/locator ".reference-picker[open]")
            (-> (Locator$WaitForOptions.)
                (.setState WaitForSelectorState/DETACHED))))

(deftest picker-test
  (.click (browser/locator "td:text-is('Alfa')"))
  (press! "ArrowRight")
  (press! "Enter")
  (wait-for-edit-mode)
  (reset! browser/*request-log [])
  (let [row (browser/locator "tr.editing")
        refs-cell (.locator row ":scope > td.column-reference >> nth=0")
        ref-cell (.locator row ":scope > td.column-reference >> nth=1")
        search (browser/locator ".reference-picker[open] .reference-search")]

    (testing "the picker is not on the page before it is opened"
      (is (= 0 (.count (browser/locator "dialog")))))

    (testing "Enter on the add button opens the picker, which lists the entities which are not yet referred"
      (press! "Enter")
      (wait-for-picker-open)
      (is (= "Name Refs Ref Alfa Bravo, Charlie Bravo Delta" (picker-results))
          "the picker comes with the results")
      (is (= [opened-picker] (post-requests)))
      (is (browser/has-focus? search) (browser/focused-element))
      (is (= "Alfa" (highlighted-result)) "highlights the first result"))

    (testing "arrow keys move the highlight"
      (press! "ArrowDown")
      (is (= "Delta" (highlighted-result)))
      (press! "ArrowDown")
      (is (= "Delta" (highlighted-result)) "stays at the last result")
      (press! "ArrowUp")
      (is (= "Alfa" (highlighted-result))))

    (testing "typing filters the results"
      (.type (.keyboard browser/*page*) "del")
      (is (= "Name Refs Ref Delta Create del" (wait-for-picker-results "Name Refs Ref Delta Create del")))
      (is (= "Delta" (highlighted-result))))

    (testing "no matches"
      (.type (.keyboard browser/*page*) "x")
      (is (= "No matches Create delx" (wait-for-picker-results "No matches Create delx")))
      (press! "Backspace")
      (is (= "Name Refs Ref Delta Create del" (wait-for-picker-results "Name Refs Ref Delta Create del"))))

    (testing "Enter adds the highlighted entity, closes the picker, and saves the row"
      (press! "Enter")
      (wait-for-picker-closed)
      (is (= ["Bravo×" "Charlie×" "Delta×"] (vec (.allTextContents (.locator refs-cell ".reference-editor > .pill")))))
      (is (has-focus-soon? (.locator refs-cell ".pill-button")) (browser/focused-element))
      (is (= {:thing/refs ["2" "3" "4"]} (wait-for-saved {:thing/refs ["2" "3" "4"]}))))

    (testing "Escape closes the picker, but does not exit edit mode"
      (press! "Enter")
      (is (= "Name Refs Ref Alfa Bravo, Charlie, Delta Bravo"
             (wait-for-picker-results "Name Refs Ref Alfa Bravo, Charlie, Delta Bravo")))
      (press! "Escape")
      (wait-for-picker-closed)
      (is (has-focus-soon? (.locator refs-cell ".pill-button")) (browser/focused-element))
      (is (= 1 (.count row)))
      (is (= 0 (.count (browser/locator "dialog")))
          "the closed picker is removed from the page"))

    (testing "clicking outside the picker closes it, but does not exit edit mode"
      (.click (.locator refs-cell ".pill-button"))
      (wait-for-picker-results "Name Refs Ref Alfa Bravo, Charlie, Delta Bravo")
      (.click (.mouse browser/*page*) 1 1)
      (wait-for-picker-closed)
      (is (has-focus-soon? (.locator refs-cell ".pill-button")) (browser/focused-element))
      (is (= 1 (.count row))))

    (testing "the close button closes the picker, but does not exit edit mode"
      (.click (.locator refs-cell ".pill-button"))
      (wait-for-picker-results "Name Refs Ref Alfa Bravo, Charlie, Delta Bravo")
      (is (= "Search Refs" (.getAttribute search "placeholder")))
      (.click (browser/locator ".reference-picker[open] .reference-picker-close"))
      (wait-for-picker-closed)
      (is (has-focus-soon? (.locator refs-cell ".pill-button")) (browser/focused-element))
      (is (= 1 (.count row))))

    (testing "clicking a result in a single-value column replaces the value, and saves the row"
      (.click (.locator ref-cell ".pill-button"))
      (is (= "Name Refs Ref Alfa Bravo, Charlie, Delta Bravo Charlie Delta"
             (wait-for-picker-results "Name Refs Ref Alfa Bravo, Charlie, Delta Bravo Charlie Delta")))
      (.click (browser/locator ".reference-picker[open] tr:has(td:text-is('Charlie'))"))
      (wait-for-picker-closed)
      (is (= ["Charlie×"] (vec (.allTextContents (.locator ref-cell ".reference-editor > .pill")))))
      (is (has-focus-soon? (.locator ref-cell ".pill-button")) (browser/focused-element))
      (is (= {:thing/ref "3"} (wait-for-saved {:thing/ref "3"}))))

    (testing "closing the picker without picking makes no requests"
      (is (= {opened-picker 5 ; the tests above opened the picker five times
              saved-reference 2} ; and picked two times
             (frequencies (remove #(= "/spreadsheet/things/reference-search" (:path %))
                                  (post-requests))))))

    (testing "exiting edit mode keeps the picked references"
      (press! "F2")
      (wait-for-view-mode)
      (is (= {:thing/refs ["2" "3" "4"], :thing/ref "3"}
             (select-keys (db/get-by-id :things "1") [:thing/refs :thing/ref]))))))

(deftest create-test
  (.click (browser/locator "td:text-is('Alfa')"))
  (press! "ArrowRight")
  (press! "Enter")
  (wait-for-edit-mode)
  (reset! browser/*request-log [])
  (let [row (browser/locator "tr.editing")
        refs-cell (.locator row ":scope > td.column-reference >> nth=0")
        ref-cell (.locator row ":scope > td.column-reference >> nth=1")]

    (testing "the create option is not shown without a query"
      (press! "Enter")
      (is (= "Name Refs Ref Alfa Bravo, Charlie Bravo Delta"
             (wait-for-picker-results "Name Refs Ref Alfa Bravo, Charlie Bravo Delta"))))

    (testing "the create option is shown after the matches"
      (.type (.keyboard browser/*page*) "Del")
      (is (= "Name Refs Ref Delta Create Del"
             (wait-for-picker-results "Name Refs Ref Delta Create Del")))
      (is (= "Delta" (highlighted-result)))
      (press! "ArrowDown")
      (is (= "Create Del" (.textContent (browser/locator ".reference-picker[open] .highlighted")))))

    (testing "the create option is highlighted when there are no matches"
      (.type (.keyboard browser/*page*) "ta Doe")
      (is (= "No matches Create Delta Doe"
             (wait-for-picker-results "No matches Create Delta Doe")))
      (is (= "Create Delta Doe" (.textContent (browser/locator ".reference-picker[open] .highlighted")))))

    (testing "Enter creates the entity and adds it as a pill"
      (press! "Enter")
      (wait-for-picker-closed)
      (.waitFor (.locator refs-cell ".reference-editor > .pill:has-text('Delta Doe')"))
      (is (= ["Bravo×" "Charlie×" "Delta Doe×"] (vec (.allTextContents (.locator refs-cell ".reference-editor > .pill")))))
      (is (= {:entity/id "5", :thing/name "Delta Doe"} (db/get-by-id :things "5")))
      (is (has-focus-soon? (.locator refs-cell ".pill-button")) (browser/focused-element))
      (is (= {:thing/refs ["2" "3" "5"]} (wait-for-saved {:thing/refs ["2" "3" "5"]}))
          "saves the reference to the created entity"))

    (testing "clicking the create option in a single-value column replaces the value"
      (.click (.locator ref-cell ".pill-button"))
      (wait-for-picker-open)
      (.type (.keyboard browser/*page*) "Echo")
      (wait-for-picker-results "No matches Create Echo")
      (.click (browser/locator ".reference-picker[open] .reference-create"))
      (wait-for-picker-closed)
      (.waitFor (.locator ref-cell ".reference-editor > .pill:has-text('Echo')"))
      (is (= ["Echo×"] (vec (.allTextContents (.locator ref-cell ".reference-editor > .pill")))))
      (is (= {:thing/ref "6"} (wait-for-saved {:thing/ref "6"}))
          "saves the reference to the created entity"))

    (testing "exiting edit mode keeps the references to the created entities"
      (press! "F2")
      (wait-for-view-mode)
      (is (= {:thing/refs ["2" "3" "5"], :thing/ref "6"}
             (select-keys (db/get-by-id :things "1") [:thing/refs :thing/ref]))))))

(defn- gate-requests!
  "Makes the requests, whose URL has the suffix, wait until the test opens the named gate.
   Then the test can do things while the request is in progress."
  [gate-name url-suffix]
  (.evaluate browser/*page* "([gateName, urlSuffix]) => {
    window.gates ??= {};
    const gate = new Promise(resolve => window.gates[gateName] = resolve);
    const fetch = window.fetch;
    window.fetch = async (url, ...args) => {
      if (String(url).endsWith(urlSuffix)) {
        await gate;
      }
      return fetch(url, ...args);
    };
  }" [gate-name url-suffix]))

(defn- open-gate! [gate-name]
  (.evaluate browser/*page* "gateName => window.gates[gateName]()" gate-name))

(defn- pill-texts [^Locator cell]
  (vec (.allTextContents (.locator cell ".reference-editor > .pill"))))

(deftest pick-in-progress-test
  (.click (browser/locator "td:text-is('Alfa')"))
  (press! "ArrowRight")
  (press! "Enter")
  (wait-for-edit-mode)
  (press! "Enter")
  (wait-for-picker-open)
  (gate-requests! "pick" "/save-reference")
  (reset! browser/*request-log [])
  (let [row (browser/locator "tr.editing")
        refs-cell (.locator row ":scope > td.column-reference >> nth=0")
        open-pickers (browser/locator ".reference-picker[open]")]

    (testing "the picker stays open until the pick is saved"
      (press! "Enter")
      (is (= "true" (.getAttribute open-pickers "data-busy")))
      (is (= ["Bravo×" "Charlie×"] (pill-texts refs-cell))))

    (testing "the picker cannot be closed while the pick is being saved"
      (press! "Escape")
      (press! "Escape")
      (.click (browser/locator ".reference-picker-close"))
      (.click (.mouse browser/*page*) 1 1)
      (is (= 1 (.count open-pickers))))

    (testing "the picker accepts no more picks"
      (press! "ArrowDown")
      (press! "Enter")
      (.click (browser/locator ".reference-picker tr:has(td:text-is('Delta'))"))
      (is (= 1 (.count open-pickers))))

    (testing "the picker closes when the pick is saved"
      (open-gate! "pick")
      (wait-for-picker-closed)
      (is (= 0 (.count (browser/locator "dialog"))))
      (is (= ["Alfa×" "Bravo×" "Charlie×"] (pill-texts refs-cell)))
      (is (= {:thing/refs ["2" "3" "1"]}
             (select-keys (db/get-by-id :things "1") [:thing/refs])))
      (is (= [saved-reference] (post-requests))
          "only the first pick was sent"))

    (testing "the add button gets the focus, and the row stays in edit mode"
      (is (has-focus-soon? (.locator refs-cell ".pill-button")) (browser/focused-element))
      (is (= 1 (.count row))))))

(deftest pick-waits-for-earlier-requests-test
  (.dblclick (browser/locator "td:text-is('Alfa')"))
  (wait-for-edit-mode)
  (gate-requests! "open" "/reference-picker")
  (gate-requests! "save" "/save-and-continue")
  (let [row (browser/locator "tr.editing")
        refs-cell (.locator row ":scope > td.column-reference >> nth=0")]

    ;; The user changes a field while the picker's opening is in progress,
    ;; so that saving the field is in progress when the picker opens
    (.click (.locator refs-cell ".pill-button"))
    (press! "ControlOrMeta+a")
    (.type (.keyboard browser/*page*) "Alfa 2")
    (press! "Tab")
    (.evaluate browser/*page* "() => new Promise(resolve => setTimeout(resolve, 0))")
    (open-gate! "open")
    (wait-for-picker-open)

    (testing "the pick waits for the earlier request"
      (press! "Enter")
      (is (= "true" (.getAttribute (browser/locator ".reference-picker[open]") "data-busy")))
      (is (= "Alfa" (:thing/name (db/get-by-id :things "1")))
          "the test should pick before the earlier request finishes"))

    (testing "both changes are saved"
      (open-gate! "save")
      (wait-for-picker-closed)
      (is (= ["Alfa 2×" "Bravo×" "Charlie×"] (pill-texts refs-cell)))
      (is (= {:thing/name "Alfa 2", :thing/refs ["2" "3" "1"]}
             (select-keys (db/get-by-id :things "1") [:thing/name :thing/refs]))))))

(deftest failed-pick-test
  (.click (browser/locator "td:text-is('Alfa')"))
  (press! "ArrowRight")
  (press! "ArrowRight")
  (press! "Enter")
  (wait-for-edit-mode)
  (let [row (browser/locator "tr.editing")
        ref-cell (.locator row ":scope > td.column-reference >> nth=1")]
    (is (= ["Bravo×"] (pill-texts ref-cell)))
    (press! "Enter")
    (wait-for-picker-open)
    (.type (.keyboard browser/*page*) "Echo")
    (wait-for-picker-results "No matches Create Echo")
    (.route browser/*page* "**/spreadsheet/things/1/save-reference" (reify Consumer
                                                                       (accept [_ route]
                                                                         (.abort ^Route route))))

    (testing "a failed pick closes the picker, and shows the error"
      (press! "Enter")
      (wait-for-picker-closed)
      (is (= "Could not reach the server. The row is not saved."
             (.textContent (browser/locator ".status-bar-message"))))
      (is (some? (.getAttribute row "data-save-error"))))

    (testing "the old reference of a single-value column is not lost"
      (is (= ["Bravo×"] (pill-texts ref-cell)))
      (is (= "2" (:thing/ref (db/get-by-id :things "1")))))

    (testing "the add button gets the focus, and the row stays in edit mode"
      (is (has-focus-soon? (.locator ref-cell ".pill-button")) (browser/focused-element))
      (is (= 1 (.count row))))

    (testing "the pick can be tried again"
      (.unrouteAll browser/*page*)
      (press! "Enter")
      (wait-for-picker-open)
      (.type (.keyboard browser/*page*) "Echo")
      (wait-for-picker-results "No matches Create Echo")
      (press! "Enter")
      (wait-for-picker-closed)
      (is (= ["Echo×"] (pill-texts ref-cell)))
      (is (= "5" (:thing/ref (db/get-by-id :things "1"))))
      (is (.isHidden (browser/locator ".status-bar")))
      (is (nil? (.getAttribute row "data-save-error"))))))

(deftest remove-focus-test
  (.click (browser/locator "td:text-is('Alfa')"))
  (press! "ArrowRight")
  (press! "Enter")
  (wait-for-edit-mode)
  (let [row (browser/locator "tr.editing")
        refs-cell (.locator row ":scope > td.column-reference >> nth=0")
        remove-charlie (.locator refs-cell ".pill-remove[aria-label='Remove Charlie']")]
    (press! "Shift+Tab")
    (press! "Shift+Tab")
    (is (browser/has-focus? (.locator refs-cell ".pill-remove[aria-label='Remove Bravo']")) (browser/focused-element))
    (gate-requests! "remove" "/save-reference")

    (testing "removing a reference moves the focus to the next remove button"
      (press! "Enter")
      (is (= ["Charlie×"] (pill-texts refs-cell)))
      (is (browser/has-focus? remove-charlie) (browser/focused-element)))

    (testing "the focus stays there when the server renders the buttons again"
      (.evaluate remove-charlie "button => button.dataset.old = 'true'")
      (open-gate! "remove")
      (is (= {:thing/refs ["3"]} (wait-for-saved {:thing/refs ["3"]})))
      (.waitFor (.locator refs-cell ".pill-remove:not([data-old])"))
      (is (browser/has-focus? remove-charlie) (browser/focused-element))
      (is (= 1 (.count row))))))

(deftest open-picker-after-removal-test
  (.click (browser/locator "td:text-is('Alfa')"))
  (press! "ArrowRight")
  (press! "Enter")
  (wait-for-edit-mode)
  (let [row (browser/locator "tr.editing")
        refs-cell (.locator row ":scope > td.column-reference >> nth=0")]
    (gate-requests! "remove" "/save-reference")
    (reset! browser/*request-log [])

    (testing "the picker, which was opened while a removal was being saved, stays open"
      (.click (.locator refs-cell ".pill-remove[aria-label='Remove Charlie']"))
      (.click (.locator refs-cell ".pill-button"))
      (open-gate! "remove")
      (wait-for-picker-open)
      (is (= [saved-reference opened-picker] (post-requests))
          "the server's responses come in the same order as the user's actions")
      (is (= "Name Refs Ref Alfa Bravo Bravo Charlie Delta" (picker-results))
          "the results have the removed reference")
      (is (= ["Bravo×"] (pill-texts refs-cell))))))
