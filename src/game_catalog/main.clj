(ns game-catalog.main
  (:require [clojure.tools.logging :as log]
            [game-catalog.webapp :as webapp]
            [mount.core :as mount]
            [ring.adapter.jetty :as httpd]
            [unilog.config :refer [start-logging!]])
  (:import (org.eclipse.jetty.server Server)
           (org.eclipse.jetty.server.handler.gzip GzipHandler))
  (:gen-class))

(mount/defstate ^{:tag Server, :on-reload :noop} http-server
  :start
  (httpd/run-jetty #'webapp/app
                   {:port 8080
                    :join? false
                    :configurator (fn [^Server server]
                                    (.insertHandler server (GzipHandler.)))})
  :stop
  (.stop ^Server http-server))

(defn- log-mount-states [result]
  (doseq [component (:started result)]
    (log/info component "started"))
  (doseq [component (:stopped result)]
    (log/info component "stopped")))

(defn stop-app []
  (log-mount-states (mount/stop)))

(defn start-app []
  (start-logging! {:level "info"
                   :console true})
  (log-mount-states (mount/start)))

(defn -main [& _args]
  (try
    (start-app)
    (-> (Runtime/getRuntime)
        (.addShutdownHook (Thread. ^Runnable stop-app)))
    (catch Throwable t
      (log/error (str "Failed to start\n" (pr-str t)))
      (.printStackTrace t)
      (System/exit 1))))
