(ns webtest.core
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [webtest.functionTest :as functionTest]
            [hello-time :as ht]) ;; if unavailable swap to (java.time.Instant/now)
  (:gen-class)
  (:import (com.microsoft.playwright Playwright BrowserType BrowserType$LaunchOptions
                                     Page Page$ScreenshotOptions
                                     Page$WaitForSelectorOptions Page$WaitForFunctionOptions
                                     Page$WaitForLoadStateOptions
                                     ElementHandle Locator)
           (java.nio.file Paths)
           (java.time Instant)
           (java.io File))

  (:import [com.microsoft.playwright Playwright BrowserType$LaunchOptions])
  )

;; --- State / sample params --------------------------------------------------

(defonce state (atom {:index 0 :numInputs 0 :countInputs 0 :owlTest false  "url" "https://frankw42.github.io/public/index.html"}))

(def params
  {"username" "Henry"
   "password" "abc"
   "formal question" "Show formal question with space"
   "formal-question" "internal name: formal-question"
   "textarea" "Hello from Playwright"
   "remarks" "names of input not standard??"
   :name "Alice"
   :email "alice@example.com"
   :roles ["admin" "user"]})


(defn read-params-edn-from-root-slurp!
  []
  (let [f (io/file "params.edn")]
    (if (.exists f)
      (edn/read-string (slurp f))
      (throw (ex-info "params.edn not found in project root." {:path (.getAbsolutePath f)})))))


(defn load-params!
      "Loads params.edn and stores it in the given state atom under :params. Returns the loaded params."
      [state-atom]
      (try
        (let [params (read-params-edn-from-root-slurp!)]
             (swap! state-atom assoc :params params)
             (println "load-params! from file::\n " params "\n\n")
             params)
        (catch Exception e
          (println "Error loading params.edn:" (.getMessage e))
          (throw e))))

;; --- Helpers ---------------------------------------------------------------

(defn delay-ms
      "Blocks the current thread for ms milliseconds. No-op for non-positive values."
      [ms]
      (when (pos? ms)
            (Thread/sleep ms)))


(defn ensure-resources-dir []
      (let [dir (File. "resources")]
           (when (not (.exists dir))
                 (.mkdirs dir))
           dir))

(defn write-report! [report-map]
      (let [dir (ensure-resources-dir)
            reportFileName   (str/replace  (str "report-" (ht/now) "-run-report.edn") ":" "-")
            path (str (.getPath dir) File/separator reportFileName)]

           (spit path (with-out-str (prn report-map)))
           (println "Wrote report to" path)))


(defn take-screenshot! [^Page page filename]
      (let [dir (ensure-resources-dir)
            reportFileName   (str/replace  (str "screenshot-" (ht/now) "-" filename) ":" "-")
            - (println "reportFileName: " reportFileName)
            fullpath (str (.getPath dir) File/separator reportFileName)
            - (println "fullpath: " fullpath)
            path (Paths/get fullpath (into-array String []))
            opts (doto (Page$ScreenshotOptions.)
                       (.setPath path))]
           (.screenshot page opts)
           (println "Saved screenshot to" fullpath)
           fullpath))



;;====================================

(defn safe-text [^ElementHandle el]
      (try
        (some-> (.textContent el) str/trim)
        (catch Exception _ nil)))

(defn safe-attr [^ElementHandle el attr]
      (try
        (.getAttribute el attr)
        (catch Exception _ nil)))

(defn tag-name [^ElementHandle el]
      (try
        (some-> (.evaluate el "el => el.tagName.toLowerCase()" (object-array [])) str)
        (catch Exception _ nil)))

(defn get-parent-label-text [^ElementHandle el]
      (try
        (let [js "var p = arguments[0].parentElement; while(p && p.tagName.toLowerCase() !== 'label'){ p = p.parentElement; } return p ? p.textContent : null;"
              raw (.evaluate el js (object-array [el]))]
             (when raw
                   (some-> raw str str/trim)))
        (catch Exception _ nil)))

(defn first-if-exists [^Locator locator]
      (try
        (let [cnt (.count locator)]
             (when (pos? cnt)
                   (.first locator)))
        (catch Exception _ nil)))

