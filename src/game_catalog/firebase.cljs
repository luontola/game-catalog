(ns game-catalog.firebase
  (:require ["firebase/analytics" :as analytics]
            ["firebase/app" :as app]
            ["firebase/auth" :as auth]
            ["firebase/firestore" :as firestore]
            [kitchen-async.promise :as p]
            [lambdaisland.fetch :as fetch]
            [reagent.core :as r]))

(def firebase-config
  {:apiKey "AIzaSyBvfZ5VodNQsN4szjFcX7pOUXaYsRwhIpI",
   :authDomain "game-catalog-35894.firebaseapp.com",
   :projectId "game-catalog-35894",
   :storageBucket "game-catalog-35894.appspot.com",
   :messagingSenderId "844386070676",
   :appId "1:844386070676:web:2c591e49c1311abab94481",
   :measurementId "G-NSTHFXQB4N"})

(def ^:dynamic *ctx*)
(def *user (r/atom :loading))
(def auth-provider (auth/GoogleAuthProvider.))

(def firebase-emulator? (= "true" (js/localStorage.getItem "firebase-emulator?")))

(defn set-firebase-emulator! [enabled?]
  (js/localStorage.setItem "firebase-emulator?" (str (boolean enabled?)))
  (js/location.reload))

(defn init-prod []
  (let [app (app/initializeApp (clj->js firebase-config))]
    {:app app
     :auth (auth/getAuth app)
     #_#_:analytics (analytics/getAnalytics app)
     :firestore (firestore/getFirestore app)}))

(defn init-emulator
  ([]
   (init-emulator nil))
  ([mock-user-token]
   (let [ctx (init-prod)]
     (auth/connectAuthEmulator (:auth ctx) "http://127.0.0.1:9099")
     (firestore/connectFirestoreEmulator (:firestore ctx) "127.0.0.1" 9098 #js {"mockUserToken" mock-user-token})
     (assoc ctx :emulator? true))))

(defn init! []
  (assert (nil? *ctx*))
  (set! *ctx* (if firebase-emulator?
                (init-emulator)
                (init-prod)))
  (auth/onAuthStateChanged (:auth *ctx*)
                           (fn [user]
                             (reset! *user user))))

(defn close!
  ([]
   (assert (some? *ctx*))
   (close! *ctx*)
   (set! *ctx* nil))
  ([ctx]
   (p/do
     (app/deleteApp (:app ctx)))))

(defn sign-in! []
  (auth/signInWithRedirect (:auth *ctx*) auth-provider))

(defn sign-out!
  ([]
   (assert (some? *ctx*))
   (sign-out! *ctx*))
  ([ctx]
   (.signOut (:auth ctx))))

(defn sign-in-as! [ctx id-token]
  (p/let [cred (.credential auth/GoogleAuthProvider (js/JSON.stringify (clj->js id-token)))
          user (auth/signInWithCredential (:auth ctx) cred)]
    (.. user -user -uid)))

(defn sign-in-as-regular-user! [ctx]
  (sign-in-as! ctx {:sub "regular-user"
                    :email "regular-user@example.com"
                    :email_verified true}))

(defn sign-in-as-editor! [ctx]
  (sign-in-as! ctx {:sub "editor"
                    :email "editor@example.com"
                    :email_verified true}))

(defn empty-firestore-test-database! []
  (p/let [resp (fetch/delete (str "http://localhost:9098/emulator/v1/projects/" (:projectId firebase-config) "/databases/(default)/documents"))]
    (when-not (= 200 (:status resp))
      (throw (ex-info "Failed to empty the Firestore test database" resp)))))
