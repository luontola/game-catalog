(ns game-catalog.db
  (:require ["firebase/firestore" :as firestore]
            [clojure.data :as data]
            [clojure.set :as set]
            [promesa.core :as p]))

(defn- keys2 [m]
  (set (for [[k1 m] m
             [k2 _] m]
         [k1 k2])))

(defn diff [before after]
  (let [[only-before only-after _] (data/diff before after)]
    (doall (for [ks (sort (set/union (keys2 only-after)
                                     (keys2 only-before)))]
             (conj ks (get-in after ks))))))

(defn read-collection! [db collection]
  (p/let [response (firestore/getDocs (firestore/collection db (name collection)))]
    (->> (.-docs response)
         (map (fn [doc]
                [(.-id doc) (js->clj (.data doc) :keywordize-keys true)]))
         (into {}))))

(defn read-collections! [db collections]
  (p/let [result (->> collections
                      (map (fn [collection]
                             (p/let [docs (read-collection! db collection)]
                               [collection docs])))
                      (p/all))]
    (into {} result)))

(defn write-doc! [db collection id doc]
  (let [doc-ref (firestore/doc db (name collection) id)]
    (if (some? doc)
      (firestore/setDoc doc-ref (clj->js doc))
      (firestore/deleteDoc doc-ref))))

(defn update-collections! [db updates]
  ;; TODO: batch write
  (p/doseq [update updates]
    (let [[collection id doc] update]
      (write-doc! db collection id doc))))
