(ns ^:slow game-catalog.e2e-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.main :as main]
            [game-catalog.testing.html :as html]
            [mount.core :as mount]
            [unilog.config :refer [start-logging!]])
  (:import (com.microsoft.playwright Browser BrowserContext BrowserType$LaunchOptions Page Playwright)
           (org.eclipse.jetty.server NetworkConnector)))

(def ^:dynamic ^String *base-url* nil)
(def ^:dynamic ^Playwright *playwright* nil)
(def ^:dynamic ^Browser *browser* nil)
(def ^:dynamic ^BrowserContext *context* nil)
(def ^:dynamic ^Page *page* nil)

(defn browser-fixture [f]
  (start-logging! {:level "info"
                   :console true})
  (-> (mount/only #{#'db/*collections
                    #'main/http-server
                    #'game-catalog.webapp/app})
      (mount/with-args {:port 0})
      (mount/start))
  (try
    (let [port (.getLocalPort ^NetworkConnector (first (.getConnectors main/http-server)))]
      (with-open [playwright (Playwright/create)
                  browser (.launch (.chromium playwright)
                                   (-> (BrowserType$LaunchOptions.)
                                       (.setHeadless true)))
                  context (.newContext browser)
                  page (.newPage context)]
        (binding [*base-url* (str "http://localhost:" port)
                  *playwright* playwright
                  *browser* browser
                  *context* context
                  *page* page]
          (.setDefaultTimeout context 5000)
          (f))))
    (finally
      (mount/stop))))

(use-fixtures :once browser-fixture)

(deftest home-page-test
  (testing "home page loads and displays content"
    (.navigate *page* (str *base-url* "/"))
    (is (str/includes? (.textContent *page* "body") "hello world"))

    (let [button (.locator *page* "button")]
      (is (= "Click Me" (.textContent button)))
      (.click button)
      (.waitForCondition *page* #(not= "Click Me" (.textContent button)))
      (is (str/includes? (.textContent button) "Clicked at 20")))))

(deftest games-page-test
  (db/init-collection! :games
                       [{:entity/id "1"
                         :game/name "The Legend of Zelda: Breath of the Wild"
                         :game/release "2017"
                         :game/series "Zelda"
                         :game/status "Completed"}
                        {:entity/id "2"
                         :game/name "Portal 2"
                         :game/release "2011"
                         :game/series "Portal"
                         :game/status "Playing"}
                        {:entity/id "3"
                         :game/name "Hollow Knight"
                         :game/release "2017"
                         :game/tags "Metroidvania, Indie"
                         :game/status "Backlog"}])

  (testing "games page is accessible"
    (is (= 200 (-> (.request *page*)
                   (.get (str *base-url* "/games"))
                   (.status)))))

  (testing "games page displays table with game data"
    (.navigate *page* (str *base-url* "/games"))

    (let [table (.locator *page* "table")]
      (is (= (html/normalize-whitespace "
             #  Name                                     Release  Remake  Series Tags                 Purchases  Status   Content  DLCs
             3  Hollow Knight                            2017                    Metroidvania, Indie             Backlog
             2  Portal 2                                 2011             Portal                                 Playing
             1  The Legend of Zelda: Breath of the Wild  2017             Zelda                                  Completed")
             (html/visualize-html table))))))
