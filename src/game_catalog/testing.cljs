(ns game-catalog.testing
  (:require [cljs.test :as test]
            [kitchen-async.promise :as p]))

(defn async [promise]
  (test/async done
    (p/try
      promise
      (p/catch :default error
        (test/do-report
          {:type :error
           :message "Uncaught exception, not in assertion."
           :expected nil
           :actual error}))
      (p/finally
        (done)))))
