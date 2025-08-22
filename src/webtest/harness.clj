(ns webtest.harness
  "Minimal Playwright test harness for Clojure.
   - Part 1: setup!
   - Part 2: run-test!
   - Part 3: cleanup!
   Extras: save-failure-screenshot!, append-log!"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [webtest.download :as dl]
            [webtest.email :as email]
            )
  (:import (com.microsoft.playwright Playwright BrowserType BrowserType$LaunchOptions
                                     Page$ScreenshotOptions)
           (com.microsoft.playwright.assertions PlaywrightAssertions)
           (java.nio.file Paths)
           (java.time Instant))

  (:import [com.microsoft.playwright Page]
           [com.microsoft.playwright.options WaitForSelectorState]
           [com.microsoft.playwright Page$WaitForSelectorOptions])
  )

;; ---------- Extras ----------

(defn save-failure-screenshot!
  "Takes a Playwright `page` and saves a PNG. Returns the filepath or nil.
   Options: {:dir \"target/screenshots\" :prefix \"fail\" :full? true}"
  [page & [{:keys [dir prefix full?]
            :or   {dir "target/screenshots" prefix "fail" full? true}}]]
  (let [ts   (System/currentTimeMillis)
        file (str dir "/" prefix "-" ts ".png")
        path (Paths/get file (make-array String 0))]
    (try
      (io/make-parents file)
      (.screenshot page
        (doto (Page$ScreenshotOptions.)
          (.setPath path)
          (.setFullPage (boolean full?))))
      (println "[SHOT]" file)
      file
      (catch Throwable _
        (println "[WARN] Could not save screenshot:" file)
        nil))))

;===============================================

(def log-file "target/test-log.edn")


(defn id-first-map [m]
  (let [cmp (fn [a b]
              (cond
                (= a :id) true
                (= b :id) false
                :else (compare (str a) (str b))))]
    (into (sorted-map-by cmp) m)))


;; ---------- Part 1: setup ----------

(defn setup!
  "Create Playwright, Browser, Context, Page from opts and store in `state`.
   - `state` is an atom (string keys like \"url\")
   - opts: {:headless? bool :browser :chromium|:firefox|:webkit}
   Returns the updated state map."
  [state {:keys [headless? browser] :or {headless? false browser :chromium}}]
  (let [pw          (Playwright/create)
        bt          (case browser
                      :firefox (.firefox pw)
                      :webkit  (.webkit pw)
                      :chromium (.chromium pw)
                      (.chromium pw))
        launch-opts (doto (BrowserType$LaunchOptions.)
                      (.setHeadless (boolean headless?)))
        b           (.launch bt launch-opts)
        ctx         (.newContext b)
        pg          (.newPage ctx)]
    (swap! state assoc
           "pw" pw "browser" b "context" ctx "page" pg
           "test-ctr" (or (get @state "test-ctr") 0))
    (println "[SETUP]" "browser:" (name browser) "headless?" headless?)
    @state))

;; ---------- Part 2: run one test ----------



;; ---------- per-run log (EDN, :id first) ----------
(def ^:private ^:dynamic *run-log-file* nil)
(def ^:private ^:dynamic *ctr* 0)
(defn- next-num! [] (alter-var-root #'*ctr* inc))

(defn start-run-log! [basename]
  (let [f (dl/timestamped-path basename)]
    (io/make-parents f)
    (alter-var-root #'*run-log-file* (constantly f))
    (.getAbsolutePath f)))

;;====

(defn append-log!
"Append one EDN map per line to a single log file per run.
 Lazily picks a log path on first use, caches it in state (\"log-file\")
 and *run-log-file*. Orders keys before writing."
([m] (append-log! nil m))
([state m]
 (let [;; choose (or create) the run's single log file
       log-path
       (or (some-> state deref (get "log-file"))
           (some-> *run-log-file* .getAbsolutePath)
           (let [f (dl/timestamped-path "test-log.edn")]
             (io/make-parents f)
             (when state (swap! state assoc "log-file" (.getAbsolutePath f)))
             (alter-var-root #'*run-log-file* (constantly f))
             (.getAbsolutePath f)))
       f (io/file log-path)

       ;; ensure :id exists for this line
       n      (next-num!)
       to-log (assoc m :id n)

       ;; ORDERING: put these first (in this order), then any remaining keys alphabetically
       key-order [:id :name :ok]   ;; <-- edit this vector if you want a different fixed order
       preferred (filter #(contains? to-log %) key-order)
       rest-ks   (->> (keys to-log)
                      (remove (set preferred))
                      (sort-by (fn [k]
                                 (cond
                                   (keyword? k) (name k)
                                   (string? k)  k
                                   :else        (str k)))))

       ;; build an insertion-ordered map for stable printing
       lhm (java.util.LinkedHashMap.)]
   (doseq [k (concat preferred rest-ks)]
     (.put lhm k (get to-log k)))
   (spit f (str (pr-str lhm) "\n") :append true)
   log-path)))

;;=====


;; ---------- failure screenshot ----------
(defn- save-failure-screenshot! [page {:keys [prefix] :or {prefix "fail"}}]
  (dl/save-bytes-with-timestamp! (str prefix "-screenshot.png") (.screenshot page)))

;; ---------- test wrapper (arity 3 & 4) ----------
(defn- mk-test-id [n test-name]
  (format "%03d-%s" (long n)
          (-> (or test-name "unnamed") str/trim (str/replace #"\s+" "-"))))

(defn run-test!
  "Arity 3: (run-test! state test-name f) ; f gets {:state :pw :browser :context :page}
   Arity 4: (run-test! state test-name selector action-fn) ; action-fn is (fn [page sel] ...)"
  ;; Arity 3
  ([state test-name f]
   (let [{:strs [pw browser context page url]} @state
         n        (get (swap! state update "test-ctr" (fnil inc 0)) "test-ctr")
         test-id  (mk-test-id n test-name)
         start-ns (System/nanoTime)
         started  (Instant/now)]
     (try
       (let [payload (f {:state state :pw pw :browser browser :context context :page page})
             ended   (Instant/now)
             dur-ms  (long (/ (- (System/nanoTime) start-ns) 1e6))
             res     (merge {:test-id test-id :name test-name :ok true
                             :url (.url page) :started (str started)
                             :ended (str ended) :duration-ms dur-ms}
                            (when (map? payload) payload))]
         (append-log! state res)
         (println "[OK  ]" test-id "-" test-name)
         res)
       (catch AssertionError e
         (let [shot (save-failure-screenshot! page {:prefix test-id})
               ended (Instant/now)
               dur-ms (long (/ (- (System/nanoTime) start-ns) 1e6))
               res  {:test-id test-id :name test-name :ok false :url (.url page)
                     :error (.getMessage e) :started (str started)
                     :ended (str ended) :duration-ms dur-ms
                     :screenshot shot :class "AssertionError"}]
           (append-log! state res)
           (println "[FAIL]" test-id "-" test-name "\n" (.getMessage e))
           res))
       (catch Throwable e
         (let [shot (save-failure-screenshot! page {:prefix test-id})
               ended (Instant/now)
               dur-ms (long (/ (- (System/nanoTime) start-ns) 1e6))
               res  {:test-id test-id :name test-name :ok false :url (.url page)
                     :error (.getMessage e) :started (str started)
                     :ended (str ended) :duration-ms dur-ms
                     :screenshot shot :class (.getName (class e))}]
           (append-log! state res)
           (println "[ERR ]" test-id "-" test-name "\n" (.getMessage e))
           res)))))

  ;; Arity 4
  ([state test-name selector action-fn]
   (run-test! state test-name
              (fn [{:keys [page]}] (action-fn page selector)))))

(defn run-click-handle! [state test-name selector]
  (run-test! state test-name selector
             (fn [page sel]
               (let [h (.waitForSelector page sel)]
                 (when (nil? h)
                   (throw (ex-info "Selector not found" {:selector sel})))
                 (try
                   (.scrollIntoViewIfNeeded h)
                   (.click h)
                   {:action "click-handle" :selector sel}
                   (finally (when h (.dispose h))))))))


;;======================================================


;;-----------   helper   --------------------

(defn run-click-handle!OLD
  [state test-name selector]
  (run-test! state test-name selector
             (fn [page sel]
               (let [h (.waitForSelector page sel)]     ;; ElementHandle or nil
                 (when (nil? h)
                   (throw (ex-info "Selector not found" {:selector sel})))
                 (try
                   (.scrollIntoViewIfNeeded h)
                   (.click h)
                   {:action "click-handle" :selector sel}
                   (finally
                     (when h
                       (.dispose h))))))))


  ;;---------- END  Part 2 run one test  ------


(defn select-from-jqx!
  "Open dropdown (open-sel), verify panel open (panel-sel), then hover+click option (option-sel).
   Returns a result map; throws on not found."
  [^Page page open-sel panel-sel option-sel & {:keys [timeout-ms] :or {timeout-ms 5000}}]
  (let [wait-opts (doto (Page$WaitForSelectorOptions.) (.setTimeout timeout-ms))]
    (let [res-base {:opened? false :verified? false :selected? false}]
      (try
        ;; 1) Open the dropdown
        (let [h-open (.waitForSelector page open-sel wait-opts)]
          (when (nil? h-open)
            (throw (ex-info "Open control not found" {:selector open-sel})))
          (.scrollIntoViewIfNeeded h-open)
          (.click h-open)
          (.dispose h-open))

        ;; 2) Verify panel visible (deterministic, no sleep)
        (let [panel-opts (doto (Page$WaitForSelectorOptions.)
                           (.setTimeout timeout-ms)
                           (.setState WaitForSelectorState/VISIBLE))
              h-panel (.waitForSelector page panel-sel panel-opts)]
          (when (nil? h-panel)
            (throw (ex-info "Dropdown panel not visible" {:selector panel-sel})))
          (.dispose h-panel))

        ;; 3) Hover then click the option
        (let [h-opt (.waitForSelector page option-sel wait-opts)]
          (when (nil? h-opt)
            (throw (ex-info "Option not found" {:selector option-sel})))
          (.scrollIntoViewIfNeeded h-opt)
          (.hover h-opt)   ;; keep the hover you rely on
          (.click h-opt)
          (.dispose h-opt))

        (merge res-base
               {:opened? true :verified? true :selected? true
                :details {:open open-sel :panel panel-sel :option option-sel}})
        (catch Throwable t
          ;; bubble up so run-test! can screenshot & log
          (throw t))))))

;; Harness wrapper:  run-test! (arity 3)
(defn run-dropdown-select-handle!
  [state test-name open-sel panel-sel option-sel]
  (run-test! state test-name
             (fn [{:keys [page]}]
               (select-from-jqx! page open-sel panel-sel option-sel)
               {:action "dropdown-select-handle"
                :open open-sel :panel panel-sel :option option-sel})))




;;-----------   emaio   ----------------
;;;===============================

(defn mail
  "Sends an email whose text is `body-text` and attaches the file at `attachment-path`."
  [subject body-text attachment-path]
  (let [smtp-opts {:host "smtp.gmail.com"
                   :port 587
                   :user "frankw45@gmail.com"
                   :pass "gnav nzzw uycs ktbf"    ;;; old:  "gzbd ljcs onez fouu"        new:  "gnav nzzw uycs ktbf"
                   :tls  true}
        report-file (io/file attachment-path)
        msg {:from    "frankw45@gmail.com"
             :to      ["frankw45@gmail.com"]
             :subject subject
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
;; ---------- Part 3: cleanup ----------

(defn cleanup!
  "Close page/context/browser/pw found in `state` and remove them from state.
   Returns the updated state map."
  [state]

   (mail "Owl test"  "place holder" (get @state "log-file" ))


               (println "Before cleanup:: state:  " state)
  (let [{:strs [page context browser pw]} @state]
    (try (when page (.close page)) (catch Throwable _))
    (try (when context (.close context)) (catch Throwable _))
    (try (when browser (.close browser)) (catch Throwable _))
    (try (when pw (.close pw)) (catch Throwable _))
    (swap! state dissoc "page" "context" "browser" "pw")
    (println "[CLEANUP] done")
    @state))
