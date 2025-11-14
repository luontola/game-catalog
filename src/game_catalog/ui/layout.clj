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
           [:script {:src "https://cdn.jsdelivr.net/npm/htmx.org@4.0.0-alpha2/dist/htmx.min.js"}]
           [:script {:src "/scripts.js"}]]
          [:body {:hx-headers:inherited (html/anti-forgery-headers-json)}
           [:main
            [:h1 "Game Catalog"]
            view]]])))
