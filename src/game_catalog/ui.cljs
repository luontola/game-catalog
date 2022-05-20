(ns game-catalog.ui
  (:require [clojure.string :as str]
            [game-catalog.spreadsheet :as spreadsheet]
            [reagent.core :as r]
            [reagent.dom :as dom]))

(def sample-data
  {:games {#uuid "e5da0728-35f3-4330-9432-9199142166ef" {:name "Amnesia: Rebirth"
                                                         :release 2020
                                                         :series "Amnesia"}
           #uuid "1284c953-9ecf-4caa-bb0c-90e6d900b5bc" {:name "Amnesia: The Dark Descent"
                                                         :release 2010
                                                         :series "Amnesia"}
           #uuid "84ab7ef8-adb2-4944-b933-fdc0d4ea6ba3" {:name "Darwinia"}
           #uuid "f96d974f-50de-431c-bc27-976ecc1d0bb3" {:name "Satisfactory"
                                                         :status "Backlog"}}
   :purchases {#uuid "7d201baf-7a4d-49eb-8dd1-4bfd9920320e" {:date "2017-06-07"
                                                             :cost "0 EUR"
                                                             :base-games [#uuid "1284c953-9ecf-4caa-bb0c-90e6d900b5bc"
                                                                          #uuid "84ab7ef8-adb2-4944-b933-fdc0d4ea6ba3"]
                                                             :shop ["GOG"]}
               #uuid "a94b179d-d41d-48bc-b874-5e2f1019e61a" {:date "2022-03-25"
                                                             :cost "36.39 EUR"
                                                             :base-games [#uuid "e5da0728-35f3-4330-9432-9199142166ef"
                                                                          #uuid "f96d974f-50de-431c-bc27-976ecc1d0bb3"]
                                                             :shop ["Humble Store"
                                                                    "Steam"]}
               #uuid "86464e3b-07b7-4e8e-bc01-c424de332f93" {:date "2022-04-23"
                                                             :cost "0 EUR"
                                                             :base-games [#uuid "e5da0728-35f3-4330-9432-9199142166ef"]
                                                             :shop ["Epic Games"]}}})

(defonce *data (r/atom sample-data))

(def games-columns
  [{:title "Name"
    :data-path :name
    :data-type :text}
   {:title "Release"
    :data-path :release
    :data-type :text}
   {:title "Remake"
    :data-path :remake
    :data-type :text}
   {:title "Series"
    :data-path :series
    :data-type :text}
   {:title "Purchases"
    :data-path nil
    :data-type :reference}
   {:title "Status"
    :data-path :status
    :data-type :text}
   {:title "Content"
    :data-path :content
    :data-type :text}
   {:title "DLCs"
    :data-path nil
    :data-type :reference}])

(defn game-sort-key [{:keys [name series release]}]
  (-> (str (when-not (str/blank? series)
             (str series " " release " "))
           name)
      (str/lower-case)
      (str/replace #"\bthe " "")
      (str/trim)))

(defn games-table []
  [spreadsheet/table {:columns games-columns
                      :*data *data
                      :data-path [:games]
                      :sort-key game-sort-key}])

(def purchases-columns
  [{:title "Date"
    :data-path :date
    :data-type :text}
   {:title "Cost"
    :data-path :cost
    :data-type :money}
   {:title "Base Games"
    :data-path nil ; TODO
    :data-type :reference}
   {:title "DLCs"
    :data-path nil ; TODO
    :data-type :reference}
   {:title "Bundle Name"
    :data-path :bundle-name
    :data-type :text}
   {:title "Shop"
    :data-path :shop
    :data-type :multi-select}])

(defn purchases-table []
  [spreadsheet/table {:columns purchases-columns
                      :*data *data
                      :data-path [:purchases]
                      :sort-key :date}])

(defn app []
  [:<>
   [:h1 "Game Catalog"]
   [:h2 "Games"]
   [games-table]
   [:h2 "DLCs"]
   [:h2 "Purchases"]
   [purchases-table]])

(defn init! []
  (let [root (.getElementById js/document "root")]
    (dom/render [app] root)))

(init!)

(defn ^:dev/before-load stop [])

(defn ^:dev/after-load start []
  (init!))
