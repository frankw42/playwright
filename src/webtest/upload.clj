(ns webtest.upload
  (:require [clojure.java.io :as io])
  (:import [com.microsoft.playwright Page Locator]
           [java.nio.file Paths Path]))

(defn- abs-path ^Path [s]
  (let [f (io/file s)]
    (when-not (.exists f)
      (throw (ex-info "Upload file not found" {:path s})))
    (Paths/get (.getAbsolutePath f) (into-array String []))))

(defn upload-via-input!
  "Set file(s) on an <input type=file>. selector defaults to input[type=file].
   Returns the absolute file path string."
  (^String [^Page page file] (upload-via-input! page file "input[type=file]"))
  (^String [^Page page file selector]
   (let [^Locator inp (.locator page selector)
         ^Path p      (abs-path file)]
     (.setInputFiles inp (into-array Path [p]))
     (.getAbsolutePath (io/file (.toString p))))) )

  (defn dom-first-file-name
    "Reads the first selected file name from the input. selector defaults to input[type=file]."
    (^String [^Page page] (dom-first-file-name page "input[type=file]"))
    (^String [^Page page selector]
     (let [^Locator inp (.locator page selector)]
       (.evaluate inp "(el)=> el && el.files && el.files[0] ? el.files[0].name : null"))))