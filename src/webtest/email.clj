(ns webtest.email
  (:require [postal.core :refer [send-message]]))

(defn send-test-report-email
  "Sends an email with the given subject, body, and optional attachment.
   opts is a map with SMTP settings; msg is a map with from/to/subject/body."
  [opts msg ]
  (try
  (send-message opts msg)
  (catch Exception e
    (println "**** Failed to send email:" (.getMessage e)))))

;;=====  location to create app passwords   =====
;; https://myaccount.google.com/apppasswords

