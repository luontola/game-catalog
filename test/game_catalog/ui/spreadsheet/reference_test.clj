(ns game-catalog.ui.spreadsheet.reference-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.infra.json :as json]
            [game-catalog.testing.html :as html]
            [game-catalog.ui.spreadsheet.reference :as reference]
            [mount.core :as mount])
  (:import (org.jsoup.nodes Document Element)))

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

(defn- render-viewer ^Document [ctx]
  (html/parse-fragment
    (reference/viewer (merge {:config config, :column column, :mode :view} ctx))))

(defn- links [^Document doc]
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
      (is (empty? (links doc)))))

  (testing "the picker shows plain text without links, because it's used in edit mode"
    (let [doc (render-viewer {:value ["w1" "w2"], :mode :pick})]
      (is (= "Bar, Foo" (html/visualize-html (str (.body doc)))))
      (is (empty? (links doc))))))

(deftest value-test
  (testing "forward references are read from the entity"
    (is (= ["w1"] (reference/value {:column column
                                    :entity {:thing/widgets ["w1"]}}))))

  (testing "back references are computed from the target collection,"
    (db/init-collection! :things [{:entity/id "t1", :thing/widgets ["w1" "w2"], :thing/widget "w1"}
                                  {:entity/id "t2", :thing/widgets ["w2"], :thing/widget "w2"}
                                  {:entity/id "t3"}])
    (let [back-reference-column (assoc reference/column-defaults
                                  :column/entity-key :widget/things
                                  :column/target-collection :things
                                  :column/read-only? true)
          back-references (fn [back-reference widget-id]
                            (->> (reference/value {:column (assoc back-reference-column
                                                             :column/back-reference back-reference)
                                                   :entity {:entity/id widget-id}})
                                 sort))]
      (testing "from multiple references"
        (is (= ["t1"] (back-references :thing/widgets "w1")))
        (is (= ["t1" "t2"] (back-references :thing/widgets "w2")))
        (is (= [] (back-references :thing/widgets "w3"))))

      (testing "from single references"
        (is (= ["t1"] (back-references :thing/widget "w1")))
        (is (= ["t2"] (back-references :thing/widget "w2")))
        (is (= [] (back-references :thing/widget "w3")))))))

(defn- render-editor ^Document [ctx]
  (html/parse-fragment
    (reference/editor (merge {:config config, :column column, :form-id "things-form-1"} ctx))))

(defn- pills [^Document doc]
  (->> (.select doc ".pill")
       (mapv (fn [^Element pill]
               (let [input (.selectFirst pill "input[type=hidden]")]
                 (cond-> {:value (.attr input "value")
                          :text (.text pill)}
                   (.hasAttr pill "title") (assoc :title (.attr pill "title"))))))))

(defn- icons [^Document doc]
  (->> (.select doc ".pill-button [role=img]")
       (mapv (fn [^Element icon]
               (.attr icon "aria-label")))))

