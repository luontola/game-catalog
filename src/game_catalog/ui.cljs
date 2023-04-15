(ns game-catalog.ui
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]
            [fipp.edn :as fipp.edn]
            [game-catalog.db :as db]
            [game-catalog.spreadsheet :as spreadsheet]
            [reagent.core :as r]
            [reagent.dom :as dom]))

(def sample-collections
  {:games {:columns [{:title "Name"
                      :field :name
                      :data-type :text}
                     {:title "Release"
                      :field :release
                      :data-type :text}
                     {:title "Remake"
                      :field :remake
                      :data-type :text}
                     {:title "Series"
                      :field :series
                      :data-type :text}
                     {:title "Purchases" ; TODO: should not be editable from this direction - the current select component makes choosing the right purchase impossible
                      :field :purchases
                      :data-type :reference
                      :reference-collection :purchases
                      :reference-foreign-key :base-games}
                     {:title "Status"
                      :field :status
                      :data-type :text}
                     {:title "Content"
                      :field :content
                      :data-type :text}
                     {:title "DLCs"
                      :field :dlcs
                      :data-type :reference
                      :reference-collection :dlcs
                      :reference-foreign-key :base-game}]
           :documents {#uuid "e5da0728-35f3-4330-9432-9199142166ef" {:name "Amnesia: Rebirth"
                                                                     :release 2020
                                                                     :series "Amnesia"
                                                                     :purchases [#uuid "a94b179d-d41d-48bc-b874-5e2f1019e61a"
                                                                                 #uuid "86464e3b-07b7-4e8e-bc01-c424de332f93"]}
                       #uuid "1284c953-9ecf-4caa-bb0c-90e6d900b5bc" {:name "Amnesia: The Dark Descent"
                                                                     :release 2010
                                                                     :series "Amnesia"
                                                                     :purchases [#uuid "7d201baf-7a4d-49eb-8dd1-4bfd9920320e"]}
                       #uuid "84ab7ef8-adb2-4944-b933-fdc0d4ea6ba3" {:name "Darwinia"
                                                                     :purchases [#uuid "7d201baf-7a4d-49eb-8dd1-4bfd9920320e"]}
                       #uuid "f96d974f-50de-431c-bc27-976ecc1d0bb3" {:name "Satisfactory"
                                                                     :purchases [#uuid "a94b179d-d41d-48bc-b874-5e2f1019e61a"]
                                                                     :status "Backlog"}}}

   :purchases {:columns [{:title "Date"
                          :field :date
                          :data-type :text}
                         {:title "Cost"
                          :field :cost
                          :data-type :money}
                         {:title "Base Games"
                          :field :base-games
                          :data-type :reference
                          :reference-collection :games
                          :reference-foreign-key :purchases}
                         {:title "DLCs"
                          :field :dlcs
                          :data-type :reference
                          :reference-collection :dlcs
                          :reference-foreign-key :purchases}
                         {:title "Bundle Name"
                          :field :bundle-name
                          :data-type :text}
                         {:title "Shop"
                          :field :shop
                          :data-type :multi-select}]
               :documents {#uuid "7d201baf-7a4d-49eb-8dd1-4bfd9920320e" {:date "2017-06-07"
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
                                                                         :shop ["Epic Games"]}}}})

(defonce *collections (r/atom sample-collections))

(defn game-sort-key [{:keys [name series release]}]
  (-> (str (when-not (str/blank? series)
             (str series " " release " "))
           name)
      (str/lower-case)
      (str/replace #"\bthe " "")
      (str/trim)))

(defn games-table []
  [spreadsheet/table {:columns (-> @*collections :games :columns)
                      :*data *collections
                      :self-collection :games
                      :sort-key game-sort-key}])

(defn purchases-table []
  [spreadsheet/table {:columns (-> @*collections :purchases :columns)
                      :*data *collections
                      :self-collection :purchases
                      :sort-key :date}])

(defn pretty-print [data]
  [:pre (with-out-str (fipp.edn/pprint data))])

(defn app []
  [:<>
   [:h1 "Game Catalog"]
   [:h2 "Games"]
   [games-table]
   [:h2 "DLCs"]
   [:p "TODO"]
   [:h2 "Purchases"]
   [purchases-table]
   [pretty-print @*collections]])

(defonce root (.getElementById js/document "root"))

(defn init! []
  (dom/render [app] root))

(defn ^:dev/after-load re-render []
  #_(pp/pprint (db/diff sample-collections @*collections))
  (init!))
