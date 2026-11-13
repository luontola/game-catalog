(ns ^:slow game-catalog.ui.spreadsheet.edit-mode-e2e-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.infra.html :as infra.html]
            [game-catalog.testing.browser :as browser]
            [game-catalog.ui.layout :as layout]
            [game-catalog.ui.spreadsheet :as spreadsheet])
  (:import (com.microsoft.playwright Keyboard Locator$WaitForOptions Page$WaitForConditionOptions Page$WaitForFunctionOptions Route Route$FulfillOptions TimeoutError)
           (com.microsoft.playwright.options WaitForSelectorState)
           (java.util.function Consumer)))

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
                      (try
                        (f)
                        (finally
                          (.unrouteAll browser/*page*)))))

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
    (is (= [{:method "POST", :path "/spreadsheet/things/1/save"}]
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

(def ^:private autosaved {:method "POST", :path "/spreadsheet/things/1/save-and-continue"})
(def ^:private exited {:method "POST", :path "/spreadsheet/things/1/save"})

(deftest autosave-test
  (.dblclick (browser/locator "td:text-is('Baz')"))
  (wait-for-edit-mode)
  (is (= "1:0:INPUT" (wait-for-focus "1:0:INPUT")))
  (reset! browser/*request-log [])

  (testing "moving to another field without changes does not save"
    (press! "Tab")
    (is (= "1:1:INPUT" (wait-for-focus "1:1:INPUT")))
    (press! "Shift+Tab")
    (is (= "1:0:INPUT" (wait-for-focus "1:0:INPUT")))
    (is (= [] (post-requests))))

  (testing "moving to another field saves the changes, and stays in edit mode"
    (press! "ControlOrMeta+a")
    (.type (keyboard) "Grault")
    (press! "Tab")
    (is (= {:thing/foo "Grault"} (wait-for-saved {:thing/foo "Grault"})))
    (is (= [autosaved] (post-requests)))
    (is (= "1:1:INPUT" (focused)))
    (is (= "1" (.getAttribute (browser/locator "tr.editing") "data-entity-id"))))

  (testing "exiting edit mode saves the field which has the focus"
    (press! "ControlOrMeta+a")
    (.type (keyboard) "Garply")
    (reset! browser/*request-log [])

    (press! "Enter")
    (wait-for-view-mode)
    (is (= [exited] (post-requests)))
    (is (= {:thing/foo "Grault", :thing/bar "Garply"}
           (wait-for-saved {:thing/foo "Grault", :thing/bar "Garply"}))))

  (testing "changes made by scripts are saved when exiting edit mode, even without change events"
    (press! "Enter")
    (wait-for-edit-mode)
    (is (= "1:1:INPUT" (wait-for-focus "1:1:INPUT")))
    (.evaluate browser/*page* "() => document.activeElement.value = 'Waldo'")
    (reset! browser/*request-log [])

    (press! "Enter")
    (wait-for-view-mode)
    (is (= [exited] (post-requests)))
    (is (= "Waldo" (:thing/bar (db/get-by-id :things "1"))))))

(deftest autosave-order-test
  (.dblclick (browser/locator "td:text-is('Baz')"))
  (wait-for-edit-mode)
  (is (= "1:0:INPUT" (wait-for-focus "1:0:INPUT")))
  ;; Saving does not finish until the test opens the gate,
  ;; so that the test can make more changes while a save is in progress
  (.evaluate browser/*page* "() => {
    const fetch = window.fetch;
    window.saveGate = new Promise(resolve => window.openSaveGate = resolve);
    window.activeSaves = 0;
    window.maxActiveSaves = 0;
    window.fetch = async (url, ...args) => {
      if (!String(url).endsWith('/save-and-continue')) {
        return fetch(url, ...args);
      }
      window.activeSaves++;
      window.maxActiveSaves = Math.max(window.maxActiveSaves, window.activeSaves);
      try {
        await window.saveGate;
        return await fetch(url, ...args);
      } finally {
        window.activeSaves--;
      }
    };
  }")
  (reset! browser/*request-log [])
  (let [change-field! (fn [value next-field-key]
                        (press! "ControlOrMeta+a")
                        (.type (keyboard) value)
                        (press! next-field-key)
                        ;; The save starts in a timer, so wait for the timers which are already queued
                        (.evaluate browser/*page* "() => new Promise(resolve => setTimeout(resolve, 0))"))]
    (change-field! "Foo 1" "Tab")
    (change-field! "Bar 1" "Shift+Tab")
    (change-field! "Foo 2" "Tab"))
  (is (= {:thing/foo "Baz", :thing/bar "Qux"}
         (select-keys (db/get-by-id :things "1") [:thing/foo :thing/bar]))
      "the test should have made all the changes before the first save finishes")

  (.evaluate browser/*page* "() => window.openSaveGate()")

  (testing "the newest values are saved last"
    (is (= {:thing/foo "Foo 2", :thing/bar "Bar 1"}
           (wait-for-saved {:thing/foo "Foo 2", :thing/bar "Bar 1"}))))

  (testing "saves are sent one at a time, so that an old save cannot overwrite a newer one"
    (is (= 1 (.evaluate browser/*page* "() => window.maxActiveSaves"))))

  (testing "of the saves which wait for their turn, only the newest is sent"
    (is (= [autosaved autosaved] (post-requests))))

  (testing "saving does not disturb editing"
    (is (= "1:1:INPUT" (focused)))
    (is (= "Bar 1" (.evaluate browser/*page* "() => document.activeElement.value")))))

(defn- wait-for-error-message
  "Waits until the status bar is visible, and returns its error message."
  []
  (.waitFor (browser/locator ".status-bar"))
  (.textContent (browser/locator ".status-bar-message")))

(defn- wait-for-no-error-message []
  (.waitFor (browser/locator ".status-bar")
            (-> (Locator$WaitForOptions.)
                (.setState WaitForSelectorState/HIDDEN))))

(defn- marked-as-not-saved? []
  (some? (.getAttribute (browser/locator "tr[id='1']") "data-save-error")))

(defn- input-value [field-name]
  (.inputValue (browser/locator (str "tr.editing input[name='" field-name "']"))))

(defn- fail-requests!
  "Makes the browser's requests to the URL fail, using the handler."
  [url-pattern handler]
  (.route browser/*page* ^String url-pattern (reify Consumer
                                               (accept [_ route]
                                                 (handler route)))))

(deftest save-error-test
  (.dblclick (browser/locator "td:text-is('Baz')"))
  (wait-for-edit-mode)
  (is (= "1:0:INPUT" (wait-for-focus "1:0:INPUT")))

  (testing "a failed save shows the error message from the backend"
    (db/delete! :things "1")
    (press! "ControlOrMeta+a")
    (.type (keyboard) "Grault")
    (press! "Tab")
    (is (= "Row not found. It was probably deleted." (wait-for-error-message))))

  (testing "the row is marked as not saved, and it stays in edit mode with the user's changes"
    (is (marked-as-not-saved?))
    (is (= "1:1:INPUT" (focused)))
    (is (= "Grault" (input-value "thing/foo"))))

  (testing "the next successful save removes the error, and saves also the earlier changes"
    (db/save! :things {:entity/id "1"})
    (press! "ControlOrMeta+a")
    (.type (keyboard) "Garply")
    (press! "Shift+Tab")
    (wait-for-no-error-message)
    (is (not (marked-as-not-saved?)))
    (is (= {:entity/id "1"
            :thing/foo "Grault"
            :thing/bar "Garply"}
           (db/get-by-id :things "1")))))

(deftest save-error-when-exiting-edit-mode-test
  (.dblclick (browser/locator "td:text-is('Baz')"))
  (wait-for-edit-mode)
  (is (= "1:0:INPUT" (wait-for-focus "1:0:INPUT")))
  (press! "ControlOrMeta+a")
  (.type (keyboard) "Grault")

  (testing "when the server cannot be reached, the row stays in edit mode with the user's changes"
    (fail-requests! "**/spreadsheet/things/1/save" #(.abort ^Route %))
    (press! "Enter")
    (is (= "Could not reach the server. The row is not saved." (wait-for-error-message)))
    (is (marked-as-not-saved?))
    (is (= "1:0:INPUT" (focused)))
    (is (= "Grault" (input-value "thing/foo")))
    (is (= "Baz" (:thing/foo (db/get-by-id :things "1")))))

  (testing "exiting edit mode can be tried again"
    (.unrouteAll browser/*page*)
    (press! "Enter")
    (wait-for-view-mode)
    (wait-for-no-error-message)
    (is (= "1:0:TD" (wait-for-focus "1:0:TD")))
    (is (= "Grault" (:thing/foo (db/get-by-id :things "1"))))))

(deftest unknown-error-test
  (.dblclick (browser/locator "td:text-is('Baz')"))
  (wait-for-edit-mode)
  (is (= "1:0:INPUT" (wait-for-focus "1:0:INPUT")))

  (testing "error responses which are not plain text are not shown to the user"
    (fail-requests! "**/spreadsheet/things/1/save-and-continue"
                    #(.fulfill ^Route % (-> (Route$FulfillOptions.)
                                            (.setStatus 500)
                                            (.setContentType "text/html")
                                            (.setBody "<h1>Stack trace</h1>"))))
    (press! "ControlOrMeta+a")
    (.type (keyboard) "Grault")
    (press! "Tab")
    (is (= "Request failed (status 500)" (wait-for-error-message)))
    (is (marked-as-not-saved?)))

  (testing "closing the error message does not exit edit mode"
    (.click (browser/locator ".status-bar-close"))
    (wait-for-no-error-message)
    (is (= "1:1:INPUT" (focused)))
    (is (marked-as-not-saved?) "the row is still not saved")))

(deftest session-expired-test
  (.dblclick (browser/locator "td:text-is('Baz')"))
  (wait-for-edit-mode)
  (is (= "1:0:INPUT" (wait-for-focus "1:0:INPUT")))

  (testing "saving after the session has expired tells the user what to do"
    (.clearCookies browser/*context*)
    (press! "ControlOrMeta+a")
    (.type (keyboard) "Grault")
    (press! "Tab")
    (is (= "The session has expired. Reload the page to continue." (wait-for-error-message)))
    (is (marked-as-not-saved?))
    (is (= "Baz" (:thing/foo (db/get-by-id :things "1"))))))
