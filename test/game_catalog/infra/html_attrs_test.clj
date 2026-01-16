(ns ^:slow game-catalog.infra.html-attrs-test
  (:require [clojure.test :refer :all]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as infra.html]
            [game-catalog.testing.browser :as browser]
            [game-catalog.testing.util :refer [with-fixtures]]
            [game-catalog.ui.layout :as layout]))

(def autofocus-routes
  [["/test"
    {:get {:handler (fn [_request]
                      (-> (h/html
                            [:button {:hx-get "/test/input" :hx-target "this" :hx-swap "outerHTML"}
                             "Load input"])
                          (layout/page)
                          (infra.html/response)))}}]
   ["/test/input"
    {:get {:handler (fn [_request]
                      (-> (h/html
                            [:input#input1 {:type "text" :value "hello" :autofocus true}])
                          (infra.html/response)))}}]])

(deftest autofocus-test
  (with-fixtures [(partial browser/fixture autofocus-routes)]
    (browser/navigate! "/test")
    (.click (browser/locator "button"))
    (.waitFor (browser/locator "#input1"))

    (testing "autofocus selects all text by default"
      (let [selection (.evaluate browser/*page* "window.getSelection().toString()")]
        (is (= "hello" selection))))

    (testing "autofocus attribute is removed after focusing"
      ;; Browsers expect at most one autofocus attribute per page.
      ;; If a second autofocus is added, the browser will still focus the first autofocus.
      ;; When adding autofocuses dynamically with htmx, we need to clean them up after use.
      (let [input1 (browser/locator "#input1")]
        (is (not (.evaluate input1 "el => el.hasAttribute('autofocus')")))))))
