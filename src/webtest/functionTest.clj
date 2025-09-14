(ns webtest.functionTest
  (:require [clojure.string :as str]
         ;   [clojure.edn :as edn]
            [clojure.java.io :as io]
         ;   [clojure.edn :as edn]
         ;   [clojure.pprint :refer [pprint]]
            [webtest.email :as email]
            [webtest.harness :as h]
            [webtest.setup :as setup]
            [webtest.console :as console]
        ;    [java-time.api :as jt]
         ;   [clojure.pprint :as pprint]
            [webtest.download :as dl]
            [webtest.upload :as up]
            [hello-time :as ht])
  (:import
    com.microsoft.playwright.Page
    com.microsoft.playwright.options.WaitForSelectorState
    com.microsoft.playwright.Page$WaitForSelectorOptions
    com.microsoft.playwright.Page$ScreenshotOptions       ;; if you use it
    com.microsoft.playwright.Locator
    com.microsoft.playwright.Locator$WaitForOptions
    com.microsoft.playwright.assertions.PlaywrightAssertions
    java.nio.file.Paths
    java.time.Instant
    java.io.File)

  (:import [com.microsoft.playwright Page ElementHandle])
  (:import
    (com.microsoft.playwright Page)
     ;;;;  (com.microsoft.playwright.options WaitUntil State)
    (com.microsoft.playwright.options LoadState WaitUntilState)
    (com.microsoft.playwright.options LoadState)
     )
  )

;==============   upload   ==============================
"Sets the given file path into the first <input type=\"file\"> on the page,
 bypassing the OS picker."


(defn- file-exists? [^File f]
  (and f (.exists f)))

(defn- resource->tempfile ^String [res-name]
  (let [tmp (doto (File/createTempFile "upload-" (str "-" res-name))
              (.deleteOnExit))]
    (with-open [in (io/input-stream (io/resource res-name))]
      (io/copy in tmp))
    (.getAbsolutePath tmp)))

(defn resolve-upload-path
  "Resolve absolute filesystem path for uploads.
   Handles classpath resources, relative paths, absolute paths, and Docker /app/resources."
  ^String [spec]
  (let [spec* (if (str/starts-with? spec "resources/")
                (subs spec (count "resources/"))
                spec)
        f (io/file spec)]
    (cond
      ;; Absolute path exists
      (and (.isAbsolute f) (file-exists? f)) (.getAbsolutePath f)
      ;; Relative path exists
      (file-exists? f) (.getAbsolutePath f)
      ;; Docker resource path exists (/app/resources/foo.json)
      (file-exists? (io/file "resources" spec*)) (.getAbsolutePath (io/file "resources" spec*))
      ;; Classpath resource (local dev)
      (io/resource spec*) (resource->tempfile spec*)
      :else
      (throw (ex-info "Upload file not found"
                      {:spec spec
                       :tried [(.getAbsolutePath f)
                               (str (io/file "resources" spec*))
                               (str "classpath:" spec*)]})))))


(defn make-upload-step
  "Factory for h/run-test! callback.
   Arity 1 (preferred): gets Page from ctx.
   Arity 2 (legacy): captures Page directly."
  ([res-path]                                    ;; preferred
   (fn [{:keys [page]}]
     (let [saved (up/upload-via-input! ^Page page res-path)
           dom   (up/dom-first-file-name ^Page page)]
       {:step :upload-test-step
        :path saved
        :dom-file-name (when dom (str dom))
        :ok (boolean dom)})))
  ([^Page page res-path]                          ;; legacy compatibility
   (fn [_]
     (let [saved (up/upload-via-input! ^Page page res-path)
           dom   (up/dom-first-file-name ^Page page)]
       {:step :upload-test-step
        :path saved
        :dom-file-name (when dom (str dom))
        :ok (boolean dom)}))))


;========================================================

(defn safe-text [^ElementHandle el]
  (try
    (some-> (.textContent el) str/trim)
    (catch Exception _ nil)))


(def state (atom {"url" "https://frankw42.github.io/public/index.html"
                  "title" "Owl Buddy"}))


