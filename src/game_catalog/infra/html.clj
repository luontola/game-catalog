(ns game-catalog.infra.html
  (:require [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.json :as json]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response]))

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
