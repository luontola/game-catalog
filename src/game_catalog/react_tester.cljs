(ns game-catalog.react-tester
  (:require ["@testing-library/react" :as rtl]
            ["@testing-library/user-event$default" :as user-event]
            [cljs.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [goog.object :as g]
            [promesa.core :as p]
            [reagent.core :as r]))

(def ^:dynamic *user-event*)

(def user-event-options
  {:delay 1}) ; increase delay between inputs to reduce test fragility

(def fixture
  {:before (fn []
             (set! *user-event* (.setup user-event (clj->js user-event-options))))
   :after (fn []
            (set! *user-event* nil)
            (rtl/cleanup))})

(use-fixtures :each fixture)

(defn sleep! [ms]
  (p/create (fn [resolve _reject]
              (js/setTimeout resolve ms))))

(defn render [hiccup]
  (rtl/cleanup) ; needed when the same test renders a component multiple times
  (rtl/render (r/as-element hiccup)))

(defn simulate! [event target]
  (p/do
    (when (= :keyboard event)
      (sleep! 10)) ; XXX: typing is flaky - sometimes the first character isn't typed into the just focused input field
    (let [f (g/get *user-event* (name event))]
      (f target))
    (rtl/act (fn []
               (r/flush)))))


(defn inner-text [rendered]
  (.-innerText (.-container rendered)))

(deftest inner-text-test
  (is (= "foo" (inner-text (render [:p "foo"])))))


(defn inner-html [rendered]
  (.-innerHTML (.-container rendered)))

(deftest inner-html-test
  (is (= "<p>foo</p>" (inner-html (render [:p "foo"])))))


(defn query-selector [rendered query]
  (-> (.-container rendered)
      (.querySelector query)))

(deftest query-selector-test
  (is (= "[object HTMLParagraphElement]"
         (str (-> (render [:p "foo"])
                  (query-selector "p"))))))


(defn query-selector-all [rendered query]
  (-> (.-container rendered)
      (.querySelectorAll query)
      (array-seq)))

(deftest query-selector-all-test
  (let [elements (-> (render [:div [:p "1"] [:p "2"]])
                     (query-selector-all "p"))]
    (is (= 2 (count elements)))
    (is (= "[object HTMLParagraphElement]" (str (first elements))))))


(defn normalize-whitespace [& ss]
  (-> (apply str ss)
      (str/replace #"\s+" " ")
      (str/trim)))

(deftest normalize-whitespace-test
  (is (= "foo bar" (normalize-whitespace "  foo\n\nbar "))))

(defn normalize-lines [s]
  (->> s
       (str/split-lines)
       (map normalize-whitespace)
       (remove empty?)
       (str/join "\n")))

(deftest normalize-lines-test
  (is (= "a b c" (normalize-lines "a b c"))
      "already normalized")
  (is (= "a b" (normalize-lines "  a  b  "))
      "trims and collapses spaces")
  (is (= "foo\nbar" (normalize-lines "\nfoo\n\nbar\n"))
      "trims and collapses newlines")
  (is (= "foo\nbar" (normalize-lines "foo \n \n \n bar"))
      "mixed spaces and newlines"))

(defn render-text [hiccup]
  (-> (render hiccup)
      (inner-text)
      (normalize-whitespace)))

(deftest render-text-test
  (is (= "hello there" (render-text [:p "hello" [:br] "there"]))))

(defn render-html [hiccup]
  (-> (render hiccup)
      (inner-html)))

(deftest render-html-test
  (is (= "<p>hello<br>there</p>" (render-html [:p "hello" [:br] "there"]))))
