(ns webtest.helpers
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [webtest.paths :as paths]
            [webtest.metrics :as metrics]
            [webtest.email :as email]))

(defn ^:private pick-console-file [state]
  (let [adir (paths/artifacts-dir state)
        by-ts1 (io/file adir (str "console-" (paths/ts state) ".txt"))
        by-env (let [ts (System/getenv "RUN_TS")]
                 (when (seq ts) (io/file adir (str "console-" ts ".txt"))))
        newest (let [fs (some-> (io/file adir) .listFiles seq)]
                 (some->> fs
                          (filter #(re-matches #"console-.*\.txt" (.getName ^java.io.File %)))
                          (sort-by #(.lastModified ^java.io.File %))
                          last))]
    (cond
      (and by-ts1 (.exists by-ts1)) by-ts1
      (and by-env (.exists by-env)) by-env
      (and newest (.exists newest)) newest
      :else nil)))

(defn send-log-email! [state]
  (let [user (System/getenv "MAIL_ID")
        pass (System/getenv "MAIL_KEY")]
    (if (and (some? user) (not (str/blank? user))
             (some? pass) (not (str/blank? pass)))
      (let [logf     (io/file (paths/edn-target state))
            junitf   (io/file (paths/junit-target state))
            consolef (pick-console-file state)
            {:keys [ok total]} (if consolef (metrics/ok-err-counts consolef) {:ok 0 :total 0})
            subj (str "Owl Test - " (get-in @state [:run :suite] "Function Test")
                      (when (pos? total) (str ", Passed (" ok " of " total ")")))
            atts (cond-> []
                         (.exists logf)    (conj {:type :attachment :content logf    :file-name (.getName logf)})
                         (.exists junitf)  (conj {:type :attachment :content junitf  :file-name (.getName junitf)})
                         (some? consolef)  (conj {:type :attachment :content consolef :file-name (.getName consolef)}))
            smtp {:host "smtp.gmail.com" :port 587 :user user :pass pass :tls true}
            msg  {:from user :to user :subject subj
                  :body (into [{:type "text/plain" :content "Owl functional test. Logs attached."}] atts)}]
        (if (seq atts)
          (try
            (email/send-test-report-email smtp msg)
            (println "Email sent:" subj "Attachments:" (mapv :file-name atts))
            (catch Throwable t
              (println "Email send failed:" (.getMessage t))))
          (println "Email not sent. No attachments found.")))
      (println "Email not sent. Set MAIL_ID and MAIL_KEY."))))