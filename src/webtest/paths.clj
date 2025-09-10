(ns webtest.paths
  (:require [clojure.java.io :as io]))

(def ^:private env #(System/getenv %))

(def artifacts-dir (or (env "ARTIFACTS_DIR") "/tmp/artifacts"))
(def downloads-dir (or (env "DOWNLOADS_DIR") "/tmp/downloads"))
(def screenshots-dir (str artifacts-dir "/screenshots"))
(def logs-dir        (str artifacts-dir "/logs"))
(def junit-dir       (str artifacts-dir "/junit"))

(defn ensure-dirs!
  "Create all dirs and return a map used by setup/harness."
  []
  (doseq [d [artifacts-dir downloads-dir screenshots-dir logs-dir junit-dir]]
    (.mkdirs (io/file d)))
  {:artifacts artifacts-dir
   :downloads downloads-dir
   :screens   screenshots-dir
   :logs      logs-dir
   :junit     junit-dir})