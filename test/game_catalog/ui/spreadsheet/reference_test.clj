(ns game-catalog.ui.spreadsheet.reference-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.testing.html :as html]
            [game-catalog.ui.spreadsheet.reference :as reference]
            [mount.core :as mount])
  (:import (org.jsoup.nodes Element)))

(def widgets-config
  {:collection-key :widgets
   :sort-by :widget/name
   :reference-label :widget/name
   :reference-tooltip (fn [widget]
                        (some->> (:widget/size widget)
                                 (str "Size ")))})

(def config
  {:collection-key :things
   :target-configs {:widgets widgets-config}})

(def column
  (assoc reference/column-defaults
    :column/entity-key :thing/widgets
    :column/target-collection :widgets))

(def single-column
  (assoc column :column/multiple? false))

(defn reset-collections-fixture [f]
  (mount/start #'db/*collections)
  (try
    (db/init-collection! :widgets [{:entity/id "w1", :widget/name "Foo", :widget/size 1}
                                   {:entity/id "w2", :widget/name "Bar"}
                                   {:entity/id "w3", :widget/name "Gazonk"}
                                   {:entity/id "w4", :widget/name ""}])
    (f)
    (finally
      (mount/stop))))

(use-fixtures :each reset-collections-fixture)

(defn- render-viewer [ctx]
  (html/parse-fragment
    (reference/viewer (merge {:config config, :column column, :mode :view} ctx))))

(defn- links [doc]
  (->> (.select doc "a")
       (mapv (fn [^Element a]
               (cond-> {:href (.attr a "href")
                        :text (.text a)}
                 (.hasAttr a "title") (assoc :title (.attr a "title")))))))

(deftest viewer-test
  (testing "renders the referred entities as links, in the target collection's order"
    (let [doc (render-viewer {:value ["w1" "w2"]})]
      (is (= "Bar, Foo" (html/visualize-html (str (.body doc)))))
      (is (= [{:href "/widgets#w2", :text "Bar"}
              {:href "/widgets#w1", :text "Foo", :title "Size 1"}]
             (links doc)))))

  (testing "renders a single ID"
    (is (= "Bar" (html/visualize-html (str (reference/viewer {:config config, :column single-column, :value "w2"}))))))

  (testing "renders nothing for no references"
    (is (= "" (html/visualize-html (str (reference/viewer {:config config, :column column, :value nil})))))
    (is (= "" (html/visualize-html (str (reference/viewer {:config config, :column column, :value []}))))))

  (testing "a blank label is replaced with a placeholder, so that the link is visible"
    (is (= [{:href "/widgets#w4", :text "(no label)"}]
           (links (render-viewer {:value ["w4"]})))))

  (testing "missing entities are shown as their IDs, after the found entities"
    (let [doc (render-viewer {:value ["w404" "w1"]})]
      (is (= "Foo, w404" (html/visualize-html (str (.body doc)))))
      (is (= "w404" (.text (.selectFirst doc "span.missing"))))))

  (testing "edit mode shows plain text without links, so that unsaved changes are not lost"
    (let [doc (render-viewer {:value ["w1" "w2"], :mode :edit})]
      (is (= "Bar, Foo" (html/visualize-html (str (.body doc)))))
      (is (empty? (links doc))))))

(defn- options [^Element select]
  (->> (.select select "option")
       (mapv (fn [^Element option]
               [(.attr option "value") (.text option)]))))

(defn- selected-option-values [^Element select]
  (->> (.select select "option[selected]")
       (mapv (fn [^Element option]
               (.attr option "value")))))

(defn- render-editor [ctx]
  (html/parse-fragment
    (reference/editor (merge {:config config, :column column, :form-id "things-form-1"} ctx))))

(deftest editor-test
  (testing "multiple references:"
    (let [doc (render-editor {:value ["w1" "w3"], :focus? true})
          hidden (.selectFirst doc "input[type=hidden]")
          select (.selectFirst doc "select")]
      (testing "an empty hidden value lets clearing all selections be saved"
        (is (= "things-form-1" (.attr hidden "form")))
        (is (= "thing/widgets" (.attr hidden "name")))
        (is (= "" (.attr hidden "value"))))

      (testing "options are the target collection's entities, in its order"
        (is (.hasClass select "editor-entry-point"))
        (is (.hasAttr select "multiple"))
        (is (= "thing/widgets" (.attr select "name")))
        (is (= [["w4" "(no label)"] ["w2" "Bar"] ["w1" "Foo"] ["w3" "Gazonk"]]
               (options select)))
        (is (= ["w1" "w3"] (selected-option-values select))))))

  (testing "single reference:"
    (let [doc (render-editor {:column single-column, :value "w2"})
          select (.selectFirst doc "select")]
      (testing "has no hidden input, because a single select is always submitted"
        (is (nil? (.selectFirst doc "input[type=hidden]"))))

      (testing "has an empty option for no reference"
        (is (not (.hasAttr select "multiple")))
        (is (= [["" ""] ["w4" "(no label)"] ["w2" "Bar"] ["w1" "Foo"] ["w3" "Gazonk"]]
               (options select)))
        (is (= ["w2"] (selected-option-values select))))))

  (testing "missing entities are kept as options, so that saving will not lose them"
    (let [select (.selectFirst (render-editor {:value ["w404" "w1"]}) "select")]
      (is (= ["w404" "w404"] (last (options select))))
      (is (= ["w1" "w404"] (selected-option-values select))))))

(deftest parse-form-params-test
  (testing "multiple references"
    (is (= {:thing/widgets ["w1" "w2"]}
           (reference/parse-form-params {"thing/widgets" ["" "w1" "w2"]} column)))
    (is (= {:thing/widgets ["w1"]}
           (reference/parse-form-params {"thing/widgets" "w1"} column)))
    (is (= {:thing/widgets []}
           (reference/parse-form-params {"thing/widgets" ""} column))))

  (testing "single reference"
    (is (= {:thing/widgets "w1"}
           (reference/parse-form-params {"thing/widgets" "w1"} single-column)))
    (is (= {:thing/widgets nil}
           (reference/parse-form-params {"thing/widgets" ""} single-column))))

  (testing "no change when the field is not in the form"
    (is (nil? (reference/parse-form-params {} column)))))
