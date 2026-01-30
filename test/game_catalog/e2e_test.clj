(ns ^:slow game-catalog.e2e-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.testing.browser :as browser]
            [game-catalog.testing.html :as html])
  (:import (com.microsoft.playwright.assertions PlaywrightAssertions)))

(use-fixtures :once browser/fixture)

(deftest home-page-test
  (testing "home page loads and displays content"
    (browser/navigate! "/")
    (is (str/includes? (.textContent browser/*page* "body") "hello world"))

    (.click (browser/locator "text=Click Me"))
    (.isVisible (PlaywrightAssertions/assertThat (browser/locator "text=Clicked at 20")))))

(deftest games-page-test
  (db/init-collection! :games
                       [{:entity/id "1"
                         :game/name "The Legend of Zelda: Breath of the Wild"
                         :game/release "2017"
                         :game/series "Zelda"
                         :game/status "Completed"}
                        {:entity/id "2"
                         :game/name "Portal 2"
                         :game/release "2011"
                         :game/series "Portal"
                         :game/status "Playing"}
                        {:entity/id "3"
                         :game/name "Hollow Knight"
                         :game/release "2017"
                         :game/tags "Metroidvania, Indie"
                         :game/status "Backlog"}])

  (testing "games page is accessible"
    (is (= 200 (-> (.request browser/*page*)
                   (.get (str browser/*base-url* "/games"))
                   (.status)))))

  (testing "games page displays table with game data"
    (browser/navigate! "/games")
    (let [table (browser/locator "table")]
      ;; row numbers are CSS-generated (::before), so they don't appear in DOM text
      (is (= (html/normalize-whitespace "
             #  Name                                     Release  Remake  Series  Tags                 Purchases  Status     Content  DLCs
                Hollow Knight                            2017                     Metroidvania, Indie             Backlog
                Portal 2                                 2011             Portal                                  Playing
                The Legend of Zelda: Breath of the Wild  2017             Zelda                                   Completed
                []                                       []       []      []      []                   []         []         []       []")
             (html/visualize-html table))))))
