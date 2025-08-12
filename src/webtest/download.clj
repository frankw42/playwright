(ns webtest.download
  (:require [clojure.java.io :as io])
  (:import (java.time Instant)
           (java.nio.file Paths)
           )

  (:import
    (com.microsoft.playwright Page))
  )

(defn user-downloads-dir ^java.nio.file.Path []
  (Paths/get (System/getProperty "user.home") (into-array String ["Downloads"])))

(defn downloads-dir []
  ;; Works across Windows/macOS/Linux by using $HOME/Downloads
  (io/file (System/getProperty "user.home") "Downloads"))

(defn instant-utc-nano-str []
  ;; e.g., "2025-08-11T19:00:04.661850100Z" (UTC + nanoseconds)
  (str (Instant/now)))

(defn safe-filename [s]
  ;; Minimal sanitization for Windows/macOS: replace ":" with "-"
  (.replace s ":" "-"))

(defn timestamped-path
  "Build a java.io.File under Downloads with <UTCnanos>-basename."
  [basename]
  (let [ts    (safe-filename (instant-utc-nano-str))
        fname (str ts "-" basename)]
    (io/file (downloads-dir) fname)))

(defn save-string-with-timestamp!
  "Write a text file to Downloads/<UTCnanos>-basename. Returns absolute path."
  [basename text]
  (let [f (timestamped-path basename)]
    (io/make-parents f)
    (spit f text)
    (.getAbsolutePath f)))

(defn save-bytes-with-timestamp!
  "Write bytes to Downloads/<UTCnanos>-basename. Returns absolute path."
  [basename ^bytes bs]
  (let [f (timestamped-path basename)]
    (io/make-parents f)
    (with-open [out (java.io.FileOutputStream. f)]
      (.write out bs))
    (.getAbsolutePath f)))



(defn download-and-handle
  [^Page page selector dest-path]
  (println "download-and-handle:: page: " page)
    (println  "Is visible:: " selector  " = "  (.isVisible page selector) )

  ;(let [btn (.locator page selector)]
  ;  (.isVisible (PlaywrightAssertions/assertThat btn)) ; waits under the hood
   ; (.click btn))

  ;; 1. Kick off the download and wait for it to complete
  (let [download
        (.waitForDownload page
                          (fn []
                            (.click page selector)))

        ;; 2. Get the temp path where Playwright saved it
        temp-path (.path download)]
    (println "Downloaded temp file at:" temp-path)

    ;; 3a. Read it into memory
    (let [contents (slurp (str temp-path))]
      (println "First 200 chars of download:" (subs contents 0 (min 200 (count contents)))))

    ;; 3b. Or move it to a permanent location
    (io/copy (io/file (str temp-path))
             (io/file dest-path))
    (println "Saved download to:" dest-path)

    ;; 4. Return the final path
    dest-path))


(defn downLoadWithTimestamp
  "Builds a timestamped filename in the user's Downloads dir, then runs the click+download.
   - state: harness state atom (must contain \"page\")
   - counter: integer prefix for the filename
   Optional kwargs:
     :selector  CSS selector for the download button (default \"#download-button\")
     :prefix    filename prefix before timestamp (default \"downloadFile\")
     :ext       filename extension, including dot (default \".txt\")
   Returns the final destination path (string)."
  [state counter & {:keys [selector prefix ext]
                    :or   {selector "#download-button"
                           prefix   "downloadFile"
                           ext      ".txt"}}]
  (let [page   (get @state "page")
        ts     (clojure.string/replace (str (java.time.Instant/now)) ":" "-")
        fname  (str counter "-" prefix "-" ts ext)
        ;; build the destination using java.nio Paths (no manual separators)
        dest   (-> (user-downloads-dir) (.resolve fname) str)]
    (println "downLoadWithTimestamp →" dest)
    (download-and-handle page selector dest)))