(ns game-catalog.ui.spreadsheet.numeric-test
  (:require [clojure.test :refer :all]
            [game-catalog.testing.html :as html]
            [game-catalog.ui.spreadsheet.numeric :as numeric]))

(def column {:column/entity-key :thing/foo})

(deftest viewer-test
  (testing "renders numeric values"
    (is (= "123"
           (html/visualize-html (str (numeric/viewer {:value 123})))))
    (is (= ""
           (html/visualize-html (str (numeric/viewer {:value nil})))))))

(deftest editor-test
  (testing "renders numeric editor input"
    (let [input (-> (numeric/editor {:column column
                                     :value 123
                                     :form-id "things-form-1"
                                     :focus? true})
                    (html/parse-fragment)
                    (.selectFirst "input"))]
      (is (= "number" (.attr input "type")))
      (is (= "things-form-1" (.attr input "form")))
      (is (= "thing/foo" (.attr input "name")))
      (is (= "123" (.attr input "value")))
      (is (= "[123]" (.attr input "data-test-content"))))))

(deftest parse-form-params-test
  (testing "parses submitted numeric values"
    (is (= {:thing/foo 123}
           (numeric/parse-form-params {"thing/foo" "123"} column))))

  (testing "parses blank values as nil"
    (is (= {:thing/foo nil}
           (numeric/parse-form-params {"thing/foo" ""} column))))

  (testing "ignores missing inputs"
    (is (nil? (numeric/parse-form-params {} column)))))
