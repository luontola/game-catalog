(ns game-catalog.data.catalog-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.catalog :as catalog]))

(def games
  [{:entity/id "g1", :game/name "Game One", :game/purchases "Steam", :game/dlcs "DLC One"}
   {:entity/id "g2", :game/name "Game, Two "}])

(def dlcs
  [{:entity/id "d1", :dlc/name "DLC One", :dlc/base-game "Game One", :dlc/purchases "Steam"}
   {:entity/id "d2", :dlc/name "DLC Two", :dlc/base-game ""}])

(def purchases
  [{:entity/id "p1", :purchase/base-games "Game One,\"Game, Two\"", :purchase/dlcs "DLC One"}
   {:entity/id "p2", :purchase/base-games "", :purchase/dlcs ""}])

(defn- thrown-message [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo e
      (ex-message e))))

(deftest resolve-references-test
  (let [result (catalog/resolve-references {:games games, :dlcs dlcs, :purchases purchases})]
    (testing "resolves purchase references to vectors of IDs"
      (is (= [{:entity/id "p1", :purchase/base-games ["g1" "g2"], :purchase/dlcs ["d1"]}
              {:entity/id "p2", :purchase/base-games [], :purchase/dlcs []}]
             (:purchases result))))

    (testing "resolves the DLC base game to a single ID"
      (is (= [{:entity/id "d1", :dlc/name "DLC One", :dlc/base-game "g1"}
              {:entity/id "d2", :dlc/name "DLC Two", :dlc/base-game nil}]
             (:dlcs result))))

    (testing "removes the back reference columns, because they are computed on read"
      (is (= [{:entity/id "g1", :game/name "Game One"}
              {:entity/id "g2", :game/name "Game, Two "}]
             (:games result)))))

  (testing "a quoted DLC base game is a single name"
    (is (= "g2" (-> (catalog/resolve-references {:games games
                                                 :dlcs [{:entity/id "d1", :dlc/base-game "\"Game, Two\""}]
                                                 :purchases []})
                    :dlcs first :dlc/base-game))))

  (testing "throws on more than one DLC base game"
    (is (= "Expected at most one name in :dlc/base-game: \"Game One,Game One\""
           (thrown-message #(catalog/resolve-references {:games games
                                                         :dlcs [{:entity/id "d1", :dlc/base-game "Game One,Game One"}]
                                                         :purchases []})))))

  (testing "throws on unknown names"
    (is (= "Unknown name in :purchase/dlcs: \"No Such DLC\""
           (thrown-message #(catalog/resolve-references {:games games
                                                         :dlcs dlcs
                                                         :purchases [{:entity/id "p1", :purchase/dlcs "No Such DLC"}]})))))

  (testing "throws on ambiguous names"
    (is (= "Ambiguous name in :purchase/base-games: \"Game One\""
           (thrown-message #(catalog/resolve-references {:games (conj games {:entity/id "g3", :game/name "Game One"})
                                                         :dlcs []
                                                         :purchases [{:entity/id "p1", :purchase/base-games "Game One"}]})))))

  (testing "duplicate names are allowed when nothing refers to them"
    (is (some? (catalog/resolve-references {:games (conj games {:entity/id "g3", :game/name "Game One"})
                                            :dlcs []
                                            :purchases []})))))

(deftest read-catalog-from-csv-test
  (testing "all references in the CSV data can be resolved"
    (let [{:keys [games dlcs purchases]} (catalog/read-catalog-from-csv)]
      (is (seq games))
      (is (seq dlcs))
      (is (seq purchases)))))
