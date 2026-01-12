(ns game-catalog.testing.util
  (:require [clojure.test :refer :all]))

;; these can be required to avoid IDE warnings about the built-in clojure.test/is macro special forms
(declare ^{:arglists '([exception-class body])}
         thrown?)
(declare ^{:arglists '([exception-class regex body])}
         thrown-with-msg?)
