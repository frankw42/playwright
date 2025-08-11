(ns webtest.functionTest
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]
            [webtest.email :as email]
            [webtest.harness :as h]
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
  )


(defn safe-text [^ElementHandle el]
  (try
    (some-> (.textContent el) str/trim)
    (catch Exception _ nil)))

(defn get-jqx-item-span-textold
  [^Page page key]
  (let [loc (.locator page key)]  ;; "div#listitem1innerListBoxjqxImageQuery span"
    (when (pos? (.count loc))
      (str/trim (.textContent loc)))))



(def state (atom {"url" "https://frankw42.github.io/public/index.html"
                  "title" "Owl Buddy"}))


(defn title-step [{:keys [state page]}]
 ; (println "title: " (get state "title"))
 ; (println "page: " page)

  (let [expected (or (get state "title") "Owl Buddy")]
    (-> (com.microsoft.playwright.assertions.PlaywrightAssertions/assertThat page)
        (.hasTitle expected))
    {:actual-title (.title page)}))

;====================================================
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

;;;;;;;;;============================================

(defn make-dropdown-test [i & {:keys [timeout-ms] :or {timeout-ms 5000}}]
  (fn [{:keys [^Page page]}]
    (let [{:keys [ok? selector text error]} (dropdown-select! page i :timeout-ms timeout-ms)]
      {:ok ok?
       :step "dropdown-select"
       :i i
       :selector selector
       :text text
       :error (some-> error ex-message)})))

;;;;;;;;;================================================



(defn anyOne [txt] (contains? #{"Blink" "- Tilt -"  ""} txt))



;=================================================
;;==  find the button and click  ====  dddd

(defn extract-label-and-nameOld [^Page page key]
  (println "extract-label-and-name:  page:  "  page)
  (println "extract-label-and-name:  key:  "  key)


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


;====================================

(defn extract-label-and-name [^Page page key]
  (let [
        button-loc (.locator page key )
        button-handles (try (seq (.elementHandles button-loc)) (catch Exception _ nil))
        btn (first button-handles)
          -  (try
              (println "try:: Click Button:: === " (.textContent btn))
              (.click ^ElementHandle btn)
              (println "Clicked button.")
              (catch Exception e
                (println "Failed to click button:" (.getMessage e))))
        ]
    ))

;====================================


(defn make-extract-click-button
  [key & {:keys [timeout-ms] :or {timeout-ms 5000}}]
  (fn [{:keys [^Page page]}]
    (try
      ;; call your original function (side-effecting; prints & clicks)
      (extract-label-and-name page key)
      {:ok   true
       :step "extract-label-and-name"
       :key  key}
      (catch Exception e
        {:ok    false
         :step  "extract-label-and-name"
         :key   key
         :error (.getMessage e)}))))



(defn page-reloaded? [^Page page old-origin]
  (let [new-origin (.evaluate page "performance.timeOrigin")]
    (not= new-origin old-origin)))


;;;;;========

(defn toggle-jqx-dropdown-with-check
  "Attempts to click the first `.jqx-dropdownlist` to open, waits 500ms,
   checks for `#dropdownlistContentjqxImageQuery`, then clicks again to close.
   Catches all exceptions; returns a map summarizing what succeeded/failed."

  [^Page page dropdownSelector  idAfterOpen ii & {:keys [timeout-ms] :or {timeout-ms 5000}}]
  (let [widget-sel dropdownSelector            ;;;;=====  ".jqx-dropdownlist"
       ; - (println "page: " page)
        - (println "widget-sel: " widget-sel)
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
;;===============================================

;
;===========   Start of Test Suite   =============
;

(defn functionTest [mainState]
     ;============

  (let [p (:params @mainState)]
    (println "\n*** Suite Name:: " (p "suiteName"))

    ;=============   setup   ================================

    (h/setup! mainState {:headless? false :browser :chromium})
    (println)
    (println "➡️  Navigating to:" (p "url"))
    (.navigate (get @mainState "page") (p "url"))
    (Thread/sleep 3000)

   ;;--- needs mainState and change to take a title  dddd ?????
    (h/run-test! mainState "Title should be Owl Buddy" title-step)

    (doseq [ii [0 1 2 3]]
      (println "doseq:: " ii )   ;;  " page: " (get @mainState "page"))
      (toggle-jqx-dropdown-with-check (get @mainState "page") "#jqxImageQuery" "#dropdownlistContentjqxImageQuery" ii)
      (Thread/sleep 1000)
      )                    ;;works (extract-label-and-name (get @mainState "page") "#blink-button")  ; "start flipbook"

    (h/run-test! mainState "Start flipbook" "#blink-button" extract-label-and-name)
    (Thread/sleep 3000)

    (h/run-test! mainState "Stop flipbook" "#blink-button" extract-label-and-name)
    (Thread/sleep 2000)

    (h/run-test! mainState "Show Info" "#info-button" extract-label-and-name)
    (Thread/sleep 4000)

    (h/run-test! mainState "Hide Info" "#info-button" extract-label-and-name)
    (Thread/sleep 2000)





    ;;; not good ????
    ;(h/run-test! mainState "Extract click info button" (make-extract-click-button "#info-button"))
    ;(Thread/sleep 3000)
    ;(h/run-test! mainState "Extract click info button" (make-extract-click-button "#info-button"))


    ;(h/run-test! mainState (format "Pick item #%d" 1) (make-dropdown-test 1 :timeout-ms 8000))

    ;; (h/run-test! state "Extract click button" (make-extract-click-button "#info-button"))


    ; (h/cleanup! state)
    ) ; end let
)


;clojure -M -m webtest.core owlUrl owl
; clojure -M -m webtest.core owlUrl functionTest

