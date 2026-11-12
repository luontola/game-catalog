(ns game-catalog.ui.layout
  (:require [game-catalog.infra.hiccup :as h]
            [game-catalog.infra.html :as html]
            [hiccup.page :as hiccup.page]))

(defn page [view]
  (str (h/html
         (assert (= :html hiccup.util/*html-mode*))
         (hiccup.page/doctype :html5)
         [:html {:lang "en"}
          [:head
           [:meta {:charset "utf-8"}]
           [:title {} "Game Catalog"]
           [:meta {:name "viewport", :content "width=device-width, initial-scale=1"}]
           [:link {:rel "stylesheet", :href "/reset.css"}]
           [:link {:rel "stylesheet", :href "/simple.css"}]
           [:link {:rel "stylesheet", :href "/styles.css"}]
           ;; We don't use htmx history. With it enabled, going back in history
           ;; would reload the whole page body, and the focused element is lost.
           [:meta {:name "htmx-config", :content "{\"history\": false}"}]
           ;; Don't upgrade to htmx 4.0.1, because every swap fails in Chrome 124–145:
           ;; https://github.com/bigskysoftware/htmx/issues/4182
           [:script {:type "module"
                     :src "https://cdn.jsdelivr.net/npm/htmx.org@4.0.0/dist/htmx.esm.js"
                     :integrity "sha384-fh3WoeSX2U60P2sV0M8Y6xvhTB5cpTb+AAF28P71BzGUZ/KI7QYrzKfwTFD/OCr6"
                     :crossorigin "anonymous"}]
           [:script {:type "module"
                     :src "/scripts.js"}]]
          [:body {:hx-headers:inherited (html/anti-forgery-headers-json)}
           [:header
            [:h1 "Game Catalog"]
            [:nav
             [:ul
              [:li [:a {:href "/"} "Home"]]
              [:li [:a {:href "/games"} "Games"]]
              [:li [:a {:href "/dlcs"} "DLCs"]]
              [:li [:a {:href "/purchases"} "Purchases"]]
              [:li [:a {:href "https://github.com/luontola/game-catalog"} "GitHub Project"]]]]]
           [:main
            view]]])))
