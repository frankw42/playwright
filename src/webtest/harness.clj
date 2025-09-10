(ns webtest.harness
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [webtest.log :as log]
    [webtest.junit :as junit]
    [webtest.browser :as browser]
    [webtest.setup :as setup]
    [webtest.email :as email])
  (:import (com.microsoft.playwright Page Page$WaitForSelectorOptions)
           (com.microsoft.playwright.options WaitForSelectorState)
           (java.time Instant)))

;; ---------- helpers ----------


(defn ^:private mk-test-id [n test-name]
  (format "%03d-%s" (long n)
          (-> (or test-name "unnamed") str/trim (str/replace #"\s+" "-"))))

(defn ^:private normalize-payload [p]
  (when (map? p)
    (-> p
        (cond-> (contains? p :ok?) (assoc :ok (:ok? p)))
        (dissoc :ok?))))



(defn set-next-test-num!
  "Next run-test! will use this number. E.g. 25 => test-id 025."
  [state n]
  (swap! state assoc :test-ctr (dec (long n))))

;; ---------- core runner ----------

(defn run-test!
  "Arity 3: (run-test! state test-name f) ; f gets {:state :pw :browser :context :page}
   Arity 4: (run-test! state test-name selector action-fn) ; action-fn is (fn [page sel] ...)"
  ;; Arity 3
  ([state test-name f]
   (let [{:keys [pw browser context page paths]} @state
         {:keys [logs screens]} paths
         n        (-> (swap! state update :test-ctr (fnil inc 0)) :test-ctr)
         test-id  (mk-test-id n test-name)
         start-ns (System/nanoTime)
         started  (Instant/now)]
     (try
       (let [payload (normalize-payload (f {:state state :pw pw :browser browser :context context :page page}))
             ended   (Instant/now)
             dur-ms  (long (/ (- (System/nanoTime) start-ns) 1e6))
             ok      (if (and (map? payload) (contains? payload :ok)) (boolean (:ok payload)) true)
             base    {:test-id test-id :name test-name :ok ok
                      :url (.url ^Page page) :started (str started)
                      :ended (str ended) :duration-ms dur-ms}
             res0    (if (map? payload) (merge base payload) base)
             res     (if ok
                       res0
                       (update res0 :screenshot #(or % (try (browser/save-screenshot! page paths test-id)
                                                            (catch Throwable _ nil)))))]
         (log/append-log! {:logs logs} res)
         (println (clojure.string/join " " (remove nil?
            [(if ok "[OK  ]" "[FAIL]") test-id "-" test-name
              (when-not ok (str "\n" (or (:error res) "")))])))
         res)

       (catch AssertionError e
         (let [shot  (try (browser/save-screenshot! page paths test-id)
                          (catch Throwable _ nil))
               ended  (Instant/now)
               dur-ms (long (/ (- (System/nanoTime) start-ns) 1e6))
               res    {:test-id test-id :name test-name :ok false :url (.url ^Page page)
                       :error (.getMessage e) :started (str started)
                       :ended (str ended) :duration-ms dur-ms
                       :screenshot shot :class "AssertionError"}]
           (log/append-log! {:logs (:logs paths)} res)
           ;; FAIL
           (let [msg (.getMessage e)]
             (println (str/join " " (remove nil?
                                            ["[FAIL]" test-id "-" test-name
                                             (when msg (str "\n" msg))]))))
           res))

       (catch Throwable e
         (let [shot   (try (browser/save-screenshot! page paths test-id)
                           (catch Throwable _ nil))
               ended  (Instant/now)
               dur-ms (long (/ (- (System/nanoTime) start-ns) 1e6))
               res    {:test-id test-id :name test-name :ok false :url (.url ^Page page)
                       :error (.getMessage e) :started (str started)
                       :ended (str ended) :duration-ms dur-ms
                       :screenshot shot :class (.getName (class e))}]
           (log/append-log! {:logs (:logs paths)} res)
           ;; ERR
           (let [msg (.getMessage e)]
             (println (str/join " " (remove nil?
                                            ["[ERR ]" test-id "-" test-name
                                             (when msg (str "\n" msg))]))))
           res)))))

  ;; Arity 4
  ([state test-name selector action-fn]
   (run-test! state test-name
              (fn [{:keys [page]}]
                (action-fn page selector)))))


;;------------------------------------------------

(defn run-click-handle!
  [state test-name selector]
  (run-test! state test-name
             (fn [{:keys [page]}]
               (let [h (.waitForSelector ^com.microsoft.playwright.Page page selector)]
                 (when (nil? h)
                   (throw (ex-info "Selector not found" {:selector selector})))
                 (try
                   (.scrollIntoViewIfNeeded h)
                   (.click h)
                   {:action "click-handle" :selector selector}
                   (finally (when h (.dispose h))))))))




;; ---------- suite runner + JUnit ----------

(defn ^:private call-test [tf state]
(try (tf state) (catch clojure.lang.ArityException _ (tf))))

(defn run-suite! [state suite-name test-fns]
  (let [results (mapv #(call-test % state) test-fns)
        junit-path (junit/write-junit! results (-> @state :paths :junit) suite-name)
        failed? (some (complement :ok) results)]
    (println "JUnit written:" junit-path)
    {:results results :failed? (boolean failed?)}))


(defn run-suite!Old
  "Run a seq of zero-arg fns that each call run-test! and return a result map.
   Writes JUnit and exits non-zero on any failure."
  [state suite-name test-fns]
  (let [results    (mapv (fn [tf] (tf)) test-fns)
        {:keys [junit]} (:paths @state)
        junit-path (junit/write-junit! results junit suite-name)
        failures   (seq (remove :ok results))]
    (println "JUnit written:" junit-path)
    (when failures (System/exit 1))
    results))

;; ---------- specific actions ----------

(defn select-from-jqx!
  "Open dropdown (open-sel), verify panel open (panel-sel), then hover+click option (option-sel)."
  [^Page page open-sel panel-sel option-sel & {:keys [timeout-ms] :or {timeout-ms 5000}}]
  (let [wait-opts (doto (Page$WaitForSelectorOptions.) (.setTimeout timeout-ms))
        res-base  {:opened? false :verified? false :selected? false}]
    (try
      (let [h-open (.waitForSelector page open-sel wait-opts)]
        (when (nil? h-open)
          (throw (ex-info "Open control not found" {:selector open-sel})))
        (.scrollIntoViewIfNeeded h-open)
        (.click h-open)
        (.dispose h-open))

      (let [panel-opts (doto (Page$WaitForSelectorOptions.)
                         (.setTimeout timeout-ms)
                         (.setState WaitForSelectorState/VISIBLE))
            h-panel (.waitForSelector page panel-sel panel-opts)]
        (when (nil? h-panel)
          (throw (ex-info "Dropdown panel not visible" {:selector panel-sel})))
        (.dispose h-panel))

      (let [h-opt (.waitForSelector page option-sel wait-opts)]
        (when (nil? h-opt)
          (throw (ex-info "Option not found" {:selector option-sel})))
        (.scrollIntoViewIfNeeded h-opt)
        (.hover h-opt)
        (.click h-opt)
        (.dispose h-opt))

      (merge res-base
             {:opened? true :verified? true :selected? true
              :details {:open open-sel :panel panel-sel :option option-sel}})
      (catch Throwable t
        (throw t)))))

(defn run-dropdown-select-handle!
  [state test-name open-sel panel-sel option-sel]
  (run-test! state test-name
             (fn [{:keys [page]}]
               (select-from-jqx! page open-sel panel-sel option-sel)
               {:action "dropdown-select-handle"
                :open open-sel :panel panel-sel :option option-sel})))

;; ---------- email + cleanup ----------

(defn- env [k] (System/getenv (str k)))

(defn ^:private send-log-email! [state]

  (when (= "1" (System/getenv "SEND_EMAIL"))

    (let [user-id (env "MAIL_ID")
          user-key (env "MAIL_KEY")
          attach (io/file (-> @state :paths :logs) "test-log.txt")]
      (when (and user-id user-key (.exists attach))
        (let [smtp-opts {:host "smtp.gmail.com" :port 587 :user user-id :pass user-key :tls true}
              msg {:from user-id :to user-id :subject "Owl Test"
                   :body [{:type "text/plain" :content "Owl functional test. Log file attached."}
                          {:type :attachment :content attach :file-name (.getName attach)}]}]
          (email/send-test-report-email smtp-opts msg)
          (println "Email sent with attachment:" (.getName attach)))))

    )
  )

(defn cleanup!
  "Close page/context/browser/pw and remove them from state."
  [state]
  (send-log-email! state)
  (let [{:keys [page context browser pw]} @state]
    (try (when page (.close page)) (catch Throwable _))
    (try (when context (.close context)) (catch Throwable _))
    (try (when browser (.close browser)) (catch Throwable _))
    (try (when pw (.close pw)) (catch Throwable _))
    (swap! state dissoc :page :context :browser :pw)
    (println "[CLEANUP] done")
    @state))