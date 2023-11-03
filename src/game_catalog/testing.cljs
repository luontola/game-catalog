(ns game-catalog.testing
  (:require [cljs.test :as test]
            [promesa.core :as p]))

(defn async [p]
  (test/async done
    (-> p
        (p/catch (fn [error]
                   (test/do-report
                     {:type :error
                      :message "Uncaught exception, not in assertion."
                      :expected nil
                      :actual error})))
        (p/finally (fn [_ _]
                     (done))))))
