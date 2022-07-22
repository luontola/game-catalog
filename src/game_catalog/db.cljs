(ns game-catalog.db
  (:require [clojure.data :as data]
            [clojure.set :as set]))

(defn- keys2 [m]
  (set (for [[k1 m] m
             [k2 _] m]
         [k1 k2])))

(defn diff [before after]
  (let [[only-before only-after _] (data/diff before after)]
    (doall (for [ks (sort (set/union (keys2 only-after)
                                     (keys2 only-before)))]
             (conj ks (get-in after ks))))))
