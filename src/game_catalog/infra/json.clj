(ns game-catalog.infra.json
  (:require [jsonista.core :as json])
  (:import (com.fasterxml.jackson.databind ObjectMapper)))

(def ^:private ^ObjectMapper default-mapper
  (json/object-mapper {:decode-key-fn true}))

(defn ^String write-value-as-string [obj]
  (json/write-value-as-string obj default-mapper))

(defn read-value [^String json]
  (json/read-value json default-mapper))
