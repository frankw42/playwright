(ns webtest.owl
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]
            [hello-time :as ht]) ;; if unavailable swap to (java.time.Instant/now)
  (:import (com.microsoft.playwright Playwright BrowserType BrowserType$LaunchOptions
                                     Page Page$ScreenshotOptions
                                     Page$WaitForSelectorOptions Page$WaitForFunctionOptions
                                     Page$WaitForLoadStateOptions
                                     ElementHandle Locator)
           (java.nio.file Paths)
           (java.time Instant)
           (java.io File)))


(defn delay-ms [ms]
  (when (pos? ms) (Thread/sleep ms)))

(defn safe-text [^ElementHandle el]
  (try
    (some-> (.textContent el) str/trim)
    (catch Exception _ nil)))



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
        (toggle-jqx-dropdown-with-check page "#jqxImageQuery"  "#dropdownlistContentjqxImageQuery"))
        (delay-ms 1000)
        )
      (delay-ms 500)

      ;====  click Blink button to start flipbook animation  =====
      (extract-label-and-name page  "#blink-button")
      (delay-ms 5500)

      ;===== select and play music track =====
      (toggle-jqx-dropdown-with-check page "#jqxMusicQuery"  "#dropdownlistContentjqxImageQuery")
      (delay-ms 6500)

      ;====  click Blink button to stop flipbook animation  =====
      (extract-label-and-name page    "#blink-button")

      ;====  Pause audio   ====
      (delay-ms 2500)
      ;; pause and rewind
      (.evaluate page
                 "( () => {
                      const a = document.querySelector('audio');
                      if (a) {
                        a.pause();
                        a.currentTime = 0;
                      }
                    })")

      ;====  click Info button  - visible ====
      (println " click result: "
               (extract-label-and-name page "#info-button"))

      (delay-ms 5500)

      ;====  click Info button  - hide ====
      (println " click result: "
               (extract-label-and-name page "#info-button"))

      (delay-ms 2500)

      ;====  click Info button  - visible ====
      (println " click result: "
               (extract-label-and-name page "#download-button"))
      ))
  )





;;=====================================

;(.evaluate page
;           "( () => {
;                const a = document.querySelector('audio');
;                if (a) a.play();
;              })")
