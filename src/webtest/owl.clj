(ns webtest.owl
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]
            [webtest.email :as email]
            [hello-time :as ht]) ;; if unavailable swap to (java.time.Instant/now)
  (:import (com.microsoft.playwright Playwright BrowserType BrowserType$LaunchOptions
                                     Page Page$ScreenshotOptions
                                     Page$WaitForSelectorOptions Page$WaitForFunctionOptions
                                     Page$WaitForLoadStateOptions
                                     ElementHandle Locator)
           (java.nio.file Paths)
           (java.time Instant)
           (java.io File)))


;;;  ========
;;    ToDo:
;;      user assertion function
;;      gem test report log file
;;      email log file
;;;  =======

(def file-path (Paths/get (System/getProperty "user.home")
                          (into-array String ["Downloads"])))    ;"myfile.txt"


;;;===============================

(defn mail
  "Sends an email whose text is `body-text` and attaches the file at `attachment-path`."
  [body-text attachment-path]
  (let [smtp-opts {:host "smtp.gmail.com"
                   :port 587
                   :user "frankw45@gmail.com"
                   :pass "gnav nzzw uycs ktbf"    ;;; old:  "gzbd ljcs onez fouu"        new:  "gnav nzzw uycs ktbf"
                   :tls  true}
        report-file (io/file attachment-path)
        msg {:from    "frankw45@gmail.com"
             :to      ["frankw45@gmail.com"]
             :subject "Automated Test Results"
             :body    [ ;; plain-text part
                       {:type    "text/plain"
                        :content body-text}
                       ;; attachment part
                       {:type      :attachment
                        :content   report-file
                        :file-name (.getName report-file)}]}]
    (email/send-test-report-email smtp-opts msg)
    (println "Email sent with attachment:" (.getName report-file))))

;;====================================

(defn delay-ms [ms]
  (when (pos? ms) (Thread/sleep ms)))

(defn safe-text [^ElementHandle el]
  (try
    (some-> (.textContent el) str/trim)
    (catch Exception _ nil)))


(defn download-and-handle
  [^Page page selector dest-path]

(println  "Is visible:: " selector  " = "  (.isVisible page selector) )

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


(defn downLoadWithTimestamp [page counter]
  ;====  click download button  - visible ====
  (let [tim (str (ht/now))
        testReport "place holder for test log file"
        downloadFileName (str counter (str/replace (str "downloadFile-" tim ".txt") ":" "-"))
        downloadPath (str file-path File/separator downloadFileName)
        - (println "downloadFileName:: " downloadFileName " downloadPath: " downloadPath)
        ]
    (download-and-handle page "#download-button" downloadPath)

    ;====  email test results  =======
    ;====  to test email send downdown file for now dddd??? ===
    ;;  (mail "Owl test " downloadPath)
    )
  )


(defn mailReportWithAttachment [page]
  ;====  email test report, attached report file from User Download dir ====
  (let [tim (str (ht/now))
        testReport "place holder for test log file"
        attachmentFileName "smokeTestReport.txt"
        attachmentFilePath (str file-path File/separator attachmentFileName)
        - (println "attachmentFileName:: " attachmentFileName " attachmentFilePath: " attachmentFilePath)
        ]
    ;====  email test results  =======
    ;====  to test email send downdown file for now dddd??? ===
      (mail "Owl test " attachmentFilePath)
    )
  )





;=============  upload  =========================

;;   (str file-path File/separator downloadFileName) ;dddd
; (upload-file page   "resource/owlBuddyCloudinry.json")
;

(defn upload-file
  "Sets the given file path into the first <input type=\"file\"> on the page,
   bypassing the OS picker."
  [^Page page file-path-str]
  (let [;; build a java.nio.file.Path
        file-path (Paths/get file-path-str (into-array String []))
        ;; locate your file-input; narrow selector as needed
        file-input (.locator page "input[type=file]")]
    ;; this call will fire the same events as a user selecting the file
    (.setInputFiles file-input (into-array java.nio.file.Path [file-path]))
    true))

;======================================



(defn get-jqx-item-span-text
  [^Page page key]
  (let [loc (.locator page key)]  ;; "div#listitem1innerListBoxjqxImageQuery span"
    (when (pos? (.count loc))
      (str/trim (.textContent loc)))))



(defn dropdownSelect
  "Opens the first `.jqx-dropdownlist`, waits for the 2nd item to render, then clicks it.
   Returns true on click, false if it never appeared."
  [^Page page key i & {:keys [timeout-ms] :or {timeout-ms 5000}}]
  ;; 1) Open the dropdown
  (.click page key)
  ;; 2) Wait for the second item’s div to appear
  (let [item-selector  (str "#listitem"  i  "innerListBoxjqxImageQuery")
        - (println "item selector:: "    item-selector)
        opts          (doto (Page$WaitForSelectorOptions.) (.setTimeout timeout-ms))
        handle        (.waitForSelector page item-selector opts)]
    (when handle
      (delay-ms 2000)
      (println "get item span text:: "
      (get-jqx-item-span-text page (str "div#listitem" i  "innerListBoxjqxImageQuery span") ))

      (delay-ms 2000)

      ;; 3) Click it
      (.click page item-selector)
      true)))



