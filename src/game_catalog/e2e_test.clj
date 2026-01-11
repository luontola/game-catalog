(ns ^:slow game-catalog.e2e-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [game-catalog.main :as main]
            [mount.core :as mount]
            [unilog.config :refer [start-logging!]])
  (:import (com.microsoft.playwright BrowserType$LaunchOptions Page Playwright)
           (org.eclipse.jetty.server Server)))

(def ^:dynamic *base-url* nil)

(defn start-server-fixture [f]
  (start-logging! {:level "info"
                   :console true})
  (mount/start-with-args {:port 0})
  (let [^Server server @#'main/http-server
        port (.getLocalPort (first (.getConnectors server)))]
    (try
      (binding [*base-url* (str "http://localhost:" port)]
        (f))
      (finally
        (mount/stop)))))

(use-fixtures :once start-server-fixture)

(deftest ^:slow home-page-test
  (testing "home page loads and displays content"
    (with-open [playwright (Playwright/create)
                browser (.launch (.chromium playwright)
                                 (-> (BrowserType$LaunchOptions.)
                                     (.setHeadless true)))
                context (.newContext browser)
                ^Page page (.newPage context)]
      (.setDefaultTimeout page 5000)
      (.navigate page (str *base-url* "/"))
      (is (str/includes? (.textContent page "body") "hello world"))

      (let [button (.locator page "button")]
        (is (= "Click Me" (.textContent button)))
        (.click button)
        (.waitForCondition page #(not= "Click Me" (.textContent button)))
        (is (str/includes? (.textContent button) "Clicked at 20"))))))

(deftest games-page-test
  (testing "games page is accessible"
    (with-open [playwright (Playwright/create)
                browser (.launch (.chromium playwright)
                                 (-> (BrowserType$LaunchOptions.)
                                     (.setHeadless true)))
                context (.newContext browser)
                ^Page page (.newPage context)]
      (.setDefaultTimeout page 5000)
      (.navigate page (str *base-url* "/games"))

      (is (= 200 (-> (.request page)
                     (.get (str *base-url* "/games"))
                     (.status)))))))
