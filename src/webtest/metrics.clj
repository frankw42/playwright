(ns webtest.metrics
  (:require [clojure.java.io :as io]))

(defn ok-err-counts [^java.io.File f]
  (try
    (let [s (slurp f)
          ok  (count (re-seq #"\[OK\s*\]"  s))
          err (count (re-seq #"\[ERR\s*\]" s))]
      {:ok ok :err err :total (+ ok err)})
    (catch Throwable _ {:ok 0 :err 0 :total 0})))