(ns game-catalog.testing.browser
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.main :as main]
            [mount.core :as mount]
            [unilog.config :refer [start-logging!]])
  (:import (com.microsoft.playwright Browser BrowserContext BrowserType$LaunchOptions Page Playwright)
           (org.eclipse.jetty.server NetworkConnector)))

(def ^:dynamic ^String *base-url* nil)
(def ^:dynamic ^Playwright *playwright* nil)
(def ^:dynamic ^Browser *browser* nil)
(def ^:dynamic ^BrowserContext *context* nil)
(def ^:dynamic ^Page *page* nil)

(defn fixture [f]
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

(defn navigate! [path]
  (.navigate *page* (str *base-url* path)))

(defn locator [selector]
  (.locator *page* selector))

(defn has-focus [element]
  (.evaluate element "el => el === document.activeElement"))

(defn focused-element []
  (.evaluate *page* "document.activeElement.outerHTML"))
