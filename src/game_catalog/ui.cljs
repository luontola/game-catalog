(ns game-catalog.ui
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]
            [fipp.edn :as fipp.edn]
            [game-catalog.db :as db]
            [game-catalog.firebase :as firebase]
            [game-catalog.spreadsheet :as spreadsheet]
            [kitchen-async.promise :as p]
            [reagent.core :as r]
            [reagent.dom.client :as dom]))

(def schema
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
           :documents {}}

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
               :documents {}}})

(def sample-data
  {:games {"e5da0728-35f3-4330-9432-9199142166ef" {:name "Amnesia: Rebirth"
                                                   :release 2020
                                                   :series "Amnesia"
                                                   :purchases ["a94b179d-d41d-48bc-b874-5e2f1019e61a"
                                                               "86464e3b-07b7-4e8e-bc01-c424de332f93"]}
           "1284c953-9ecf-4caa-bb0c-90e6d900b5bc" {:name "Amnesia: The Dark Descent"
                                                   :release 2010
                                                   :series "Amnesia"
                                                   :purchases ["7d201baf-7a4d-49eb-8dd1-4bfd9920320e"]}
           "84ab7ef8-adb2-4944-b933-fdc0d4ea6ba3" {:name "Darwinia"
                                                   :purchases ["7d201baf-7a4d-49eb-8dd1-4bfd9920320e"]}
           "f96d974f-50de-431c-bc27-976ecc1d0bb3" {:name "Satisfactory"
                                                   :purchases ["a94b179d-d41d-48bc-b874-5e2f1019e61a"]
                                                   :status "Backlog"}}

   :purchases {"7d201baf-7a4d-49eb-8dd1-4bfd9920320e" {:date "2017-06-07"
                                                       :cost "0 EUR"
                                                       :base-games ["1284c953-9ecf-4caa-bb0c-90e6d900b5bc"
                                                                    "84ab7ef8-adb2-4944-b933-fdc0d4ea6ba3"]
                                                       :shop ["GOG"]}
               "a94b179d-d41d-48bc-b874-5e2f1019e61a" {:date "2022-03-25"
                                                       :cost "36.39 EUR"
                                                       :base-games ["e5da0728-35f3-4330-9432-9199142166ef"
                                                                    "f96d974f-50de-431c-bc27-976ecc1d0bb3"]
                                                       :shop ["Humble Store"
                                                              "Steam"]}
               "86464e3b-07b7-4e8e-bc01-c424de332f93" {:date "2022-04-23"
                                                       :cost "0 EUR"
                                                       :base-games ["e5da0728-35f3-4330-9432-9199142166ef"]
                                                       :shop ["Epic Games"]}}})

(defonce *data (r/atom schema))

(defn game-sort-key [{:keys [name series release]}]
  (-> (str (when-not (str/blank? series)
             (str series " " release " "))
           name)
      (str/lower-case)
      (str/replace #"\bthe " "")
      (str/trim)))

(defn games-table []
  [spreadsheet/table {:columns (-> @*data :games :columns)
                      :*data *data
                      :self-collection :games
                      :sort-key game-sort-key}])

(defn purchases-table []
  [spreadsheet/table {:columns (-> @*data :purchases :columns)
                      :*data *data
                      :self-collection :purchases
                      :sort-key :date}])

(defn pretty-print [data]
  [:pre (with-out-str (fipp.edn/pprint data))])

(defn login-button []
  (let [user @firebase/*user]
    (case user
      :loading "⏳"

      nil [:button {:type "button"
                    :on-click #(firebase/sign-in!)}
           "Sign in"]
      [:span (str "Signed in as " (.-displayName user) " ")
       [:button {:type "button"
                 :on-click #(firebase/sign-out!)}
        "Sign out"]])))

(defn firebase-emulator-toggle []
  [:label
   [:input {:type "checkbox"
            :default-checked firebase/firebase-emulator?
            :on-click (fn [event]
                        (firebase/set-firebase-emulator! (.. event -target -checked)))}]
   " Firebase Local Emulator Suite"])


(defn load-data [new-data]
  (swap! *data (fn [data]
                 (-> data
                     (assoc-in [:games :documents] (:games new-data))
                     (assoc-in [:purchases :documents] (:purchases new-data)))))
  nil)

(defn pending-changes []
  (let [data @*data
        previous {} ; TODO: use the snapshot of last load
        current {:games (get-in data [:games :documents])
                 :purchases (get-in data [:purchases :documents])}]
    (db/diff previous current)))

(defn load-samples-button []
  [:button {:type "button"
            :on-click (fn []
                        (load-data sample-data))}
   "Load samples"])

(defn save-button []
  [:button {:type "button"
            :on-click (fn []
                        (let [db (:firestore firebase/*ctx*)]
                          (db/update-collections! db (pending-changes))))}
   "Save to Firestore"])

(defn app []
  [:<>
   [:h1 "Game Catalog"]
   [:p [login-button]]
   [:p [firebase-emulator-toggle]]
   [:h2 "Games"]
   [games-table]
   [:h2 "DLCs"]
   [:p "TODO"]
   [:h2 "Purchases"]
   [purchases-table]

   [:hr]
   [:h2 "Current state"]
   [pretty-print @*data]
   [:h2 "Pending changes"]
   [pretty-print (pending-changes)]
   [:p [load-samples-button] " " [save-button]]])


(defn install-js-error-reporter! []
  ;; TODO: send errors to some monitoring server
  (.addEventListener js/window "error"
                     (fn [event]
                       (prn {:message (.-message event)
                             :source (.-message event)
                             :error (.-error event)})))
  (.addEventListener js/window "unhandledrejection"
                     (fn [event]
                       (prn {:message "Uncaught exception in a promise"
                             :error (.-reason event)}))))

(defonce one-time-setup (delay (install-js-error-reporter!)))

(defonce root (dom/create-root (.getElementById js/document "root")))

(defn init! []
  (force one-time-setup)
  (firebase/init!)
  (dom/render root [app]))

(defn ^:dev/after-load re-render []
  #_(pp/pprint (db/diff sample-data @*data))
  (p/do
    (firebase/close!)
    (init!)))
