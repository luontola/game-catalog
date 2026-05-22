(ns game-catalog.ui.spreadsheet.text-test
  (:require [clojure.test :refer :all]
            [game-catalog.testing.html :as html]
            [game-catalog.ui.spreadsheet.text :as text]))

(def column {:column/entity-key :thing/foo})

(deftest viewer-test
  (testing "renders text values"
    (is (= "gazonk"
           (html/visualize-html (str (text/viewer {:value "gazonk"})))))
    (is (= ""
           (html/visualize-html (str (text/viewer {:value nil})))))))

(deftest editor-test
  (testing "renders text editor input"
    (let [input (-> (text/editor {:column column
                                  :value "gazonk"
                                  :form-id "things-form-1"
                                  :focus? true})
                    (html/parse-fragment)
                    (.selectFirst "input"))]
      (is (= "text" (.attr input "type")))
      (is (= "things-form-1" (.attr input "form")))
      (is (= "thing/foo" (.attr input "name")))
      (is (= "gazonk" (.attr input "value")))
      (is (= "[gazonk]" (.attr input "data-test-content"))))))

(deftest parse-form-params-test
  (testing "parses submitted text values"
    (is (= {:thing/foo "gazonk"}
           (text/parse-form-params {"thing/foo" "gazonk"} column))))

  (testing "parses blank values as blank strings"
    (is (= {:thing/foo ""}
           (text/parse-form-params {"thing/foo" ""} column))))

  (testing "ignores missing inputs"
    (is (nil? (text/parse-form-params {} column)))))