(defn title-step [{:keys [state page]}]
   ;(println "       params: " (get @state :params))
   ;(println "title: " (get (:params @state) "title"))
  ;(println "page: " page)

  (let [expected (or (get @state "title") "Owl Buddy")]
    (-> (com.microsoft.playwright.assertions.PlaywrightAssertions/assertThat page)
        (.hasTitle expected))
    {:actual-title (.title page)}))


;====================================================


(defn dropdown-item-texts
  [^com.microsoft.playwright.Page page & {:keys [timeout-ms] :or {timeout-ms 8000}}]
  (let [options (.getByRole page com.microsoft.playwright.options.AriaRole/OPTION)]
    (try
      ;; wait for at least one ARIA option to be visible
      (.waitFor (.first options)
                (doto (com.microsoft.playwright.Locator$WaitForOptions.)
                  (.setTimeout timeout-ms)
                  (.setState com.microsoft.playwright.options.WaitForSelectorState/VISIBLE)))
      (vec (.allInnerTexts options))
      (catch Throwable _
        ;; fallback: jqx structure (span inside list items)
        (let [spans (.locator page "div[id^='listitem'][id$='innerListBoxjqxImageQuery'] span")]
          (.waitFor spans
                    (doto (com.microsoft.playwright.Locator$WaitForOptions.)
                      (.setTimeout timeout-ms)
                      (.setState com.microsoft.playwright.options.WaitForSelectorState/VISIBLE)))
          (vec (.allInnerTexts spans)))))))

(defn get-jqx-item-span-text
  "Given a Playwright page and a jqx dropdown item span selector,
   return the trimmed text content or nil."
  [^com.microsoft.playwright.Page page span-selector]
  (let [locator (.locator page span-selector)]
    (when (> (.count locator) 0)
      (str/trim (.innerText (.first locator))))))

(defn dropdown-select!
  "Open the dropdown, wait for item `i`, click it.
   Returns {:ok? true/false, :selector <css>, :text <span-text-or-nil>, :error <ex-or-nil>}."
  ^{:step/name "Dropdown: select item"}
  [^com.microsoft.playwright.Page page i & {:keys [timeout-ms] :or {timeout-ms 5000}}]
  (let [item-selector (str "#listitem" i "innerListBoxjqxImageQuery")
        span-selector (str "div#listitem" i "innerListBoxjqxImageQuery span")
        image-selector "#jqxImageQuery"]
    (try
      ;; 1) Open the dropdown
      (let [image-handle (.waitForSelector page image-selector)]
        (.hover image-handle)
        (Thread/sleep 300)
        (.click image-handle))

      ;; 2) Wait for the item to appear
      (let [opts (doto (com.microsoft.playwright.Page$WaitForSelectorOptions.)
                   (.setTimeout timeout-ms))
            handle (.waitForSelector page item-selector opts)]
        (if (nil? handle)
          {:ok? false :selector item-selector :text nil
           :error (ex-info "Item never appeared" {:timeout-ms timeout-ms})}
          (do
            (.hover handle)
            (Thread/sleep 300)

            ;; 3) Try to get text
            (let [text (try
                         (get-jqx-item-span-text page span-selector)
                         (catch Throwable _ nil))]
              ;; 4) Click the item
              (.click handle)
              (Thread/sleep 300)
              {:ok? true :selector item-selector :text text}))))

      (catch Throwable t
        {:ok? false :selector item-selector :text nil :error t}))))


;===========================================

(defn anyOne [txt] (contains? #{"Blink" "- Tilt -"  ""} txt))


;==========================================


(defn extract-label-and-name
  ^clojure.lang.IPersistentMap
  [^Page page key]
  (let [loc (.locator page key)
        handles (try (.elementHandles loc) (catch Exception _ nil))
        btn (some-> handles seq first)]
    (cond
      (nil? btn)
      {:ok? false :action :click :key key :error "No element found for selector"}

      :else
      (try
        (let [label (try (.textContent ^ElementHandle btn) (catch Exception _ nil))]
          (.click ^ElementHandle btn)
          {:ok? true :action :click :key key :label label})
        (catch Exception e
          {:ok? false
           :action :click
           :key key
           :label (try (.textContent ^ElementHandle btn) (catch Exception e (println "error: " e))) ; _ nil))
           :error (.getMessage e)})))))  ;; <- returns a map on all paths


