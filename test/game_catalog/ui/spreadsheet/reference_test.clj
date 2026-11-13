(ns game-catalog.ui.spreadsheet.reference-test
  (:require [clojure.test :refer :all]
            [game-catalog.data.db :as db]
            [game-catalog.infra.hiccup :as h]
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
    (reference/editor (merge {:config config, :entity {:entity/id "t1"}, :column column, :form-id "things-form-1"} ctx))))

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

  (testing "the buttons save the row and render the editor again, through the request queue of the row's form"
    (let [doc (render-editor {:value ["w1"]})
          add-button (.selectFirst doc ".pill-button")
          remove-button (.selectFirst doc ".pill-remove")]
      (is (= "/spreadsheet/things/t1/reference-picker" (.attr add-button "hx-post")))
      (is (= {:column "thing/widgets"} (json/read-value (.attr add-button "hx-vals"))))
      (is (= "/spreadsheet/things/t1/save-reference" (.attr remove-button "hx-post")))
      (is (= {:column "thing/widgets", :remove "w1"} (json/read-value (.attr remove-button "hx-vals"))))
      (doseq [^Element button [add-button remove-button]]
        (is (= "[form='things-form-1']" (.attr button "hx-include")))
        (is (= "#things-form-1:queue last" (.attr button "hx-sync")))
        (is (= "closest .reference-editor" (.attr button "hx-target")))
        (is (= "innerHTML" (.attr button "hx-swap"))))))

  (testing "the focus can go to the remove button of a referred entity"
    (let [doc (render-editor {:value ["w3" "w1"], :focus-item "w3"})]
      (is (= ["things-form-1-thing/widgets-remove-w3"]
             (mapv #(.id ^Element %) (.select doc "[autofocus]"))))))

  (testing "the picker is not in the editor before the user opens it"
    (is (nil? (.selectFirst (render-editor {:value []}) "dialog"))))

  (testing "the open picker comes with the first search results"
    (let [doc (render-editor {:value [], :picker-results (h/raw "<p>Results</p>")})
          dialog (.selectFirst doc ".reference-editor > dialog.reference-picker")
          search (.selectFirst dialog ".reference-search")]
      (is (.hasAttr dialog "auto-show-modal") "the frontend opens it as a modal")
      (is (.hasAttr search "autofocus"))
      (is (= "/spreadsheet/things/t1/reference-search" (.attr search "hx-post")))
      (is (= {:column "thing/widgets"} (json/read-value (.attr search "hx-vals"))))
      (is (= "<p>Results</p>" (.html (.selectFirst dialog ".reference-results"))))))

  (testing "single reference: the button replaces the value instead of adding to it"
    (let [doc (render-editor {:column single-column, :value "w2"})]
      (is (= [{:value "w2", :text "Bar ×"}] (pills doc)))
      (is (= ["Replace"] (icons doc))))
    (is (= ["Add"] (icons (render-editor {:column single-column, :value nil})))))

  (testing "the buttons have IDs, so that the focus can stay on a button when the editor is rendered again"
    (let [doc (render-editor {:value ["w3" "w1"]})]
      (is (= ["things-form-1-thing/widgets-remove-w1"
              "things-form-1-thing/widgets-remove-w3"
              "things-form-1-thing/widgets-add"]
             (mapv #(.id ^Element %) (.select doc "button"))))))

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
  (let [ctx {:config config, :entity {:entity/id "t1"}, :column column, :form-id "things-form-1"}]
    (testing "creates a target entity with the query as its label"
      (let [button (.selectFirst (html/parse-fragment (reference/create-button ctx " Qux " false)) "button")]
        (is (= "Create Qux" (.text button)))
        (is (not (.hasClass button "highlighted")))
        (is (.hasClass button "reference-create"))
        (is (= "/spreadsheet/things/t1/save-reference" (.attr button "hx-post")))
        (is (= {:column "thing/widgets", :create "Qux"} (json/read-value (.attr button "hx-vals"))))
        (is (= {:widget/name "Qux"}
               (reference/new-entity ctx " Qux "))))))

  (testing "the button can be the highlighted option, which the Enter key picks"
    (let [ctx {:config config, :column column, :form-id "things-form-1"}
          button (.selectFirst (html/parse-fragment (reference/create-button ctx "Qux" true)) "button")]
      (is (.hasClass button "reference-create"))
      (is (.hasClass button "highlighted"))))

  (testing "no button for a blank query"
    (is (nil? (reference/create-button {:config config, :column column} " " false))))

  (testing "no button when the label is computed by a function"
    (let [config (assoc-in config [:target-configs :widgets :reference-label] (comp str :widget/size))]
      (is (not (reference/creatable? {:config config, :column column})))
      (is (nil? (reference/create-button {:config config, :column column} "Qux" false))))))

(deftest add-reference-test
  (testing "multiple references: adds the reference"
    (is (= ["w1" "w2"] (reference/add-reference column ["w1"] "w2")))
    (is (= ["w1"] (reference/add-reference column nil "w1"))))

  (testing "multiple references: an entity is referred only once"
    (is (= ["w1" "w2"] (reference/add-reference column ["w1" "w2"] "w1"))))

  (testing "single reference: replaces the reference"
    (is (= "w2" (reference/add-reference single-column "w1" "w2")))
    (is (= "w1" (reference/add-reference single-column nil "w1")))))

(deftest remove-reference-test
  (testing "multiple references: removes the reference"
    (is (= ["w2"] (reference/remove-reference column ["w1" "w2"] "w1")))
    (is (= [] (reference/remove-reference column ["w1"] "w1")))
    (is (= ["w1"] (reference/remove-reference column ["w1"] "w2"))))

  (testing "single reference: removes the reference"
    (is (nil? (reference/remove-reference single-column "w1" "w1")))
    (is (= "w1" (reference/remove-reference single-column "w1" "w2")))))

(deftest item-after-test
  (let [ctx {:config config, :column column, :value ["w1" "w2" "w3"]}]
    (testing "the items are in the target collection's order: Bar w2, Foo w1, Gazonk w3"
      (is (= "w1" (reference/item-after ctx "w2")))
      (is (= "w3" (reference/item-after ctx "w1"))))

    (testing "nothing is after the last item, or after an unknown item"
      (is (nil? (reference/item-after ctx "w3")))
      (is (nil? (reference/item-after ctx "w404"))))))

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
