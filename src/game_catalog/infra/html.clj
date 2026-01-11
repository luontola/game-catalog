(ns game-catalog.infra.html
  (:require [clojure.string :as str]
            [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.json :as json]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response])
  (:import (com.microsoft.playwright Locator)
           (org.jsoup Jsoup)
           (org.jsoup.nodes Element Node TextNode)))

;;;; Common helpers

(defn response [html]
  (when (some? html)
    (-> (http-response/ok (str html))
        (response/content-type "text/html"))))


;;;; CSRF protection

(defn anti-forgery-token []
  (when (bound? #'anti-forgery/*anti-forgery-token*) ; might not be bound in tests
    (force anti-forgery/*anti-forgery-token*))) ; force may be necessary depending on session strategy

(defn anti-forgery-field []
  (h/html [:input {:type "hidden"
                   :name "__anti-forgery-token"
                   :value (anti-forgery-token)}]))

(defn anti-forgery-headers-json []
  (json/write-value-as-string {:x-csrf-token (anti-forgery-token)}))


;;;; Test helpers

(def ^:private hidden-tags #{"style" "script" "noscript"})
(def ^:private inline-tags #{"a" "abbr" "b" "big" "cite" "code" "em" "i" "small" "span" "strong" "tt"})

(defn normalize-whitespace [^String s]
  (-> s
      (str/replace #"[\p{Z}\s]+" " ")
      str/trim))

(defn- visualize-html-element [^Element node]
  (let [result (StringBuilder.)]
    (letfn [(visit [^Node node]
              (cond
                (instance? TextNode node)
                (.append result (.getWholeText ^TextNode node))

                (instance? Element node)
                (let [^Element el node
                      tag (.tagName el)]
                  (when-not (contains? hidden-tags tag)
                    (let [block (not (contains? inline-tags tag))]

                      ;; custom visualization using data-test-icon attribute
                      (when (.hasAttr el "data-test-icon")
                        (doto result
                          (.append " ")
                          (.append (.attr el "data-test-icon"))
                          (.append " ")))

                      (when block
                        (.append result " "))

                      ;; custom visualization using data-test-content attribute
                      (if (.hasAttr el "data-test-content")
                        (.append result (.attr el "data-test-content"))
                        (doseq [child (.childNodes el)]
                          (visit child)))

                      (when block
                        (.append result " ")))))))]
      (visit node))
    (normalize-whitespace (.toString result))))

(defn visualize-html [html]
  (cond
    (nil? html) ""
    (string? html) (if (str/blank? ^String html)
                     ""
                     (visualize-html-element (.body (Jsoup/parse ^String html))))
    (instance? Locator html) (recur (.evaluate ^Locator html "(element) => element.outerHTML"))
    :else ""))
