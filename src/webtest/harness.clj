(ns webtest.harness
  "Minimal Playwright test harness for Clojure.
   - Part 1: setup!
   - Part 2: run-test!
   - Part 3: cleanup!
   Extras: save-failure-screenshot!, append-log!"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
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

(defn- append-log!
  "Append one EDN map per line to target/test-log.edn"
  [m]
  (let [f "target/test-log.edn"]
    (io/make-parents f)
    (spit f (str (pr-str m) "\n") :append true)))

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


(defn run-test!
  "Arity 3:  state test-name f
     f gets {:state :pw :browser :context :page}
   Arity 4:  state test-name selector action-fn
     action-fn is (fn [page sel] ...)"

  ;; --- Arity 3 (back-compat) ---
  ([state test-name f]
   (let [{:strs [pw browser context page url]} @state
         dummy1 (println "page: " page)
         dummy2 (println "url: " url)
         n        (get (swap! state update "test-ctr" (fnil inc 0)) "test-ctr")
         test-id  (format "%03d-%s" (long n)
                          (-> (or test-name "unnamed")
                              str/trim
                              (str/replace #"\s+" "-")))
         start-ns (System/nanoTime)
         started  (java.time.Instant/now)]

     (println "test-id: " test-id)
     (try
      ; (when url
      ;   (println "[NAV ]" url)
       ;  (.navigate page url))
       (let [payload (f {:state state :pw pw :browser browser :context context :page page})
             ended   (java.time.Instant/now)
             dur-ms  (long (/ (- (System/nanoTime) start-ns) 1e6))
             res     (merge {:id test-id :name test-name :ok true :url url
                             :started (str started) :ended (str ended)
                             :duration-ms dur-ms}
                            (when (map? payload) payload))]
         (append-log! res)
         (println "[OK  ]" test-id "-" test-name)
         res)
       (catch AssertionError e
         (let [shot (save-failure-screenshot! page {:prefix test-id})
               ended (java.time.Instant/now)
               dur-ms (long (/ (- (System/nanoTime) start-ns) 1e6))
               res  {:id test-id :name test-name :ok false :url url
                     :error (.getMessage e) :started (str started)
                     :ended (str ended) :duration-ms dur-ms
                     :screenshot shot :class "AssertionError"}]
           (append-log! res)
           (println "[FAIL]" test-id "-" test-name "\n" (.getMessage e))
           res))
       (catch Throwable e
         (let [shot (save-failure-screenshot! page {:prefix test-id})
               ended (java.time.Instant/now)
               dur-ms (long (/ (- (System/nanoTime) start-ns) 1e6))
               res  {:id test-id :name test-name :ok false :url url
                     :error (.getMessage e) :started (str started)
                     :ended (str ended) :duration-ms dur-ms
                     :screenshot shot :class (.getName (class e))}]
           (append-log! res)
           (println "[ERR ]" test-id "-" test-name "\n" (.getMessage e))
           res)))))

  ;; --- Arity 4 (selector + action) ---
  ([state test-name selector action-fn]
   (println "arity 4: " selector)
   (run-test! state test-name
              (fn [{:keys [page]}]
                (action-fn page selector))
              )
   )
  )

;;-----------   helper   --------------------

(defn run-click-handle!
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


;; ---------- Part 3: cleanup ----------

(defn cleanup!
  "Close page/context/browser/pw found in `state` and remove them from state.
   Returns the updated state map."
  [state]
  (let [{:strs [page context browser pw]} @state]
    (try (when page (.close page)) (catch Throwable _))
    (try (when context (.close context)) (catch Throwable _))
    (try (when browser (.close browser)) (catch Throwable _))
    (try (when pw (.close pw)) (catch Throwable _))
    (swap! state dissoc "page" "context" "browser" "pw")
    (println "[CLEANUP] done")
    @state))
