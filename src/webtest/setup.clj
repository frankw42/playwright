(ns webtest.setup
  (:require [webtest.paths :as paths])
  (:import [com.microsoft.playwright Playwright BrowserType$LaunchOptions Browser$NewContextOptions]))

(defn setup!
  "Create Playwright, Chromium Browser, Context, Page. Store in `state` atom.
   Uses paths from paths.clj. Sets :paths in state so logs, screenshots, downloads, and junit
   are routed to /tmp. Reads :headless from state (default true)."
  [state]
  (let [headless? (boolean (get @state :headless true))
        pw        (Playwright/create)
        opts      (doto (BrowserType$LaunchOptions.)
                    (.setHeadless headless?))
        br        (.. pw chromium (launch opts))
        ctx       (-> (Browser$NewContextOptions.)
                      ;; Enable automatic file downloads into :downloads
                      (.setAcceptDownloads true)
                      (->> (.newContext br)))
        pg        (.newPage ctx)
        dirs      (paths/ensure-dirs!)] ;; make sure /tmp paths exist

    ;; Update state with everything needed downstream
    (swap! state merge
           {:pw pw
            :browser br
            :context ctx
            :page pg
            :paths dirs
            :test-ctr (or (:test-ctr @state) 0)
            :browser-name :chromium
            :headless headless?})

    (println "[SETUP]" "browser:" :chromium
             "headless?" headless?
             "artifacts:" (:artifacts dirs)
             "downloads:" (:downloads dirs))

    @state))