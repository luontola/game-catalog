(ns game-catalog.react-tester
  (:require ["@testing-library/react" :as rtl]
            [cljs.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [reagent.core :as r]))

(def fixture {:after rtl/cleanup})

(use-fixtures :each fixture)

(defn render [hiccup]
  (rtl/cleanup)                                             ; needed when the same test renders a component multiple times
  (rtl/render (r/as-element hiccup)))


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
