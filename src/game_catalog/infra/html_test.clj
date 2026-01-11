(ns game-catalog.infra.html-test
  (:require [clojure.test :refer :all]
            [game-catalog.infra.html :as html]))

(deftest visualize-html-test
  (testing "empty input"
    (is (= "" (html/visualize-html nil)))
    (is (= "" (html/visualize-html ""))))

  (testing "normalizes whitespace"
    (is (= "a b c" (html/visualize-html " a\n\t\rb    c "))))

  (testing "replaces HTML tags with whitespace"
    (is (= "one two" (html/visualize-html "<p>one</p><p>two</p>"))))

  (testing "inline elements will not add spacing to text"
    (is (= "xyz" (html/visualize-html "x<a>y</a>z")))
    (is (= "xyz" (html/visualize-html "x<a><abbr><b><big><cite><code><em><i><small><span><strong><tt>y</tt></strong></span></small></i></em></code></cite></big></b></abbr></a>z")))
    (is (= "xyz" (html/visualize-html "x<a\nhref=\"\"\n>y</a>z"))
        "works with newlines between attributes"))

  (testing "hides style elements"
    (is (= "" (html/visualize-html "<style>p { color: red; }</style>")))
    (is (= "" (html/visualize-html "<style type=\"text/css\">p { color: red; }</style>"))
        "with type attribute"))

  (testing "hides script elements"
    (is (= "" (html/visualize-html "<script>foo()</script>"))))

  (testing "hides noscript elements"
    (is (= "" (html/visualize-html "<noscript>foo</noscript>"))))

  (testing "hides comments"
    (is (= "" (html/visualize-html "<!-- foo -->")))
    (is (= "foobar" (html/visualize-html "foo<!-- 666 -->bar")))
    (is (= "" (html/visualize-html "<!-- > -->"))
        "matches until the end of comment, instead of the first > character")
    (is (= "" (html/visualize-html "<!--\n>\n-->"))
        "works with newlines in the comment"))

  (testing "replaces HTML character entities"
    (is (= "1 000" (html/visualize-html "1&nbsp;000")))
    (is (= "<" (html/visualize-html "&lt;")))
    (is (= ">" (html/visualize-html "&gt;")))
    (is (= "&" (html/visualize-html "&amp;")))
    (is (= "\"" (html/visualize-html "&quot;")))
    (is (= "'" (html/visualize-html "&apos;")))
    (is (= "'" (html/visualize-html "&#39;"))))

  (testing "data-test-icon attribute is shown before the element"
    (is (= "☑️" (html/visualize-html "<input type=\"checkbox\" data-test-icon=\"☑️\" checked value=\"true\">")))
    (is (= "x 🟢 y z" (html/visualize-html "x<div data-test-icon=\"🟢\">y</div>z"))
        "spacing, block elements")
    (is (= "x 🟢 yz" (html/visualize-html "x<span data-test-icon=\"🟢\">y</span>z"))
        "spacing, inline elements")
    (is (= "🟢" (html/visualize-html "<div\ndata-test-icon=\"🟢\"\n></div>"))
        "works with newlines between attributes"))

  (testing "data-test-content attribute replaces the element's content"
    (is (= "[foo]" (html/visualize-html "<textarea data-test-content=\"[foo]\">foo</textarea>")))
    (is (= "x 🟢 z" (html/visualize-html "x<div data-test-content=\"🟢\">y</div>z"))
        "spacing, block elements")
    (is (= "x🟢z" (html/visualize-html "x<span data-test-content=\"🟢\">y</span>z"))
        "spacing, inline elements")
    (is (= "xz" (html/visualize-html "x<span data-test-content=\"\">y</span>z"))
        "empty value hides the element content"))

  (testing "data-test-icon and data-test-content can coexist"
    (is (= "x A B z" (html/visualize-html "x<div data-test-icon=\"A\" data-test-content=\"B\">y</div>z"))
        "spacing, block elements")
    (is (= "x A Bz" (html/visualize-html "x<span data-test-icon=\"A\" data-test-content=\"B\">y</span>z"))
        "spacing, inline elements")))
