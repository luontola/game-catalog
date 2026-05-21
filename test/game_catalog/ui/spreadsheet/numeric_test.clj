(ns game-catalog.ui.spreadsheet.numeric-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [game-catalog.testing.html :as html]
            [game-catalog.ui.spreadsheet.numeric :as numeric]))

(deftest viewer-test
  (testing "renders numeric values"
    (let [viewer (:column/viewer numeric/column-defaults)]
      (is (= "1986"
             (html/visualize-html (str (viewer {:value 1986})))))
      (is (= ""
             (html/visualize-html (str (viewer {:value nil}))))))))

(deftest editor-test
  (testing "renders numeric editor input"
    (let [editor (:column/editor numeric/column-defaults)
          html (str (editor {:column {:column/entity-key :game/release}
                             :value 2017
                             :form-id "games-form-1"
                             :focus? true}))]
      (is (str/includes? html " type=\"number\""))
      (is (str/includes? html " form=\"games-form-1\""))
      (is (str/includes? html " name=\"game/release\""))
      (is (str/includes? html " value=\"2017\""))
      (is (str/includes? html " data-test-content=\"[2017]\"")))))

(deftest parse-form-params-test
  (testing "parses submitted numeric values"
    (is (= {:game/release 2017}
           (numeric/parse-form-params {"game/release" "2017"}
                                      {:column/entity-key :game/release}))))

  (testing "parses blank values as nil"
    (is (= {:game/release nil}
           (numeric/parse-form-params {"game/release" ""}
                                      {:column/entity-key :game/release}))))

  (testing "ignores missing inputs"
    (is (nil? (numeric/parse-form-params {}
                                         {:column/entity-key :game/release})))))
