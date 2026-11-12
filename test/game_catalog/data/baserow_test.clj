(ns game-catalog.data.baserow-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [game-catalog.data.baserow :as baserow]
            [game-catalog.infra.json :as json])
  (:import (java.io File)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (java.util.zip ZipEntry ZipOutputStream)))

(def foo-table-id 100)
(def bar-table-id 200)

(def database
  {:tables [{:id foo-table-id
             :name "Foos"
             :fields [{:id 1, :name "Name", :type "text"}
                      {:id 2, :name "Notes", :type "long_text"}
                      {:id 3, :name "Number", :type "number"}
                      {:id 4, :name "Date", :type "date"}
                      {:id 5, :name "Single", :type "single_select"
                       :select_options [{:id 51, :value "foo"}
                                        {:id 52, :value "bar"}]}
                      {:id 6, :name "Multiple", :type "multiple_select"
                       :select_options [{:id 61, :value "foo"}
                                        {:id 62, :value "bar"}]}
                      {:id 7, :name "Bars", :type "link_row", :link_row_table_id bar-table-id}
                      {:id 8, :name "Ignored", :type "text"}]
             :rows [{:id 1
                     :field_1 "Foo One"
                     :field_2 "notes"
                     :field_3 "2017"
                     :field_4 "2024-01-31"
                     :field_5 52
                     :field_6 [62 61]
                     :field_7 [3 1]
                     :field_8 "ignored"}
                    {:id 2
                     :field_1 "Foo Two"
                     :field_2 nil
                     :field_3 nil
                     :field_4 nil
                     :field_5 nil
                     :field_6 []
                     :field_7 []
                     :field_8 nil}]}
            {:id bar-table-id
             :name "Bars"
             :fields [{:id 9, :name "Name", :type "text"}]
             :rows []}]})

(def foo-field-keys
  {"Name" :foo/name
   "Notes" :foo/notes
   "Number" :foo/number
   "Date" :foo/date
   "Single" :foo/single
   "Multiple" :foo/multiple
   "Bars" :foo/bars
   "Ignored" nil})

(defn- thrown-message [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo e
      (ex-message e))))

(deftest entity-id-test
  (testing "is a stable UUID"
    (is (= (baserow/entity-id 100 1) (baserow/entity-id 100 1)))
    (is (re-matches #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    (baserow/entity-id 100 1))))

  (testing "is different for different rows and tables"
    (is (not= (baserow/entity-id 100 1) (baserow/entity-id 100 2)))
    (is (not= (baserow/entity-id 100 1) (baserow/entity-id 200 1)))))

(deftest read-table-test
  (let [[foo1 foo2] (baserow/read-table database "Foos" foo-field-keys)]
    (testing "converts rows to entities"
      (is (= {:entity/id (baserow/entity-id foo-table-id 1)
              :foo/name "Foo One"
              :foo/notes "notes"
              :foo/number "2017"
              :foo/date "2024-01-31"
              :foo/single "bar"
              :foo/multiple ["bar" "foo"]
              :foo/bars [(baserow/entity-id bar-table-id 3)
                         (baserow/entity-id bar-table-id 1)]}
             foo1)))

    (testing "converts empty values"
      (is (= {:entity/id (baserow/entity-id foo-table-id 2)
              :foo/name "Foo Two"
              :foo/notes nil
              :foo/number nil
              :foo/date nil
              :foo/single nil
              :foo/multiple []
              :foo/bars []}
             foo2))))

  (testing "throws on unknown table"
    (is (= "Unknown Baserow table: \"Gazonk\""
           (thrown-message #(baserow/read-table database "Gazonk" {})))))

  (testing "throws on fields which are not in the field mapping"
    (is (= "Unmapped fields in Baserow table Foos: [\"Ignored\"]"
           (thrown-message #(baserow/read-table database "Foos" (dissoc foo-field-keys "Ignored"))))))

  (testing "throws on fields which are in the field mapping but not in the table"
    (is (= "Missing fields in Baserow table Foos: [\"Gazonk\"]"
           (thrown-message #(baserow/read-table database "Foos" (assoc foo-field-keys "Gazonk" :foo/gazonk))))))

  (testing "throws on unknown option IDs"
    (let [database (assoc-in database [:tables 0 :rows 0 :field_5] 666)]
      (is (= "Unknown option ID in Baserow field Single: 666"
             (thrown-message #(baserow/read-table database "Foos" foo-field-keys))))))

  (testing "throws on unsupported field types"
    (let [database (assoc-in database [:tables 0 :fields 0 :type] "formula")]
      (is (= "Unsupported Baserow field type: \"formula\""
             (thrown-message #(baserow/read-table database "Foos" foo-field-keys)))))))

(defn- temp-dir ^File []
  (.toFile (Files/createTempDirectory "baserow-test" (make-array FileAttribute 0))))

(defn- write-zip [file entries]
  (with-open [out (ZipOutputStream. (io/output-stream file))]
    (doseq [[entry-name content] entries]
      (.putNextEntry out (ZipEntry. ^String entry-name))
      (.write out (.getBytes (json/write-value-as-string content) "UTF-8"))
      (.closeEntry out))))

(defn- manifest [version]
  {:version version
   :applications {:database {:items [{:files {:schema "database.json"}}]}}})

(deftest read-export-test
  (let [file (io/file (temp-dir) "export_foo.zip")]
    (testing "reads the database from the export"
      (write-zip file {"manifest.json" (manifest "1.0.0")
                       "database.json" database})
      (is (= ["Foos" "Bars"]
             (->> (baserow/read-export file)
                  :tables
                  (map :name)))))

    (testing "throws on unsupported export version"
      (write-zip file {"manifest.json" (manifest "2.0.0")
                       "database.json" database})
      (is (= "Unsupported Baserow export version: \"2.0.0\""
             (thrown-message #(baserow/read-export file)))))))

(deftest find-export-file-test
  (let [dir (temp-dir)]
    (testing "throws when there are no exports"
      (is (= (str "Expected exactly one Baserow export_*.zip file in " dir ", but found 0")
             (thrown-message #(baserow/find-export-file dir)))))

    (testing "finds the export file"
      (spit (io/file dir "other.zip") "")
      (spit (io/file dir "export_foo.zip") "")
      (is (= (io/file dir "export_foo.zip")
             (baserow/find-export-file dir))))

    (testing "throws when there are many exports"
      (spit (io/file dir "export_bar.zip") "")
      (is (= (str "Expected exactly one Baserow export_*.zip file in " dir ", but found 2")
             (thrown-message #(baserow/find-export-file dir)))))))
