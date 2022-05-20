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

(defn game-sort-key [{:keys [name series release]}]
  (-> (str (when-not (str/blank? series)
             (str series " " release " "))
           name)
      (str/lower-case)
      (str/replace #"\bthe " "")
      (str/trim)))

(defn games-table []
  (let [games (->> (:games @*data)
                   (sort-by (comp game-sort-key val)))
        data-path [:games]]
    [:table.spreadsheet
     [:thead
      [:tr
       [:th "Name"]
       [:th "Release"]
       [:th "Remake"]
       [:th "Series"]
       [:th "Purchases"]
       [:th "Status"]
       [:th "Content"]
       [:th "DLCs"]]]
     [:tbody
      (for [[id _game] games]
        (let [data-path (conj data-path id)]
          [:tr {:key (str id)}
           [spreadsheet/data-cell {:*data *data
                                   :data-path (conj data-path :name)
                                   :data-type :text}]
           [spreadsheet/data-cell {:*data *data
                                   :data-path (conj data-path :release)
                                   :data-type :text}]
           [spreadsheet/data-cell {:*data *data
                                   :data-path (conj data-path :remake)
                                   :data-type :text}]
           [spreadsheet/data-cell {:*data *data
                                   :data-path (conj data-path :series)
                                   :data-type :text}]
           [:td ""] ; TODO: purchases
           [spreadsheet/data-cell {:*data *data
                                   :data-path (conj data-path :status)
                                   :data-type :text}]
           [spreadsheet/data-cell {:*data *data
                                   :data-path (conj data-path :content)
                                   :data-type :text}]
           [:td ""]]))]])) ; TODO: DLCs

(defn purchases-table []
  (let [purchases (->> (:purchases @*data)
                       (sort-by (comp :date val)))
        data-path [:purchases]]
    [:table.spreadsheet
     [:thead
      [:tr
       [:th "Date"]
       [:th "Cost"]
       [:th "Base Games"]
       [:th "DLCs"]
       [:th "Bundle Name"]
       [:th "Shop"]]]
     [:tbody
      (for [[id _purchase] purchases]
        (let [data-path (conj data-path id)]
          [:tr {:key (str id)}
           [spreadsheet/data-cell {:*data *data
                                   :data-path (conj data-path :date)
                                   :data-type :text}]
           [spreadsheet/data-cell {:*data *data
                                   :data-path (conj data-path :cost)
                                   :data-type :money}]
           [:td ""] ; TODO: base games
           [:td ""] ; TODO: DLCs
           [spreadsheet/data-cell {:*data *data
                                   :data-path (conj data-path :bundle-name)
                                   :data-type :text}]
           [spreadsheet/data-cell {:*data *data
                                   :data-path (conj data-path :shop)
                                   :data-type :multi-select}]]))]]))

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
