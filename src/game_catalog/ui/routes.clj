(ns game-catalog.ui.routes
  (:require [game-catalog.ui.games-page :as games-page]
            [game-catalog.ui.home-page :as home-page]
            [reitit.ring :as ring]
            [ring.util.http-response :as http-response]))

(def routes
  [home-page/routes
   games-page/routes])

(def router (ring/router routes))

(def ring-handler (ring/ring-handler router (constantly (http-response/not-found "Not found"))))
