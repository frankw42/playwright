(ns webtest.functionTest
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]
            [webtest.email :as email]
            [webtest.harness :as h]
            [hello-time :as ht]) ;; if unavailable swap to (java.time.Instant/now)
  (:import (com.microsoft.playwright Playwright BrowserType BrowserType$LaunchOptions
                                     Page Page$ScreenshotOptions
                                     Page$WaitForSelectorOptions Page$WaitForFunctionOptions
                                     Page$WaitForLoadStateOptions
                                     ElementHandle Locator)
           [com.microsoft.playwright.assertions PlaywrightAssertions]
           (java.nio.file Paths)
           (java.time Instant)
           (java.io File)))


(def state (atom {"url" "https://frankw42.github.io/public/index.html"
                  "title" "Owl Buddy"}))


(defn title-step [{:keys [state page]}]
  (let [expected (or (get @state "title") "Owl Buddy")]
    (-> (com.microsoft.playwright.assertions.PlaywrightAssertions/assertThat page)
        (.hasTitle expected))
    {:actual-title (.title page)}))



(defn functionTest [mainState]
  (println "*** functionTest")

  (h/setup! state {:headless? false :browser :chromium})

  (h/run-test! state "Title should be Owl Buddy" title-step)



  ; (h/cleanup! state)
)


;clojure -M -m webtest.core owlUrl owl
; clojure -M -m webtest.core owlUrl functionTest

