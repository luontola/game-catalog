(ns ^:slow game-catalog.ui.spreadsheet-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.infra.html :as html]
            [game-catalog.testing.browser :as browser]
            [game-catalog.testing.html :as test-html]
            [game-catalog.ui.layout :as layout]
            [game-catalog.ui.routes :as routes]
            [game-catalog.ui.spreadsheet :as spreadsheet]
            [reitit.ring :as ring]
            [ring.util.http-response :as http-response]))

(def things-config
  {:collection-key :things
   :sort-by (comp clojure.string/lower-case :thing/name)
   :columns [{:column/name "#"
              :column/entity-key :entity/id
              :column/read-only? true}
             {:column/name "Name"
              :column/entity-key :thing/name}
             {:column/name "Color"
              :column/entity-key :thing/color}
             {:column/name "Size"
              :column/entity-key :thing/size}]})

(defn things-page-handler [_request]
  (-> (spreadsheet/table things-config)
      (layout/page)
      (html/response)))

(def test-routes
  [["/things"
    {:get {:handler things-page-handler}}]
   (spreadsheet/make-routes things-config)])

(defn test-fixture [f]
  (with-redefs [routes/ring-handler (ring/ring-handler
                                      (ring/router test-routes)
                                      (constantly (http-response/not-found "Not found")))]
    (browser/fixture f)))

(use-fixtures :once test-fixture)


(deftest spreadsheet-test
  (do
    (db/init-collection! :things
                         [{:entity/id "1"
                           :thing/name "Apple"
                           :thing/color "Red"
                           :thing/size "8"}
                          {:entity/id "2"
                           :thing/name "Banana"
                           :thing/color "Yellow"
                           :thing/size "20"}
                          {:entity/id "3"
                           :thing/name "Car"
                           :thing/color "Blue"
                           :thing/size "450"}])
    (browser/navigate! "/things"))

  (testing "renders spreadsheet table"
    (let [table (browser/locator "table")]
      (is (= (test-html/normalize-whitespace "
             #  Name    Color   Size
             1  Apple   Red     8
             2  Banana  Yellow  20
             3  Car     Blue    450
                []      []      []")
             (test-html/visualize-html table))))))
