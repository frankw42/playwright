(ns webtest.helpers
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [webtest.paths :as paths]
            [webtest.metrics :as metrics]
            [webtest.email :as email]))

(ns webtest.helpers
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [webtest.paths :as paths]
            [webtest.email :as email]))


(defn send-log-email! [state]
  (let [user (System/getenv "MAIL_ID")
        pass (System/getenv "MAIL_KEY")]
    (if (and (not (str/blank? (str user)))
             (not (str/blank? (str pass))))
      (let [logf     (io/file (paths/edn-target state))
            junitf   (io/file (paths/junit-target state))
            consolef (io/file (str (paths/artifact-path state "console") ".txt"))
            {:keys [ok total]} (if (.exists consolef)
                                 (metrics/ok-err-counts consolef)
                                 {:ok 0 :total 0})
            subj (str "Owl Test - " (get-in @state [:run :suite] "Function Test")
                      (when (pos? total) (str ", Passed (" ok " of " total ")")))
            atts (cond-> []
                         (.exists logf)   (conj {:type :attachment :content logf   :file-name (.getName logf)})
                         (.exists junitf) (conj {:type :attachment :content junitf :file-name (.getName junitf)})
                         (.exists consolef)(conj {:type :attachment :content consolef :file-name (.getName consolef)}))
            smtp {:host "smtp.gmail.com" :port 587 :user user :pass pass :tls true}
            msg  {:from user :to user :subject subj
                  :body (into [{:type "text/plain" :content "Owl functional test. Logs attached."}]
                              atts)}]
        (if (seq atts)
          (try (email/send-test-report-email smtp msg)
               (println "Email sent:" subj)
               (catch Throwable t
                 (println "Email send failed:" (.getMessage t))))
          (println "Email not sent. No attachments found.")))
      (println "Email not sent. Set MAIL_ID and MAIL_KEY."))))