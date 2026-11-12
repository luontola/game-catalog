(ns game-catalog.data.baserow
  (:require [clojure.java.io :as io]
            [game-catalog.infra.json :as json])
  (:import (java.io File)
           (java.nio.charset StandardCharsets)
           (java.util UUID)
           (java.util.zip ZipFile)))

(def supported-export-version "1.0.0")

(defn find-export-file
  "Returns the only Baserow export zip file in the directory.
   Throws if there are none or many, so that it's never unclear which export is used."
  ^File [dir]
  (let [files (->> (.listFiles (io/file dir))
                   (filter #(re-matches #"export_.*\.zip" (.getName ^File %)))
                   (sort))]
    (when-not (= 1 (count files))
      (throw (ex-info (str "Expected exactly one Baserow export_*.zip file in " dir ", but found " (count files))
                      {:dir dir, :files (mapv str files)})))
    (first files)))

(defn- read-zip-entry [^ZipFile zip ^String entry-name]
  (let [entry (or (.getEntry zip entry-name)
                  (throw (ex-info (str "Missing " entry-name " in Baserow export " (.getName zip))
                                  {:entry entry-name})))]
    (with-open [in (.getInputStream zip entry)]
      (json/read-value (String. (.readAllBytes in) StandardCharsets/UTF_8)))))

(defn read-export
  "Reads the database from a Baserow export zip file."
  [file]
  (with-open [zip (ZipFile. (io/file file))]
    (let [manifest (read-zip-entry zip "manifest.json")
          databases (get-in manifest [:applications :database :items])]
      (when-not (= supported-export-version (:version manifest))
        (throw (ex-info (str "Unsupported Baserow export version: " (pr-str (:version manifest)))
                        {:manifest manifest})))
      (when-not (= 1 (count databases))
        (throw (ex-info (str "Expected exactly one database in Baserow export, but found " (count databases))
                        {:manifest manifest})))
      (read-zip-entry zip (get-in (first databases) [:files :schema])))))

(defn entity-id
  "Converts a Baserow row ID to a stable entity ID. Row IDs are unique only
   within a table, so the table ID is part of the name."
  [table-id row-id]
  (str (UUID/nameUUIDFromBytes (.getBytes (str "baserow:" table-id ":" row-id) StandardCharsets/UTF_8))))

(defn- option-labeler [field]
  (let [labels (->> (:select_options field)
                    (map (juxt :id :value))
                    (into {}))]
    (fn [option-id]
      (or (get labels option-id)
          (throw (ex-info (str "Unknown option ID in Baserow field " (:name field) ": " option-id)
                          {:field field, :option-id option-id}))))))

(defn- value-converter [field]
  (case (:type field)
    ("text" "long_text" "number" "date") identity
    "single_select" (let [label (option-labeler field)]
                      (fn [option-id]
                        (when (some? option-id)
                          (label option-id))))
    "multiple_select" (let [label (option-labeler field)]
                        (fn [option-ids]
                          (mapv label option-ids)))
    "link_row" (let [table-id (:link_row_table_id field)]
                 (fn [row-ids]
                   (mapv #(entity-id table-id %) row-ids)))
    (throw (ex-info (str "Unsupported Baserow field type: " (pr-str (:type field)))
                    {:field field}))))

(defn read-table
  "Converts the rows of a Baserow table to entities.
   The field-keys map has the Baserow field names as keys, and entity keys as
   values. Every field must be mentioned in the map, so that new fields
   aren't silently ignored. A nil entity key means that the field is skipped."
  [database table-name field-keys]
  (let [table (or (->> (:tables database)
                       (filter #(= table-name (:name %)))
                       first)
                  (throw (ex-info (str "Unknown Baserow table: " (pr-str table-name))
                                  {:table-name table-name})))
        fields (:fields table)
        unmapped (->> fields
                      (map :name)
                      (remove #(contains? field-keys %)))
        missing (->> (keys field-keys)
                     (remove (set (map :name fields))))
        _ (when (seq unmapped)
            (throw (ex-info (str "Unmapped fields in Baserow table " table-name ": " (pr-str (vec unmapped)))
                            {:table-name table-name, :fields unmapped})))
        _ (when (seq missing)
            (throw (ex-info (str "Missing fields in Baserow table " table-name ": " (pr-str (vec missing)))
                            {:table-name table-name, :fields missing})))
        converters (->> fields
                        (keep (fn [field]
                                (when-some [entity-key (get field-keys (:name field))]
                                  {:row-key (keyword (str "field_" (:id field)))
                                   :entity-key entity-key
                                   :convert (value-converter field)}))))]
    (mapv (fn [row]
            (reduce (fn [entity {:keys [row-key entity-key convert]}]
                      (assoc entity entity-key (convert (get row row-key))))
                    {:entity/id (entity-id (:id table) (:id row))}
                    converters))
          (:rows table))))