;===========================================


(defn toggle-jqx-dropdown-with-check

  [^Page page dropdownSelector  idAfterOpen ii & {:keys [timeout-ms] :or {timeout-ms 5000}}]
  (let [widget-sel dropdownSelector
       ; - (println "page: " page)
        - (println "        widget-sel: " widget-sel)
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
            (do
              (.hover (.locator page (str "#listitem" ii "innerListBoxjqxImageQuery") ))
              (try
                (.click ^com.microsoft.playwright.ElementHandle
                        (.waitForSelector page  (str "#listitem" ii "innerListBoxjqxImageQuery")) )
                {:clicked? true}
                (catch Exception e
                  {:clicked? false :error (str "close click failed: " (.getMessage e))}))
              ); end do
            ]
        {:widget-present true
         :opened? (boolean (:clicked? open-res))
         :verified? verified?
         :closed? (boolean (:clicked? close-res))
         :details {:open open-res
                   :verify (if verified?
                             {:found true}
                             {:found false :reason "Content missing"})
                   :close close-res}}))))


;===============  WORKING  download Test  ================

(defn user-downloads-dir ^java.nio.file.Path []
  (Paths/get (System/getProperty "user.home") (into-array String ["Downloads"])))

(defn download-and-handle
  [^Page page selector dest-path]
  (println  "        Is visible:: " selector  " = "  (.isVisible page selector) )

  ;; 1. Kick off the download and wait for it to complete
  (let [download
        (.waitForDownload page
                          (fn []
                            (.click page selector)))

        ;; 2. Get the temp path where Playwright saved it
        temp-path (.path download)]
        ; (println "        Downloaded temp file at:" temp-path)

    ;; 3a. Read it into memory
     ;(let [contents (slurp (str temp-path))]
     ; (println "        First 200 chars of download:" (subs contents 0 (min 200 (count contents))))
     ;  )

    ;; 3b. Or move it to a permanent location
    (io/copy (io/file (str temp-path))
             (io/file dest-path))
    (println "            Saved download to:" dest-path)

    ;; 4. Return the final path
    dest-path))


(defn downLoadWithTimestamp [state counter]
  ;====  click download button  - visible ====

  (let [tim (str (ht/now))
        page (get @state "page")
        downloadFileName (str counter "-" (str/replace (str "downloadFile-" tim ".txt") ":" "-"))
        downloadPath (str (user-downloads-dir) File/separator downloadFileName)
        - (println "        downloadFileName:: " downloadFileName " downloadPath: " downloadPath)
        ]
    (download-and-handle page "#download-button" downloadPath)
    )
)

;================   download wrapper   =========================


(defn download-test-step
  "Harness step: trigger a download and save it under /tmp/downloads."
  [{:keys [state page]}]
  (let [ctr    (-> (swap! state update :download-ctr (fnil inc 0)) :download-ctr)
        prefix (format "%03d" (long ctr))
        ;; TODO: replace selector with your real download control
        saved  (dl/download-to-tmp! ^Page page (:paths @state) "#download-button" prefix)]
    {:step :download-test-step
     :download-path saved
     :ok (.exists (io/file saved))}))


;
;========   Wrap tests for selection   ==========


