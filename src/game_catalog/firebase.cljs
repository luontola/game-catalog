(ns game-catalog.firebase
  (:require ["firebase/analytics" :as analytics]
            ["firebase/app" :as app]
            ["firebase/auth" :as auth]
            ["firebase/firestore" :as firestore]
            [promesa.core :as p]
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
     :analytics (analytics/getAnalytics app)
     :firestore (firestore/getFirestore app)}))

(defn init-emulator []
  (let [ctx (init-prod)]
    (auth/connectAuthEmulator (:auth ctx) "http://127.0.0.1:9099")
    (firestore/connectFirestoreEmulator (:firestore ctx) "127.0.0.1" 9098)
    (assoc ctx :emulator? true)))

(defn init! []
  (set! *ctx* (if firebase-emulator?
                (init-emulator)
                (init-prod)))
  (auth/onAuthStateChanged (:auth *ctx*)
                           (fn [user]
                             (reset! *user user))))

(defn sign-in! []
  (auth/signInWithRedirect (:auth *ctx*) auth-provider))

(defn sign-out! []
  (.signOut (:auth *ctx*)))

#_(p/let [collectionRef (firestore/collection (:firestore *ctx*) "test")
          _ (js/console.log "collectionRef" collectionRef)
          docs-snapshot (firestore/getDocs collectionRef)
          #_#_docRef (firestore/addDoc collectionRef #js {:first "Alan2",
                                                          :middle "Mathison",
                                                          :last "Turing",
                                                          :born 1912})]
    #_(js/console.log "docs" docs-snapshot)
    (doseq [doc (.-docs docs-snapshot)]
      (js/console.log "doc" (.-id doc) (.data doc)))
    #_(prn 'docRef docRef)
    #_(js/console.log docRef))
