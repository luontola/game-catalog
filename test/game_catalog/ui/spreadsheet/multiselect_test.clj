(ns game-catalog.ui.spreadsheet.multiselect-test
  (:require [clojure.test :refer :all]
            [game-catalog.testing.html :as html]
            [game-catalog.ui.spreadsheet.multiselect :as multiselect])
  (:import (org.jsoup.nodes Element)))

(def column {:column/entity-key :thing/foo
             :column/options ["Foo" "Bar" "Gazonk"]})

(deftest viewer-test
  (testing "renders selected values"
    (is (= "Foo, Bar"
           (html/visualize-html (str (multiselect/viewer {:value ["Foo" "Bar"]})))))
    (is (= ""
           (html/visualize-html (str (multiselect/viewer {:value nil}))))))

  (testing "renders scalar values"
    (is (= "Foo"
           (html/visualize-html (str (multiselect/viewer {:value "Foo"})))))))

(defn- option-values [^Element select]
  (->> (.select select "option")
       (mapv (fn [^Element option]
               (.attr option "value")))))

(defn- selected-option-values [^Element select]
  (->> (.select select "option[selected]")
       (mapv (fn [^Element option]
               (.attr option "value")))))

(deftest editor-test
  (testing "renders multiselect editor"
    (let [doc (html/parse-fragment
                (multiselect/editor {:column column
                                     :value ["Bar" "Gazonk"]
                                     :form-id "things-form-1"
                                     :focus? true}))
          hidden (.selectFirst doc "input[type=hidden]")
          select (.selectFirst doc "select")]
      (is (= "things-form-1" (.attr hidden "form")))
      (is (= "thing/foo" (.attr hidden "name")))
      (is (= "" (.attr hidden "value")))

      (is (= "things-form-1" (.attr select "form")))
      (is (= "thing/foo" (.attr select "name")))
      (is (= "[[\"Bar\" \"Gazonk\"]]" (.attr select "data-test-content")))
      (is (= ["Foo" "Bar" "Gazonk"] (option-values select)))
      (is (= ["Bar" "Gazonk"] (selected-option-values select)))))

  (testing "preserves unknown existing values as selectable options"
    (let [select (-> (multiselect/editor {:column column
                                          :value ["Quux" "Bar"]
                                          :form-id "things-form-1"})
                     (html/parse-fragment)
                     (.selectFirst "select"))]
      (is (= ["Foo" "Bar" "Gazonk" "Quux"] (option-values select)))
      (is (= ["Bar" "Quux"] (selected-option-values select))))))

(deftest parse-form-params-test
  (testing "parses submitted multiselect values"
    (is (= {:thing/foo ["Foo" "Bar"]}
           (multiselect/parse-form-params {"thing/foo" ["Foo" "Bar"]} column))))

  (testing "parses scalar values as one selected value"
    (is (= {:thing/foo ["Foo"]}
           (multiselect/parse-form-params {"thing/foo" "Foo"} column))))

  (testing "parses blank values as an empty vector"
    (is (= {:thing/foo []}
           (multiselect/parse-form-params {"thing/foo" ""} column))))

  (testing "ignores hidden blank values when options are selected"
    (is (= {:thing/foo ["Foo" "Bar"]}
           (multiselect/parse-form-params {"thing/foo" ["" "Foo" "Bar"]} column))))

  (testing "ignores missing inputs"
    (is (nil? (multiselect/parse-form-params {} column)))))