;; Each element is one test (fn of mainState)
(def steps
  [;; 1
   (fn [mainState]
     (let [r (h/run-test! mainState "Title should be: Owl Buddy" title-step)]
       (Thread/sleep 1000)
       r))
   ;; 2
   (fn [state]
     (let [res-path "resources/owlBuddycloudinary.json"
           r (h/run-test! state (str "Upload: " res-path)
                          (make-upload-step res-path))]
       (Thread/sleep 1000)
       r))

   ;; 3
   (fn [mainState]
     (let [r  (h/run-test! mainState "Start flipbook animation" "#blink-button" extract-label-and-name)]
     (Thread/sleep 5000)
     r))

   ;; 4
   (fn [mainState]
     (let [r (h/run-test! mainState "Stop flipbook animation" "#blink-button" extract-label-and-name)]
     (Thread/sleep 8000)
     r))
   ;; 5
   (fn [mainState]
     (let [r (h/run-test! mainState "Download test" download-test-step)]
       (Thread/sleep 1000)
       r))

   ;; 6
   (fn [mainState]
     (let [open   "#jqxImageQuery"
           panel  "#dropdownlistContentjqxImageQuery"
           option (format "#listitem%dinnerListBoxjqxImageQuery" 0)
           r (h/run-dropdown-select-handle! mainState "Select image by category" open panel option)]
     (Thread/sleep 1000)
     r))
   ;; 7
   (fn [mainState]
     (let [open   "#jqxImageQuery"
           panel  "#dropdownlistContentjqxImageQuery"
           option (format "#listitem%dinnerListBoxjqxImageQuery" 1)
       r (h/run-dropdown-select-handle! mainState "Select image by category" open panel option)]
     (Thread/sleep 1000)
     r))
   ;; 8
   (fn [mainState]
     (let [open   "#jqxImageQuery"
           panel  "#dropdownlistContentjqxImageQuery"
           option (format "#listitem%dinnerListBoxjqxImageQuery" 2)
           r (h/run-dropdown-select-handle! mainState "Select image by category" open panel option)]
     (Thread/sleep 1000)
     r))
   ;; 9
   (fn [mainState]
     (let [open   "#jqxImageQuery"
           panel  "#dropdownlistContentjqxImageQuery"
           option (format "#listitem%dinnerListBoxjqxImageQuery" 3)
           r (h/run-dropdown-select-handle! mainState "Select image by category" open panel option)]
     (Thread/sleep 1000)
     r))
   ;; 10
   (fn [mainState]
     (let [open   "#jqxMusicQuery"
           panel  "#dropdownlistContentjqxMusicQuery"
           option (format "#listitem%dinnerListBoxjqxMusicQuery" 0)
           r (h/run-dropdown-select-handle! mainState "Select music track by category" open panel option)]
     (Thread/sleep 4000)
    r))
   ;; 11
   (fn [mainState]
     (let [r (h/run-test! mainState "Start flipbook animation" "#blink-button" extract-label-and-name)]
     (Thread/sleep 3000)
     r))
   ;; 12
   (fn [mainState]
     (let [r (h/run-test! mainState "Stop flipbook animation" "#blink-button" extract-label-and-name)]
     (Thread/sleep 8000)
     r))
   ;; 13
   (fn [mainState]
     (let [r (h/run-test! mainState "Show Info panel" "#info-button" extract-label-and-name)]
     (Thread/sleep 4000)
     r))
   ;; 14
   (fn [mainState]
     (let [r (h/run-test! mainState "Hide Info panel" "#info-button" extract-label-and-name)]
     (Thread/sleep 2000)
     r))
   ;; 15
   (fn [mainState]
     ;--- note  precondition: download button must be clickable - clickable
     (let [r (h/run-test! mainState "Download-test" download-test-step)]
     (Thread/sleep 2000)
     r))

   ;; 16
   (fn [mainState]
     (let [r (h/run-click-handle!  mainState "Start flipbook animation"  "#blink-button")]
     (Thread/sleep 2000)
     r))
   ;; 17
   (fn [mainState]
     (let [r (h/run-click-handle!  mainState "Start tilt animation"  "#tilt-button")]
     (Thread/sleep 2000)
     r))
   ;; 18
   (fn [mainState]
     (let [open   "#jqxImageQuery"
      panel  "#dropdownlistContentjqxImageQuery"
       option (format "#listitem%dinnerListBoxjqxImageQuery" 0)
       r (h/run-dropdown-select-handle! mainState "Select image by category" open panel option)]
       (Thread/sleep 1000)
     r))
   ;; 19
     (fn [mainState]
       (let [open   "#jqxImageQuery"
             panel  "#dropdownlistContentjqxImageQuery"
             option (format "#listitem%dinnerListBoxjqxImageQuery" 1)
             r (h/run-dropdown-select-handle! mainState "Select image by category" open panel option)]
       (Thread/sleep 1000)
       r))
   ;; 20
     (fn [mainState]
       (let [open   "#jqxImageQuery"
             panel  "#dropdownlistContentjqxImageQuery"
             option (format "#listitem%dinnerListBoxjqxImageQuery" 2)
             r (h/run-dropdown-select-handle! mainState "Select image by category" open panel option)]
       (Thread/sleep 1000)
       r))
   ;; 21
     (fn [mainState]
       (let [open   "#jqxImageQuery"
             panel  "#dropdownlistContentjqxImageQuery"
             option (format "#listitem%dinnerListBoxjqxImageQuery" 3)
             r (h/run-dropdown-select-handle! mainState "Select image by category" open panel option)]
       (Thread/sleep 1000)
       r))
   ;; 22
   (fn [mainState]
     (let [open   "#jqxMusicQuery"
           panel  "#dropdownlistContentjqxMusicQuery"
           option (format "#listitem%dinnerListBoxjqxMusicQuery" 0)
           r (h/run-dropdown-select-handle! mainState "Select music by category" open panel option)]
     (Thread/sleep 8000)
     r))

   ;; 23
   (fn [mainState]
     (let [open   "#jqxMusicQuery"
           panel  "#dropdownlistContentjqxMusicQuery"
           option (format "#listitem%dinnerListBoxjqxMusicQuery" 1)
           r (h/run-dropdown-select-handle! mainState "Select music by category" open panel option)]
     (Thread/sleep 8000)
     r))
   ;; 24
   (fn [mainState]
     (let [open   "#jqxMusicQuery"
           panel  "#dropdownlistContentjqxMusicQuery"
           option (format "#listitem%dinnerListBoxjqxMusicQuery" 2)
           r (h/run-dropdown-select-handle! mainState "Select music by category" open panel option)]
     (Thread/sleep 8000)
     r))

   ;;25
   (fn [mainState]
     (let [r (h/run-test! mainState "Force fail test"
                          (fn [_] (throw (ex-info "        intentional failure" {:step 25}))))]
       (Thread/sleep 1000)
       r))
   ])

