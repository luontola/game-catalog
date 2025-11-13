(ns game-catalog.webapp
  (:require [game-catalog.ui.routes :as routes]
            [mount.core :as mount]
            [ring-ttl-session.core :as ttl-session]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.reload :as reload])
  (:import (java.time Duration)))

;; defonce to avoid forgetting sessions every time the code is reloaded in development mode
(defonce session-store (ttl-session/ttl-memory-store (.toSeconds (Duration/ofHours 4))))

(defn wrap-base [handler]
  (-> handler
      (reload/wrap-reload {:dirs ["src" "resources"]})
      (defaults/wrap-defaults (-> defaults/site-defaults
                                  (assoc :proxy true)
                                  (assoc-in [:session :store] session-store)
                                  (assoc-in [:session :flash] false)))))

(mount/defstate app
  :start
  (-> #'routes/ring-handler
      (wrap-base)))
