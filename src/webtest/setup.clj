(ns webtest.setup
  (:require [webtest.paths :as paths]
            [webtest.harness :as h]
            [webtest.junit :as junit])
  (:import [com.microsoft.playwright Playwright BrowserType$LaunchOptions Browser$NewContextOptions]))

(defn setup!
  "Create Playwright, Chromium Browser, Context, Page. Store in `state` atom.
   Creates /tmp dirs and timestamped targets via paths.install-ctx!. Also sets :paths in state.
   Reads :headless from state (default true)."
  [state]
  ;; Create and record the run-context + dirs under /tmp
  (let [{:keys [dirs] :as _ctx} (paths/install-ctx! state "Function Test")
        headless? (boolean (get @state :headless true))
        pw        (Playwright/create)
        opts      (doto (BrowserType$LaunchOptions.)
                    (.setHeadless headless?))
        br        (.. pw chromium (launch opts))
        ctx       (-> (Browser$NewContextOptions.)
                      (.setAcceptDownloads true)   ; send downloads to the container default
                      (->> (.newContext br)))
        pg        (.newPage ctx)]
    (swap! state merge
           {:pw pw
            :browser br
            :context ctx
            :page pg
            :test-ctr (or (:test-ctr @state) 0)
            :browser-name :chromium
            :headless headless?})
    (println "[SETUP]" "browser:" :chromium
             "headless?" headless?
             "artifacts:" (:artifacts dirs)
             "downloads:" (:downloads dirs))
    @state))


(defn finish! [state]
  ;; 1) Write JUnit first so the email can include it.
  (try
    (junit/write-junit! (paths/edn-target state)  ;; EDN log file
                        (paths/junit-target state) ;; full file path e.g. /tmp/junit/results-<ts>.xml
                        (get-in @state [:run :suite] "Function Test"))
    (catch Exception e
      (println "[WARN] junit/write-junit! failed:" (.getMessage e))))
  ;; 2) Delegate resource cleanup + email
  (try
    (h/cleanup! state)
    (catch Throwable e (println "calling cleanup!  "  (.getMessage e)   )  ))
  :ok)