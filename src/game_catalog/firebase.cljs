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

(def app (app/initializeApp (clj->js firebase-config)))
(def auth (auth/getAuth app))
(def analytics (analytics/getAnalytics app))
(def db (firestore/getFirestore app))

(prn 'app app)
(prn 'auth auth)
(prn 'analytics analytics)
(prn 'db db)

(def *user (r/atom :loading))

(auth/onAuthStateChanged auth (fn [user]
                                (js/console.log "onAuthStateChanged" user)
                                (js/console.log "xxx" auth)
                                (reset! *user user)))
(def auth-provider (auth/GoogleAuthProvider.))
(prn 'auth-provider auth-provider)

(p/catch
  (p/let [result (auth/getRedirectResult auth)]
    (js/console.log "getRedirectResult" result))
  (fn [error]
    (js/console.log "getRedirectResult-error" error)))

(defn sign-in! []
  (auth/signInWithRedirect auth auth-provider))

(defn sign-out! []
  (.signOut auth))

#_(p/let [response (js/fetch "https://httpbin.org/uuid")
          data (.json response)]
    (prn 'xxx data))

#_(p/let [collectionRef (firestore/collection db "test")
          docRef (firestore/addDoc collectionRef #js {:first "Alan",
                                                      :middle "Mathison",
                                                      :last "Turing",
                                                      :born 1912})]
    (prn 'collectionRef collectionRef)
    (js/console.log collectionRef)
    (prn 'docRef docRef)
    (js/console.log docRef))

;; TODO: Firebase Local Emulator Suite https://firebase.google.com/docs/auth/web/start
