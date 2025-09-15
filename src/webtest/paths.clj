(ns webtest.paths
  (:require [clojure.java.io :as io]
            [webtest.time :as t]))

;; Create /tmp subdirs and timestamped targets, then return a run-context.
(defn ensure-dirs!
  ([] (ensure-dirs! "Function Test"))
  ([suite]
   (let [ts (or (System/getenv "RUN_TS") (t/utc-ts))
         base "/tmp"
         dirs {:base base
               :artifacts   (str base "/artifacts")
               :downloads   (str base "/downloads")
               :logs        (str base "/logs")
               :screenshots (str base "/screenshots")
               :junit       (str base "/junit")}
         files {:edn   (format "%s/test-log-%s.edn" (:logs dirs) ts)
                :junit (format "%s/results-%s.xml"  (:junit dirs) ts)}
         ctx {:suite suite :ts ts :dirs dirs :files files}]
     (doseq [d (vals dirs)] (.mkdirs (io/file d)))

     (doseq [f (vals files)]
       (let [ff (io/file f)]
         (when (.isDirectory ff) (io/delete-file ff true)) ; kill mistaken dir
         (io/make-parents ff)))

     ctx)))

;; Save the run-context into your main state atom for downstream use.
(defn install-ctx!
  ([state] (install-ctx! state "Function Test"))
  ([state suite]
   (let [{:keys [dirs] :as ctx} (ensure-dirs! suite)]
     (swap! state assoc :run ctx :paths dirs) ; :paths kept for compatibility
     ctx)))

;; Helpers that read paths from state
(defn edn-target      [state] (get-in @state [:run :files :edn]   "/tmp/logs/test-log.edn"))
(defn junit-target    [state] (get-in @state [:run :files :junit] "/tmp/junit/results.xml"))
(defn screenshots-dir [state] (get-in @state [:run :dirs :screenshots] "/tmp/screenshots"))
(defn ts              [state] (get-in @state [:run :ts] (t/utc-ts)))
(defn screenshot-path [state label]
  (format "%s/%s-%s.png" (screenshots-dir state) label (ts state)))

(defn artifacts-dir [state]
  (get-in @state [:run :dirs :artifacts] "/tmp/artifacts"))

(defn artifact-path [state name]
  (format "%s/%s-%s" (artifacts-dir state) name (ts state)))