;; --- Field extraction -----------------------------------------------------


(defn extract-label-and-name [^Page page clickBlink]
      (let [input-loc  (.locator page "input")
            textarea-loc (.locator page "textarea")
            button-loc (.locator page "button")
            input-handles (try (seq (.elementHandles input-loc)) (catch Exception _ nil))
            textarea-handles (try (seq (.elementHandles textarea-loc)) (catch Exception _ nil))
            button-handles (try (seq (.elementHandles button-loc)) (catch Exception _ nil))
            all-inputs (concat (or input-handles []) (or textarea-handles []))

            - (println "button-handles: " (count button-handles)      )

            ;; click the first button whose visible text (trimmed) is exactly "Blink"
            - (when clickBlink
                (when-let [blink-btn
                         (some (fn [^ElementHandle b]
                                   (let [txt (some-> (safe-text b) str/trim)]
                                        (when (= txt "Blink") b)))
                               (or button-handles []))]
                        (try
                          (println "try:: btn: === " (.textContent blink-btn))
                          (.click ^ElementHandle blink-btn)
                          (println "Clicked Blink button.")
                          (catch Exception e
                            (println "Failed to click Blink button:" (.getMessage e))))) )

            label-for-fn
            (fn [^ElementHandle el]
                (let [id (safe-attr el "id")]
                     (or
                       (when (and id (not (str/blank? id)))
                             (let [selector (str "label[for=\"" id "\"]")
                                   lbl-loc (.locator page selector)
                                   lbl (first-if-exists lbl-loc)]
                                  (some-> lbl safe-text)))
                       (get-parent-label-text el))))]

           (let [field-maps
                 (for [el all-inputs]
                      (let [tag (tag-name el)
                            name-attr (safe-attr el "name")
                            id-attr (safe-attr el "id")
                            placeholder (safe-attr el "placeholder")
                            label-text (label-for-fn el)]
                           {:type        (or tag "")
                            :visible     label-text
                            :placeholder placeholder
                            :internal    name-attr
                            :id          id-attr
                            :element     el}))

                 button-maps
                 (for [el (or button-handles [])]
                      (let [visible (safe-text el)
                            name-attr (safe-attr el "name")
                            id-attr (safe-attr el "id")
                            internal (or name-attr (and visible (str/trim visible)) id-attr)]
                           {:type     "button"
                            :visible  visible
                            :internal internal
                            :id       id-attr
                            :element  el}))

                 all-fields (concat field-maps button-maps)
                 field-keys (fn [field]
                                (remove nil?
                                        [(some-> (:visible field) str/trim)
                                         (some-> (:visible field) str/lower-case str/trim)
                                         (:placeholder field)
                                         (:internal field)
                                         (:id field)]))
                 reverse-map
                 (reduce (fn [acc field]
                             (reduce (fn [a k]
                                         (assoc a k field))
                                     acc
                                     (field-keys field)))
                         {} all-fields)]
                {:fields all-fields
                 :lookup reverse-map})))




;; --- Filling logic ---------------------------------------------------------

(defn normalize-params [params]
      (into {}
            (map (fn [[k v]]
                     [(-> (str k) str/lower-case) v])
                 params)))

