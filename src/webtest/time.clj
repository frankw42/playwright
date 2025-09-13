(ns webtest.time
  (:import [java.time Instant ZoneOffset]
           [java.time.format DateTimeFormatter]))

(def ^DateTimeFormatter ^:private fmt
  (-> (DateTimeFormatter/ofPattern "yyyyMMdd_HHmmss")
      (.withZone ZoneOffset/UTC)))

(defn utc-ts [] (.format fmt (Instant/now)))