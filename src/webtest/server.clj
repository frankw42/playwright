(ns webtest.server
  (:require [org.httpkit.server :as srv])
  (:import (java.lang System)))

(defn ok [_] {:status 200 :headers {"Content-Type" "text/plain"} :body "ok"})

(defn -main [& _]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))]
    (srv/run-server ok {:port port :ip "0.0.0.0"})
    (println "listening on" port)
    @(promise)))