(ns game-catalog.ui
  (:require [reagent.core :as r]
            [reagent.dom :as dom]))

(defonce *data (r/atom nil))

(defn app []
  [:p "Hello world"])

(defn init! []
  (let [root (.getElementById js/document "root")]
    (dom/render [app] root)))

(init!)

(defn ^:dev/before-load stop [])

(defn ^:dev/after-load start []
  (init!))
