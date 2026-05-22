(ns game-catalog.ui.spreadsheet.text-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [game-catalog.testing.html :as html]
            [game-catalog.ui.spreadsheet.text :as text]))

(deftest viewer-test
  (testing "renders text values"
    (let [viewer (:column/viewer text/column-defaults)]
      (is (= "NieR:Automata"
             (html/visualize-html (str (viewer {:value "NieR:Automata"})))))
      (is (= ""
             (html/visualize-html (str (viewer {:value nil}))))))))

(deftest editor-test
  (testing "renders text editor input"
    (let [editor (:column/editor text/column-defaults)
          html (str (editor {:column {:column/entity-key :game/name}
                             :value "NieR:Automata"
                             :form-id "games-form-1"
                             :focus? true}))]
      (is (str/includes? html " type=\"text\""))
      (is (str/includes? html " form=\"games-form-1\""))
      (is (str/includes? html " name=\"game/name\""))
      (is (str/includes? html " value=\"NieR:Automata\""))
      (is (str/includes? html " data-test-content=\"[NieR:Automata]\"")))))

(deftest parse-form-params-test
  (let [column {:column/entity-key :game/name}]
    (testing "parses submitted text values"
      (is (= {:game/name "NieR:Automata"}
             (text/parse-form-params {"game/name" "NieR:Automata"} column))))

    (testing "parses blank values as blank strings"
      (is (= {:game/name ""}
             (text/parse-form-params {"game/name" ""} column))))

    (testing "ignores missing inputs"
      (is (nil? (text/parse-form-params {} column))))))
