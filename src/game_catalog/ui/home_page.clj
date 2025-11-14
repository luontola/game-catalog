(ns game-catalog.ui.home-page
  (:require [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as html]
            [game-catalog.ui.layout :as layout])
  (:import (java.time LocalDateTime)))

(def routes
  [["/"
    {:get {:handler (fn [request]
                      (->
                        (h/html
                          [:p "hello world"]
                          [:button {:hx-post "/clicked"
                                    :hx-swap "outerHTML"}
                           "Click Me"])
                        (layout/page)
                        (html/response)))}}]
   ["/clicked"
    {:post {:handler (fn [request]
                       (->
                         (h/html
                           [:button {:hx-post "/clicked"
                                     :hx-swap "outerHTML"}
                            "Clicked at " (LocalDateTime/now)])
                         (html/response)))}}]])
