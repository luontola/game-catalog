(ns ^:slow game-catalog.e2e-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [game-catalog.main :as main]
            [mount.core :as mount]
            [unilog.config :refer [start-logging!]])
  (:import (com.microsoft.playwright Browser BrowserContext BrowserType$LaunchOptions Page Playwright)))

(def ^:dynamic ^String *base-url* nil)
(def ^:dynamic ^Playwright *playwright* nil)
(def ^:dynamic ^Browser *browser* nil)
(def ^:dynamic ^BrowserContext *context* nil)
(def ^:dynamic ^Page *page* nil)

(defn http-server-fixture [f]
  (start-logging! {:level "info"
                   :console true})
  (mount/start-with-args {:port 0})
  (try
    (let [port (.getLocalPort (first (.getConnectors main/http-server)))]
      (binding [*base-url* (str "http://localhost:" port)]
        (f)))
    (finally
      (mount/stop))))

(defn playwright-fixture [f]
  (with-open [playwright (Playwright/create)
              browser (.launch (.chromium playwright)
                               (-> (BrowserType$LaunchOptions.)
                                   (.setHeadless true)))
              context (.newContext browser)
              page (.newPage context)]
    (binding [*playwright* playwright
              *browser* browser
              *context* context
              *page* page]
      (.setDefaultTimeout context 5000)
      (f))))

(use-fixtures :once http-server-fixture)
(use-fixtures :each playwright-fixture)

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
  (testing "games page is accessible"
    (.navigate *page* (str *base-url* "/games"))

    (is (= 200 (-> (.request *page*)
                   (.get (str *base-url* "/games"))
                   (.status))))))
