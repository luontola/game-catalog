(ns game-catalog.ui
  (:require [clojure.string :as str]
            [fipp.edn :as fipp.edn]
            [game-catalog.db :as db]
            [game-catalog.firebase :as firebase]
            [game-catalog.spreadsheet :as spreadsheet]
            [kitchen-async.promise :as p]
            [reagent.core :as r]
            [reagent.dom.client :as dom]))

(def schemas
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
                      :reference-foreign-key :base-game}]}

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
                          :data-type :multi-select}]}})

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

(defonce *current-data (r/atom nil))
(defonce *loaded-data (r/atom nil))

(defn game-sort-key [{:keys [name series release]}]
  (-> (str (when-not (str/blank? series)
             (str series " " release " "))
           name)
      (str/lower-case)
      (str/replace #"\bthe " "")
      (str/trim)))

(defn games-table []
  [spreadsheet/table {:columns (-> schemas :games :columns)
                      :*data *current-data
                      :self-collection :games
                      :sort-key game-sort-key}])

(defn purchases-table []
  [spreadsheet/table {:columns (-> schemas :purchases :columns)
                      :*data *current-data
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


(defn pending-changes []
  (db/diff @*loaded-data @*current-data))

(defn load-samples []
  ;; We leave *loaded-data empty, so that if it contains Firestore data,
  ;; it will be easy to delete from Firestore documents that are not in sample data.
  (reset! *current-data sample-data))

(defn load-from-firestore! []
  (p/let [db (:firestore firebase/*ctx*)
          data (db/read-collections! db (keys schemas))]
    (reset! *current-data data)
    (reset! *loaded-data data)))

(defn save-to-firestore! []
  (let [db (:firestore firebase/*ctx*)]
    (p/do
      (db/update-collections! db (pending-changes))
      (load-from-firestore!))))

(defn load-samples-button []
  [:button {:type "button"
            :on-click load-samples}
   "Load samples"])

(defn load-firestore-button []
  [:button {:type "button"
            :on-click (fn []
                        (p/try
                          (load-from-firestore!)
                          (p/catch :default e
                            (js/console.error "Load from Firestore failed:" e)
                            (js/alert (str "Load failed:\n" e)))))}
   "Load from Firestore"])

(defn save-firestore-button []
  [:button {:type "button"
            :on-click (fn []
                        (p/try
                          (save-to-firestore!)
                          (p/catch :default e
                            (js/console.error "Save to Firestore failed:" e)
                            (js/alert (str "Save failed:\n" e)))))}
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
   [pretty-print @*current-data]
   [:h2 "Pending changes"]
   [pretty-print (pending-changes)]
   [:p
    [load-samples-button] " "
    [load-firestore-button] " "
    [save-firestore-button]]])


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
  (p/do
    (firebase/close!)
    (init!)))
