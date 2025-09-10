(ns webtest.junit
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn xml-escape [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'" "&apos;")))

(defn- ->ms [x] (long (or x 0)))

(defn write-junit!
  "results = [{:name \"Step\" :ok true :duration-ms 123 :error \"...\"}]"
  [results junit-dir suite-name]
  (let [file    (io/file junit-dir (str "TEST-" suite-name ".xml"))
        tests   (count results)
        failures (count (remove :ok results))
        total-ms (reduce + (map (comp ->ms :duration-ms) results))
        body (str
               "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
               "<testsuite name=\"" (xml-escape suite-name)
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
                                    (xml-escape err)
                                    "</failure>"))
                             "</testcase>\n")))
               "</testsuite>\n")]
    (.mkdirs (.getParentFile file))
    (spit file body)
    (.getAbsolutePath file)))