(defn safe-fill [^Page page field value]
      "Fill a field, trying in order: select/dropdown, label, placeholder, name, id.
       `value` can be a collection for selects or a single item for inputs."
      (try
        (let [v value
              tag (some-> (:type field) str/lower-case)]
             (cond
               ;; select/dropdown support
               (= tag "select")
               (let [selector (cond
                                (and (:id field) (not (str/blank? (:id field)))) (str "#" (:id field))
                                (and (:internal field) (not (str/blank? (:internal field)))) (str "[name=\"" (:internal field) "\"]")
                                :else nil)]
                    (if selector
                      (let [loc (.locator page selector)
                            vals (if (coll? v)
                                   (into-array String (map str v))
                                   (into-array String [(str v)]))]
                           (println "Selecting dropdown" selector "value" v)
                           (try
                             (.selectOption loc vals)
                             (catch Exception e
                               (println "Dropdown select failed for" selector ":" (.getMessage e)))))
                      (println "No selector found for select field, skipping:" (select-keys field [:visible :internal :id]))))

               ;; fill by label if available
               (and (:visible field) (not (str/blank? (:visible field))))
               (do
                 (println "Filling by label:" (:visible field) "with" v)
                 (try
                   (.fill (.getByLabel page (str/trim (:visible field))) (str v))
                   (catch Exception e
                     (println "Label fill failed for" (:visible field) ":" (.getMessage e)))))

               ;; fill by placeholder
               (and (:placeholder field) (not (str/blank? (:placeholder field))))
               (do
                 (println "Filling by placeholder:" (:placeholder field) "with" v)
                 (try
                   (.fill (.locator page (str "[placeholder=\"" (:placeholder field) "\"]")) (str v))
                   (catch Exception e
                     (println "Placeholder fill failed for" (:placeholder field) ":" (.getMessage e)))))

               ;; fill by name/internal
               (and (:internal field) (not (str/blank? (:internal field))))
               (do
                 (println "Filling by name:" (:internal field) "with" v)
                 (try
                   (.fill (.locator page (str "[name=\"" (:internal field) "\"]")) (str v))
                   (catch Exception e
                     (println "Name fill failed for" (:internal field) ":" (.getMessage e)))))

               ;; fill by id
               (and (:id field) (not (str/blank? (:id field))))
               (do
                 (println "Filling by id:" (:id field) "with" v)
                 (try
                   (.fill (.locator page (str "#" (:id field))) (str v))
                   (catch Exception e
                     (println "ID fill failed for" (:id field) ":" (.getMessage e)))))

               :else
               (println "No selector available for field, skipping:" (select-keys field [:visible :placeholder :internal :id]))))
        (catch Exception e
          (println "Exception in safe-fill for field" (select-keys field [:visible :placeholder :internal :id]) "->" (.getMessage e)))))



