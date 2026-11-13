(ns game-catalog.webapp-test
  (:require [clojure.test :refer :all]
            [game-catalog.webapp :as webapp]
            [ring.mock.request :as mock]))

(deftest anti-forgery-test
  (let [app (webapp/wrap-base (constantly {:status 200
                                           :headers {}
                                           :body "OK"}))]

    (testing "safe requests need no anti-forgery token"
      (is (= 200 (:status (app (mock/request :get "/foo"))))))

    (testing "unsafe requests without a valid anti-forgery token get an error message as plain text, which the frontend can show"
      (let [response (app (mock/request :post "/foo"))]
        (is (= 403 (:status response)))
        (is (= "text/plain; charset=utf-8" (get-in response [:headers "Content-Type"])))
        (is (= "The session has expired. Reload the page to continue." (:body response)))))))