(defn toggle-jqx-dropdown-with-check
  "Attempts to click the first `.jqx-dropdownlist` to open, waits 500ms,
   checks for `#dropdownlistContentjqxImageQuery`, then clicks again to close.
   Catches all exceptions; returns a map summarizing what succeeded/failed."

  [^Page page dropdownClass  idAfterOpen  & {:keys [timeout-ms] :or {timeout-ms 5000}}]
  (let [widget-sel dropdownClass            ;;;;=====  ".jqx-dropdownlist"
        ;; attempt to get the widget, but catch any timeout or other errors
        widget
        (try
          (let [wait-opts (doto (Page$WaitForSelectorOptions.) (.setTimeout timeout-ms))]
            (.waitForSelector page widget-sel wait-opts))
          (catch Exception e
            (println "Warning: error locating widget:" (.getMessage e))
            nil))
        result-base {:widget-present (boolean widget)
                     :opened? false
                     :verified? false
                     :closed? false}]
    (if (nil? widget)
      (assoc result-base :error (str "Widget not found: " widget-sel))
      (let [open-res
            (try
              (.click ^com.microsoft.playwright.ElementHandle widget)
              {:clicked? true}
              (catch Exception e
                {:clicked? false :error (str "open click failed: " (.getMessage e))}))
            _ (Thread/sleep 500)
            content
            (try
              (.querySelector page idAfterOpen)           ;;;;==== "#dropdownlistContentjqxImageQuery")
              (catch Exception e
                (println "Warning: error querying content element:" (.getMessage e))
                nil))
            verified? (boolean content)
            close-res
            (try
              (.click ^com.microsoft.playwright.ElementHandle widget)
              {:clicked? true}
              (catch Exception e
                {:clicked? false :error (str "close click failed: " (.getMessage e))}))]
        {:widget-present true
         :opened? (boolean (:clicked? open-res))
         :verified? verified?
         :closed? (boolean (:clicked? close-res))
         :details {:open open-res
                   :verify (if verified?
                             {:found true}
                             {:found false :reason "Content missing"})
                   :close close-res}}))))



