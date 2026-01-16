(ns game-catalog.testing.browser
  (:require [clojure.string :as str]
            [clojure.test :as test]
            [game-catalog.data.db :as db]
            [game-catalog.main :as main]
            [game-catalog.ui.routes :as routes]
            [mount.core :as mount]
            [reitit.ring :as ring]
            [ring.util.http-response :as http-response]
            [unilog.config :refer [start-logging!]])
  (:import (com.microsoft.playwright Browser BrowserContext BrowserType$LaunchOptions Locator Page Page$EmulateMediaOptions Playwright Request Tracing$StartOptions Tracing$StopOptions)
           (com.microsoft.playwright.options ReducedMotion)
           (java.awt Desktop)
           (java.io File)
           (java.net URI)
           (javax.swing JEditorPane JOptionPane UIManager)
           (javax.swing.event HyperlinkEvent$EventType HyperlinkListener)
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
          ;; animations slow down tests and make them flaky if we need to wait for animations to end
          (.emulateMedia page (-> (Page$EmulateMediaOptions.)
                                  (.setReducedMotion ReducedMotion/REDUCE)))
          ;; enable asserting which requests the tests made
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

(defn tracing-fixture [f]
  (let [trace-dir (File. "target/playwright-traces")
        test-var (first test/*testing-vars*) ; empty outside a test var, i.e. with use-fixtures
        trace-name (if test-var
                     (str (-> test-var meta :ns ns-name) "__" (-> test-var meta :name))
                     (str "trace-" (System/currentTimeMillis))) ; fallback
        trace-file (File. trace-dir (str trace-name ".zip"))]
    (.start (.tracing *context*)
            (-> (Tracing$StartOptions.)
                (.setScreenshots true)
                (.setSnapshots true)))
    (try
      (f)
      (finally
        (.mkdirs trace-dir)
        (.stop (.tracing *context*)
               (-> (Tracing$StopOptions.)
                   (.setPath (.toPath trace-file))))
        (println (str "Trace saved to: " trace-file))))))

(defn pause-here []
  (when-not *base-url*
    (throw (IllegalStateException. "not inside browser fixture")))
  (let [test-var (first test/*testing-vars*)
        test-name (when test-var
                    (str (-> test-var meta :ns ns-name) "/" (-> test-var meta :name)))
        test-context (test/testing-contexts-str)
        font (UIManager/getFont "Label.font")
        message (str "<html><body style=\"font-family:" (.getFamily font) "; font-size:" (.getSize font) "pt\">"
                     "Test paused:<br>" test-name
                     (when-not (str/blank? test-context)
                       (str "<br><i>" test-context "</i>"))
                     "<br><br>Server: <a href=\"" *base-url* "\">" *base-url* "</a>"
                     "<br><br>Click OK to continue.</body></html>")
        message (doto (JEditorPane. "text/html" message)
                  (.setEditable false)
                  (.setFocusable false)
                  (.setOpaque false)
                  (.addHyperlinkListener
                    (reify HyperlinkListener
                      (hyperlinkUpdate [_ event]
                        (when (= (.getEventType event) HyperlinkEvent$EventType/ACTIVATED)
                          (.browse (Desktop/getDesktop) (.toURI (.getURL event))))))))]
    (println "Test paused. Click OK in the UI dialog to continue.")
    (JOptionPane/showMessageDialog nil message "Test Paused" JOptionPane/INFORMATION_MESSAGE)))

(defn navigate! [path]
  (.navigate *page* (str *base-url* path)))

(defn ^Locator locator [selector]
  (.locator *page* selector))

(defn has-focus? [^Locator element]
  (.evaluate element "el => el === document.activeElement"))

(defn focused-element []
  (.evaluate *page* "document.activeElement.outerHTML"))
