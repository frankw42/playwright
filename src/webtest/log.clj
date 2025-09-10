(ns webtest.log
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.instant :as inst]))

(defn utc-now [] (.toString (java.time.Instant/now)))

(defn append-log!
  "EDN one-line log + human-readable log."
  [{:keys [logs]} m]
  (let [m* (assoc m :ts (utc-now))
        edn (io/file logs "test-log.edn")
        txt (io/file logs "test-log.txt")]
    (.mkdirs (.getParentFile edn))
    (spit edn (str (pr-str m*) "\n") :append true)
    (with-open [w (io/writer txt :append true)]
      (pp/pprint m* w))
    m*))