(defn show-version [mainState]
  (print "\nStarting Playwright-based test... Version:" (:version @mainState) "  ")
  (println "Current time is:" (try (str (ht/now)) (catch Exception _ (Instant/now))))
  (println "\nTime:  " (ht/time-str (ht/now)) "\n")
)


;;; (functionTestSelection [3])

(defn functionTestSelection
     ;=====================
  "Arity-1: run ALL tests once in order.
   Arity-2: run the given 1-based positions in the SAME order (duplicates allowed)."

  ([mainState]
  ;===========

   (console/with-console-tee mainState
   (fn []
   (show-version mainState)

   (let [p (:params @mainState)]
     (println "\n**** Suite Name:: " (p "suiteName"))

     (try
     (setup/setup! mainState)

     (println "        Navigating to:" (p "url") "\n")
     (.navigate (:page @mainState) (p "url"))
     (Thread/sleep 3000)

     (mapv #(% mainState) steps)

     (finally
       (setup/finish! mainState))) )) ))


   ([mainState positions]
   ;=====================

    (console/with-console-tee mainState
    (fn []
    (show-version mainState)

    (let [p (:params @mainState)]
      (println "\n*** Suite Name:: " (p "suiteName"))
      (try
        (setup/setup! mainState)
        (println "        Navigating to:" (p "url"))
          ; (println "mainState: "  @mainState)
        (.navigate ^com.microsoft.playwright.Page (:page @mainState) (p "url"))

        (Thread/sleep 3000)

       (mapv (fn [pos]
            (if-let [step (nth steps (dec pos) nil)]
              (step mainState)
              {:position pos :ok false :error :no-such-test}))
          positions)

      (finally
        (setup/finish! mainState))) ) ))
    )
  )


;
;===========   Start of Test Suite   =============
;

(defn functionTest [mainState]
     ;============

  (let [p (:params @mainState)]
    (println "\n*** Suite Name:: " (p "suiteName"))

    ;=============   setup   ================================

    (setup/setup! mainState)
    (println) (println "        Navigating to:" (p "url"))
    (.navigate (get @mainState "page") (p "url"))
    (Thread/sleep 3000)

    (h/run-test! mainState "Title should be: Owl Buddy" title-step)
    (Thread/sleep 1000)

    (let [res-path "resources/owlBuddycloudinary.json"]      ;; or an absolute path you built earlier
      (h/run-test! mainState (str "Upload:" res-path)
                   (make-upload-step (get @mainState "page") res-path)))
    (Thread/sleep 1000)

    (h/run-test! mainState "Start flipbook animation" "#blink-button" extract-label-and-name)
    (Thread/sleep 3000)

    (h/run-test! mainState "Stop flipbook animation" "#blink-button" extract-label-and-name)
    (Thread/sleep 8000)

    (h/run-test! mainState "Download-test" download-test-step)


    (doseq [ii [0 1 2 3]] (println "        Select image: " ii )
      (let [open   "#jqxImageQuery"
            panel  "#dropdownlistContentjqxImageQuery"
            option (format "#listitem%dinnerListBoxjqxImageQuery" ii)]
        (h/run-dropdown-select-handle! mainState "Select image by category" open panel option))
      (Thread/sleep 1000))


    (doseq [ii [0 ]] (println "        Select music: " ii )
                        (let [open   "#jqxMusicQuery"
                              panel  "#dropdownlistContentjqxMusicQuery"
                              option (format "#listitem%dinnerListBoxjqxMusicQuery" ii)]
                          (h/run-dropdown-select-handle! mainState "Select music track by category" open panel option))
                        (Thread/sleep 4000))

    (h/run-test! mainState "Start flipbook animation" "#blink-button" extract-label-and-name)
    (Thread/sleep 3000)

    (h/run-test! mainState "Stop flipbook animation" "#blink-button" extract-label-and-name)
    (Thread/sleep 8000)

    (h/run-test! mainState "Show Info panel" "#info-button" extract-label-and-name)
    (Thread/sleep 4000)

    (h/run-test! mainState "Hide Info panel" "#info-button" extract-label-and-name)
    (Thread/sleep 2000)

    ;--- note  precondition: download button must be clickable - clickable
    (h/run-test! mainState "Download-test" download-test-step)
    (Thread/sleep 2000)

    (h/run-click-handle!  mainState "Start flipbook animation"  "#blink-button")
    (Thread/sleep 2000)

    (h/run-click-handle!  mainState "Start tilt animation"  "#tilt-button")
    (Thread/sleep 2000)


    (doseq [ii [0 1 2 3]] (println "image doseq:: " ii )
      (let [open   "#jqxImageQuery"
          panel  "#dropdownlistContentjqxImageQuery"
          option (format "#listitem%dinnerListBoxjqxImageQuery" ii)]
      (h/run-dropdown-select-handle! mainState "Select image by category" open panel option))
      (Thread/sleep 1000))

    (doseq [ii [0 1 2]] (println "music doseq:: " ii )
    (let [open   "#jqxMusicQuery"
          panel  "#dropdownlistContentjqxMusicQuery"
          option (format "#listitem%dinnerListBoxjqxMusicQuery" ii)]
      (h/run-dropdown-select-handle! mainState "Select music by category" open panel option))
      (Thread/sleep 8000))


     (h/cleanup! mainState)
    ) ; end let
)


; start nrepl
; Open your Clojure project in IntelliJ.
; Go to Run → Edit Configurations….
; Click the + icon and select Clojure REPL → Local.
; Choose deps.edn (or Leiningen/Boot if that’s what your project uses).
; Set the Project and Module to your project.
; Apply and run the configuration

;============   To Start  run   =============

;  cd to playwright  dir
;clojure -M -m webtest.core owlUrl owl

;---  no third param will run all test steps headless, optional: third param = headed   ---
; clojure -M -m webtest.core owlUrl functionTest

