(ns game-catalog.data.catalog-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.baserow :as baserow]
            [game-catalog.data.catalog :as catalog]))

(def config
  {:columns [{:column/entity-key :game/name}
             {:column/entity-key :game/status
              :column/options ["" "Backlog" "Completed"]}
             {:column/entity-key :game/tags
              :column/options ["VR" "PvP"]}]})

(defn- thrown-message [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo e
      (ex-message e))))

(deftest validate-options-test
  (testing "accepts known options and empty values"
    (is (nil? (catalog/validate-options! config [{:game/name "Game One", :game/status "Backlog", :game/tags ["VR" "PvP"]}
                                                 {:game/name "Game Two", :game/status nil, :game/tags []}
                                                 {:game/name "Game Three"}]))))

  (testing "throws on unknown single option"
    (is (= "Unknown option in :game/status: \"Dropped\""
           (thrown-message #(catalog/validate-options! config [{:game/status "Dropped"}])))))

  (testing "throws on unknown multiple option"
    (is (= "Unknown option in :game/tags: \"LAN Co-Op\""
           (thrown-message #(catalog/validate-options! config [{:game/tags ["VR" "LAN Co-Op"]}]))))))

(deftest read-catalog-test
  (testing "the Baserow export can be imported"
    (let [{:keys [games dlcs purchases]} (catalog/read-catalog (baserow/read-export (baserow/find-export-file "data")))
          game-ids (set (map :entity/id games))
          dlc-ids (set (map :entity/id dlcs))]
      (is (seq games))
      (is (seq dlcs))
      (is (seq purchases))

      (testing "- all references point to existing entities"
        (is (every? game-ids (keep :dlc/base-game dlcs)))
        (is (every? game-ids (mapcat :purchase/base-games purchases)))
        (is (every? dlc-ids (mapcat :purchase/dlcs purchases)))))))