(deftest editor-test
  (testing "multiple references:"
    (let [doc (render-editor {:value ["w3" "w1"], :focus? true})
          hidden (.selectFirst doc ".reference-editor > input[type=hidden]")
          button (.selectFirst doc ".pill-button")]
      (testing "an empty hidden value lets removing all references be saved"
        (is (= "things-form-1" (.attr hidden "form")))
        (is (= "thing/widgets" (.attr hidden "name")))
        (is (= "" (.attr hidden "value"))))

      (testing "the referred entities are pills, in the target collection's order"
        (is (= [{:value "w1", :text "Foo ×", :title "Size 1"}
                {:value "w3", :text "Gazonk ×"}]
               (pills doc)))
        (is (= ["things-form-1" "things-form-1"]
               (mapv #(.attr ^Element % "form") (.select doc ".pill input"))))
        (is (= ["thing/widgets" "thing/widgets"]
               (mapv #(.attr ^Element % "name") (.select doc ".pill input")))))

      (testing "each pill has a remove button"
        (is (= ["Remove Foo" "Remove Gazonk"]
               (mapv #(.attr ^Element % "aria-label") (.select doc ".pill-remove"))))
        (is (= ["button" "button"]
               (mapv #(.attr ^Element % "type") (.select doc ".pill-remove")))))

      (testing "the add button is the entry point, so it gets the focus"
        (is (.hasClass button "editor-entry-point"))
        (is (.hasAttr button "autofocus"))
        (is (= "button" (.attr button "type")))
        (is (= ["Add"] (icons doc))))))

  (testing "no references"
    (let [doc (render-editor {:value []})]
      (is (empty? (pills doc)))
      (is (not (.hasAttr (.selectFirst doc ".pill-button") "autofocus")))))

  (testing "single reference has also a replace icon, and CSS shows only one of them"
    (let [doc (render-editor {:column single-column, :value "w2"})]
      (is (= "false" (.attr (.selectFirst doc ".reference-editor") "data-multiple")))
      (is (= [{:value "w2", :text "Bar ×"}] (pills doc)))
      (is (= ["Add" "Replace"] (icons doc)))))

  (testing "a blank label is replaced with a placeholder"
    (is (= [{:value "w4", :text "(no label) ×"}]
           (pills (render-editor {:value ["w4"]})))))

  (testing "missing entities are kept as pills with their IDs, so that saving will not lose them"
    (let [doc (render-editor {:value ["w404" "w1"]})]
      (is (= [{:value "w1", :text "Foo ×", :title "Size 1"}
              {:value "w404", :text "w404 ×", :title "Missing reference"}]
             (pills doc)))
      (is (.hasClass (.selectFirst doc ".pill:last-of-type") "missing")))))

(deftest search-test
  (let [ctx {:config config, :column column}
        search (fn [query exclude-ids]
                 (update (reference/search ctx query exclude-ids) :entities #(mapv :entity/id %)))]
    (testing "an empty query matches all entities, in the target collection's order"
      (is (= {:entities ["w4" "w2" "w1" "w3"], :more 0}
             (search "" nil))))

    (testing "matches a part of the label, ignoring case and surrounding whitespace"
      (is (= {:entities ["w1"], :more 0}
             (search " fO " nil)))
      (is (= {:entities ["w3"], :more 0}
             (search "zon" nil))))

    (testing "excludes the already referred entities"
      (is (= {:entities ["w4" "w3"], :more 0}
             (search "" ["" "w1" "w2"]))))

    (testing "no matches"
      (is (= {:entities [], :more 0}
             (search "xyz" nil))))

    (testing "returns a limited number of matches, and the number of the others"
      (with-redefs [reference/search-limit 3]
        (is (= {:entities ["w4" "w2" "w1"], :more 1}
               (search "" nil)))))))

(deftest create-button-test
  (let [ctx {:config config, :column column, :form-id "things-form-1"}]
    (testing "creates a target entity with the query as its label"
      (let [button (.selectFirst (html/parse-fragment (reference/create-button ctx " Qux ")) "button")]
        (is (= "Create Qux" (.text button)))
        (is (= "/spreadsheet/things/reference-create" (.attr button "data-url")))
        (is (= {:column "thing/widgets", :form "things-form-1", :q "Qux"}
               (json/read-value (.attr button "hx-vals"))))
        (is (= {:widget/name "Qux"}
               (reference/new-entity ctx " Qux "))))))

  (testing "no button for a blank query"
    (is (nil? (reference/create-button {:config config, :column column} " "))))

  (testing "no button when the label is computed by a function"
    (let [config (assoc-in config [:target-configs :widgets :reference-label] (comp str :widget/size))]
      (is (not (reference/creatable? {:config config, :column column})))
      (is (nil? (reference/create-button {:config config, :column column} "Qux"))))))

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
    (is (= {:thing/widgets "w1"}
           (reference/parse-form-params {"thing/widgets" ["" "w1"]} single-column)))
    (is (= {:thing/widgets nil}
           (reference/parse-form-params {"thing/widgets" ""} single-column))))

  (testing "no change when the field is not in the form"
    (is (nil? (reference/parse-form-params {} column)))))
