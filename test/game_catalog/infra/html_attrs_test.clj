(ns ^:slow game-catalog.infra.html-attrs-test
  (:require [clojure.test :refer :all]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as infra.html]
            [game-catalog.testing.browser :as browser]
            [game-catalog.testing.util :refer [with-fixtures]]
            [game-catalog.ui.layout :as layout]))

(def autofocus-routes
  [["/"
    {:get {:handler (fn [_request]
                      (-> (h/html
                            [:button {:hx-get "/input"
                                      :hx-target "this"
                                      :hx-swap "afterend"}
                             "Load input"])
                          (layout/page)
                          (infra.html/response)))}}]
   ["/input"
    {:get {:handler (fn [_request]
                      (-> (h/html
                            [:input#input1 {:type "text"
                                            :value "hello"
                                            :autofocus true}])
                          (infra.html/response)))}}]])

(deftest autofocus-test
  (with-fixtures [(partial browser/fixture autofocus-routes)]
    (browser/navigate! "/")
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

(def auto-scroll-routes
  [["/"
    {:get {:handler (fn [_request]
                      (-> (h/html
                            [:div
                             [:button {:hx-get "/element"
                                       :hx-target "#container"
                                       :hx-swap "afterend"}
                              "Load element"]
                             [:div#container {:style {:height "2000px"
                                                      :border "1px solid blue"}}
                              "Tall content"]])
                          (layout/page)
                          (infra.html/response)))}}]
   ["/element"
    {:get {:handler (fn [_request]
                      (-> (h/html
                            [:div#target {:auto-scroll-into-view true}
                             "Scrolled element"])
                          (infra.html/response)))}}]])

(deftest auto-scroll-into-view-test
  (with-fixtures [(partial browser/fixture auto-scroll-routes)]
    (browser/navigate! "/")
    (let [scroll-before (.evaluate browser/*page* "window.scrollY")]
      (is (= 0 scroll-before) "should start at top of page")

      (.click (browser/locator "button"))
      (.waitFor (browser/locator "#target"))

      (testing "element is scrolled into view"
        (let [scroll-after (.evaluate browser/*page* "window.scrollY")]
          (is (< scroll-before scroll-after))))

      (testing "auto-scroll-into-view attribute is removed after scrolling"
        (let [target (browser/locator "#target")]
          (is (not (.evaluate target "el => el.hasAttribute('auto-scroll-into-view')"))))))))
