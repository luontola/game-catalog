(ns game-catalog.ui.spreadsheet.select-test
  (:require [clojure.test :refer :all]
            [game-catalog.testing.html :as html]
            [game-catalog.ui.spreadsheet.select :as select])
  (:import (org.jsoup.nodes Element)))

(def column {:column/entity-key :thing/foo
             :column/options ["" "Foo" "Bar" "Gazonk"]})

(deftest viewer-test
  (testing "renders select values"
    (is (= "Bar"
           (html/visualize-html (str (select/viewer {:value "Bar"})))))
    (is (= ""
           (html/visualize-html (str (select/viewer {:value nil})))))))

(defn- option-values [^Element select]
  (->> (.select select "option")
       (mapv (fn [^Element option]
               (.attr option "value")))))

(defn- selected-option [^Element select]
  (.text (.select select "option[selected]")))

(deftest editor-test
  (testing "renders select editor"
    (let [select (-> (select/editor {:column column
                                     :value "Bar"
                                     :form-id "things-form-1"
                                     :focus? true})
                     (html/parse-fragment)
                     (.selectFirst "select"))]
      (is (= "things-form-1" (.attr select "form")))
      (is (= "thing/foo" (.attr select "name")))
      (is (= "[Bar]" (.attr select "data-test-content")))
      (is (= ["" "Foo" "Bar" "Gazonk"] (option-values select)))
      (is (= "Bar" (selected-option select)))))

  (testing "preserves unknown existing values as selectable options"
    (let [select (-> (select/editor {:column column
                                     :value "Quux"
                                     :form-id "things-form-1"})
                     (html/parse-fragment)
                     (.selectFirst "select"))]
      (is (= ["" "Foo" "Bar" "Gazonk" "Quux"] (option-values select)))
      (is (= "Quux" (selected-option select))))))

(deftest parse-form-params-test
  (testing "parses submitted select values"
    (is (= {:thing/foo "Bar"}
           (select/parse-form-params {"thing/foo" "Bar"} column))))

  (testing "parses blank values as blank strings"
    (is (= {:thing/foo ""}
           (select/parse-form-params {"thing/foo" ""} column))))

  (testing "ignores missing inputs"
    (is (nil? (select/parse-form-params {} column)))))
