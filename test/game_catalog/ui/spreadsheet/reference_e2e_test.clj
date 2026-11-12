(ns ^:slow game-catalog.ui.spreadsheet.reference-e2e-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.infra.html :as infra.html]
            [game-catalog.testing.browser :as browser]
            [game-catalog.ui.layout :as layout]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [game-catalog.ui.spreadsheet.reference :as reference])
  (:import (com.microsoft.playwright Dialog Keyboard Locator$WaitForOptions)
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

(defn- focused []
  (.evaluate browser/*page* "() => {
    const el = document.activeElement;
    const row = el.closest('tr');
    return row.dataset.entityId + ':' + el.tagName + ':' + el.textContent;
  }"))

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
    (is (= "3" (.evaluate browser/*page* "() => document.querySelector('tr:target')?.id")))))

(deftest keyboard-edit-mode-test
  (.click (browser/locator "td:text-is('Alfa')"))
  (press! "ArrowRight")

  (testing "Enter on the cell itself still enters edit mode"
    (press! "Enter")
    (.waitFor (browser/locator "tr.editing:not(.adding)"))
    (is (= "/things" (.evaluate browser/*page* "() => location.pathname + location.hash")))))

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