(defn fill-fields! [^Page page params]
      (let [{:keys [fields]} (extract-label-and-name page true)
            norm-params (normalize-params params)]
           (doseq [field fields]
                  (let [candidates (->> [(:visible field) (:placeholder field) (:internal field) (:id field)]
                                        (remove nil?)
                                        (map str)
                                        (map str/lower-case)
                                        distinct)
                        matched-key (some #(when (contains? norm-params %) %) candidates)
                        value (when matched-key (get norm-params matched-key))]
                       (if value
                         (safe-fill page field value)
                         (println "No param match for field:" (select-keys field [:visible :placeholder :internal :id])))))))

;; --- Submit detection ------------------------------------------------------

(defn find-submit-button [^Page page]
      (try
        (let [buttons (.elementHandles (.locator page "button"))
              matches (filter (fn [^ElementHandle btn]
                                  (let [txt (some-> (safe-text btn) str/lower-case)]
                                       (and txt (re-find #"submit|send" txt))))
                              (or buttons []))]
             (first matches))
        (catch Exception _
          nil)))


 ;;--------  helper  ------------

(defn parse-vector-arg [s]
  (when (and (string? s) (str/starts-with? s "["))
    (edn/read-string s))) ;; safely parse EDN vector

(defn headed? [args]
  (some #(= "headed" (str/lower-case %)) args))




;; --- Main ------------------------------------------------------------------

;;;    (def url "https://frankw42.github.io/public/index.html")
;;;    (def url "http://localhost:8080/examples/sample.html")
;;;    (def url "http://localhost:8080")


(defn -main [& args]
    ;dddd  (print "\nStarting Playwright-based test...   version: 1.0.2 ")
    ;dddd  (println "Current time is:" (try (str (ht/now)) (catch Exception _ (Instant/now))))
    ;dddd  (println "\nTime:  " (ht/time-str (ht/now)) "\n")

  (load-params! state)

  (let [[param-1 param-2 & rest] args
    selection (some parse-vector-arg rest)]
    (println "Command line param-1:" param-1 "param-2:" param-2 "rest:" rest "\n")
    (if param-1
      (let [firstSavedUrl (get-in @state [:params (str param-1)])]
          ;(println "        firstSavedUrl: " firstSavedUrl)
        (if firstSavedUrl
          (swap! state assoc "url" firstSavedUrl)
          (swap! state assoc "url" (str param-1)))
          ) ; if
      ) ; if


      ; First  parameter identifies the URL of website to be tested.
      ; Second parameter chooses the test suite:
      ;          functionalTest:   Tests for Owl App webpage hosted on GitHub.
      ;                 default:   Tests for Etaoin User Guide - Sample Page hosted locally.


    (when (= param-2 "functionTest")
      (swap! state assoc :owlTest true)
      (if (headed? rest)
        (swap! state assoc :headless false)
        (swap! state assoc :headless true)
        )

;;;
;;;   ======== Tests for Owl App using framework harness  ===============
;;;
      (if selection    ;------  with only 1 param passed all test steps will be executed -
        (functionTest/functionTestSelection state selection)  ;; 2-arity call
        (functionTest/functionTestSelection state))
      )
    ) ; let

  ;;;
  ;;;  ======== Tests for Sample Page, no framework used  ===============
  ;;;
  (if (not (:owlTest @state))

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

             ;; Non-fatal check for <h1>
             (let [h1-loc (.locator page "h1")]
                  (when (pos? (try (.count h1-loc) (catch Exception _ 0)))
                        (println "Found <h1> on page:" (some-> (.textContent (.first h1-loc)) str/trim))))

             ;; Fill fields
             (fill-fields! page (:params @state))

             ;; Diagnostics and submit with navigation detection
             (let [before-url (.url page)
                   submit-btn (find-submit-button page)
                   submit-text (when submit-btn (some-> (safe-text submit-btn) str))
                   nav-result
                   (if submit-btn
                     (let [new-url
                           (try
                             (.waitForNavigation page
                                                 (reify Runnable
                                                        (run [_]
                                                             (try
                                                               (.click submit-btn)
                                                               (catch Exception e
                                                                 (println "Submit click failed inside navigation wrapper:" (.getMessage e)))))))
                             (.url page)
                             (catch Exception _
                               (.url page)))]
                          {:before-url before-url
                           :after-url new-url
                           :navigated (not= before-url new-url)
                           :submit-text submit-text})
                     {:before-url before-url
                      :after-url before-url
                      :navigated false
                      :submit-text submit-text})]

                  ;; Screenshots
                  (when (:navigated nav-result)
                        (take-screenshot! page "page-change.png"))
                  (take-screenshot! page "post-submit.png")

                  ;; Build and write report
                  (let [{:keys [fields]} (extract-label-and-name page false)
                        filled-fields (map (fn [f]
                                               (select-keys f [:type :visible :placeholder :internal :id]))
                                           fields)
                        report {:timestamp (str (Instant/now))
                                :params params
                                :filled-fields filled-fields
                                :submit-info nav-result}]
                       (write-report! report)))

             (finally
               (Thread/sleep 30000)
               (try
                 (.close browser)
                 (.close pw)
                 (catch Exception e
                   (println "Error closing Playwright:" (.getMessage e)))
                )))
           )
)
  )

;; clojure -M -m webtest.core   http://localhost:8080/examples/sample.html
;; clojure -M -m webtest.core owlUrl functionTest
;; clojure -M -m webtest.core owlUrl functionTest headed
;; clojure -M -m webtest.core owlUrl functionTest headed "[1 2 3]"
;; clojure -M -m webtest.core owlUrl functionTest headed "[13 14 13 14 24 13 14 13 24 24]"


;; --- Demo error test case  ---
;; clojure -M -m webtest.core owlUrl functionTest headed "[1 2 13 19 19 24]"
;; clojure -M -m webtest.core owlUrl functionTest headed "[1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25]"

;; clojure -M -m webtest.core owlUrl functionTest headed "[1 2 3]"

;; $env:MAIL_ID="you@example.com"; $env:MAIL_KEY="kkkkkkkkkk"; clojure -M -m webtest.core owlUrl functionTest "[1 2 3]"
;; $env:MAIL_ID="you@example.com"; $env:MAIL_KEY=""; clojure -M -m webtest.core owlUrl functionTest "[1 2 3]"
