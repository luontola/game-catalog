(ns game-catalog.testing.browser
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.main :as main]
            [game-catalog.ui.routes :as routes]
            [mount.core :as mount]
            [reitit.ring :as ring]
            [ring.util.http-response :as http-response]
            [unilog.config :refer [start-logging!]])
  (:import (com.microsoft.playwright Browser BrowserContext BrowserType$LaunchOptions Locator Page Playwright Request)
           (java.net URI)
           (org.eclipse.jetty.server NetworkConnector)))

(def ^:dynamic ^String *base-url* nil)
(def ^:dynamic ^Playwright *playwright* nil)
(def ^:dynamic ^Browser *browser* nil)
(def ^:dynamic ^BrowserContext *context* nil)
(def ^:dynamic ^Page *page* nil)
(def *request-log (atom []))

(defn- run-fixture [f]
  (start-logging! {:level :info
                   :console true
                   :overrides {"org.eclipse.jetty" :warn}})
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
          (.onRequest page (fn [^Request request]
                             (let [uri (URI. (.url request))]
                               (swap! *request-log conj (cond-> {:method (.method request)
                                                                 :path (.getPath uri)}
                                                          (.getQuery uri) (assoc :query (.getQuery uri)))))))
          (f))))
    (finally
      (mount/stop))))

(defn fixture
  ([f] (run-fixture f))
  ([routes f]
   (with-redefs [routes/ring-handler (ring/ring-handler
                                       (ring/router routes)
                                       (constantly (http-response/not-found "Not found")))]
     (run-fixture f))))

(defn navigate! [path]
  (.navigate *page* (str *base-url* path)))

(defn ^Locator locator [selector]
  (.locator *page* selector))

(defn has-focus? [^Locator element]
  (.evaluate element "el => el === document.activeElement"))

(defn focused-element []
  (.evaluate *page* "document.activeElement.outerHTML"))
