(ns webtest.junit
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn]))

(defn xml-escape [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'" "&apos;")))

(defn- ->ms [x] (long (or x 0)))

(defn- read-ld-edn [f]                      ;; 1 map per line EDN
  (with-open [r (io/reader f)]
    (doall (map edn/read-string (line-seq r)))))

;; Flexible API:
;; - (write-junit! results junit-path suite-name)
;; - (write-junit! edn-path junit-path suite-name) ; edn-path is a file with 1 map/line
(defn write-junit! [results-or-edn junit-path suite-name]
  (let [results (if (or (string? results-or-edn)
                        (instance? java.io.File results-or-edn))
                  (read-ld-edn results-or-edn)
                  results-or-edn)
        tests    (count results)
        failures (count (remove :ok results))
        total-ms (reduce + (map (comp ->ms :duration-ms) results))
        body     (str
                   "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                   "<testsuite name=\"" (xml-escape (or suite-name "suite"))
                   "\" tests=\"" tests "\" failures=\"" failures
                   "\" time=\"" (/ (double total-ms) 1000.0) "\">\n"
                   (apply str
                          (for [{:keys [name ok duration-ms error]} results
                                :let [nm (or name "unnamed")
                                      t  (/ (double (->ms duration-ms)) 1000.0)
                                      err (when-not ok (or error ""))]]
                            (str "  <testcase name=\"" (xml-escape nm)
                                 "\" time=\"" t "\">"
                                 (when err
                                   (str "<failure message=\"failed\">"
                                        (xml-escape err) "</failure>"))
                                 "</testcase>\n")))
                   "</testsuite>\n")
        f (io/file junit-path)]
    (io/make-parents f)                    ;; create parents only
    (when (.isDirectory f) (io/delete-file f true))  ;; kill mistaken dir
    (spit f body)
    (.getAbsolutePath f)))