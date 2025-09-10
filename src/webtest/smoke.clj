(ns webtest.smoke
  (:require [webtest.setup :as setup]
            [webtest.harness :as h]
            [webtest.suite :as suite]))

(defn -main [& _]
  (let [state (atom {:headless true})
        pick  (fn [i] (nth suite/steps (dec i)))]
    (setup/setup! state)
    (try
      (let [{:keys [failed?]}
            (h/run-suite! state "smoke-1-2-25" [(pick 1) (pick 2) (pick 25)])]
        (h/cleanup! state)
        (when failed? (System/exit 1)))
      (catch Throwable e
        (h/cleanup! state)
        (throw e)))))