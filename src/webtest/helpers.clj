(ns webtest.helpers
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [webtest.paths :as paths]
            [webtest.email :as email]))

(ns webtest.helpers
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [webtest.paths :as paths]
            [webtest.email :as email]))

(defn send-log-email! [state]
  (let [user (System/getenv "MAIL_ID")
        pass (System/getenv "MAIL_KEY")]
    (if (and (some? user) (not (str/blank? user))
             (some? pass) (not (str/blank? pass)))
      (let [logf   (io/file (paths/edn-target state))
            junitf (io/file (paths/junit-target state))
            atts   (cond-> []
                           (.exists logf)  (conj {:type :attachment :content logf  :file-name (.getName logf)})
                           (.exists junitf) (conj {:type :attachment :content junitf :file-name (.getName junitf)}))
            msg    {:from user
                    :to   user
                    :subject (str "Owl Test - " (get-in @state [:run :suite] "Function Test"))
                    :body (into [{:type "text/plain" :content "Owl functional test. Logs attached."}]
                                atts)}
            smtp   {:host "smtp.gmail.com" :port 587 :user user :pass pass :tls true}]
        (if (seq atts)
          (try
            (email/send-test-report-email smtp msg)
            (println "        Email sent. Attachments:" (mapv :file-name atts))
            (catch Throwable t
              (println "        Email send failed:" (.getMessage t))))
          (println "        Email not sent. No attachments found.")))
      (println "        Email not sent. Set MAIL_ID and MAIL_KEY environment variables."))))