(defn anyOne [txt] (contains? #{"Blink" "- Tilt -"  ""} txt))


;=================================================
  ;;==  find the button and click  ====  dddd
(defn extract-label-and-name [^Page page key]
  (let [
        button-loc (.locator page key )   ;;===== "button")
        button-handles (try (seq (.elementHandles button-loc)) (catch Exception _ nil))
        - (println "button-handles: " (count button-handles)      )
        ;; click the first button whose visible text (trimmed) is exactly "Blink"
        - (when-let [btn
                       (some (fn [^ElementHandle b]
                               (let [txt (some-> (safe-text b) str/trim)]
                                 (println "txt: " txt)
                                 (when (anyOne txt) b)))
                             (or button-handles []))]
              (try
                (println "try:: Click Button:: " btn " === "  (.textContent btn))
                (.click ^ElementHandle btn)
                (println "Clicked button.")
                (catch Exception e
                  (println "Failed to click button:" (.getMessage e)))))
          ]
    ))


;;===============================================

(defn owlTest [state]
     ;=======

  (let [pw (Playwright/create)
        browser-type (.chromium pw)
        launch-opts (doto (BrowserType$LaunchOptions.) (.setHeadless false))
        browser (.launch browser-type launch-opts)
        context (.newContext browser)
        page (.newPage context)]

    (try
      ;; Navigate
      (println "url:: " (get @state "url"))
      (.navigate page (get @state "url"))


      ;==========  Select 3 images for flipbook  ============
      (dotimes [_ 3]
        (println " click result: "
                 ; (toggle-jqx-dropdown-with-check page ".jqx-dropdownlist"  "#dropdownlistContentjqxImageQuery"))
                 (toggle-jqx-dropdown-with-check page "#jqxImageQuery" "#dropdownlistContentjqxImageQuery"))
        (delay-ms 1000)
        )
      (delay-ms 500)

      ;====  click Blink button to start flipbook animation  =====
      (extract-label-and-name page "#blink-button")
      (delay-ms 5500)

      ;===== select and play music track =====
      (toggle-jqx-dropdown-with-check page "#jqxMusicQuery" "#dropdownlistContentjqxImageQuery")
      (delay-ms 6500)

      ;====  click Blink button to stop flipbook animation  =====
      (extract-label-and-name page "#blink-button")

      (delay-ms 2500)

      ;; pause and rewind
      (.evaluate page "( () => {  const a = document.querySelector('audio');
                      if (a) {   a.pause();   a.currentTime = 0;  }  })")


      (delay-ms 3500)

      ;====  click Info button  - visible ====
      (println " click result: "
               (extract-label-and-name page "#info-button"))

      (delay-ms 4500)

      ;====  click Info button  - hide ====
      (println " click result: "
               (extract-label-and-name page "#info-button"))

      (delay-ms 2500)

      (downLoadWithTimestamp page 3)

      ;====== turn on audio   ===
      (.evaluate page "( () => {  const a = document.querySelector('audio');
                      if (a) {   a.play();   a.currentTime = 0;  }  })")


      ;;;;;===== upload path to owlBuddy json file   ===
      (upload-file page   "resources/owlBuddycloudinary.json")

      (delay-ms 2500)

      ;====  click Blink button to start flipbook animation  =====
      (extract-label-and-name page "#blink-button")
      (delay-ms 500)
      ;====  click Blink button to stop flipbook animation  =====
      (extract-label-and-name page "#blink-button")
      (delay-ms 500)


      (downLoadWithTimestamp page 4)


      ;====  click Blink button to start flipbook animation  =====
      (extract-label-and-name page "#blink-button")
      (delay-ms 500)

      ;;;;;===== Click Tilt again  ===
      (extract-label-and-name page "#tilt-button")
      (delay-ms 1500)


      ;====  click Blink button to stop flipbook animation  =====
      (extract-label-and-name page "#blink-button")
      (delay-ms 500)

      (downLoadWithTimestamp page 5)

      (delay-ms 2500)

      ;==== select item 2 on dropdown list which is "Puppy" ===
      (dropdownSelect page "#jqxImageQuery" 2)

      ;;;;;=====  start flipbook again  ===
      (extract-label-and-name page "#blink-button")

      (mailReportWithAttachment page)

      )

    ;;=====================================
     ;                END
    ;;=====================================
    ))