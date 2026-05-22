(ns game-catalog.data.csv-fields
  (:require [clojure.data.csv :as csv]
            [clojure.string :as str])
  (:import (java.io StringReader)))

(defn parse-string-vector [value]
  (->> (csv/read-csv (StringReader. value))
       first
       (map str/trim)
       (remove str/blank?)
       vec))
