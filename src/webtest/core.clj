(ns webtest.core
    (:require [clojure.string :as str]
      [cheshire.core :as json]
      [clojure.pprint :refer [pprint]]
      [hello-time :as ht]) ;; if unavailable swap to (java.time.Instant/now)
    (:import
      (com.microsoft.playwright Playwright BrowserType BrowserType$LaunchOptions Page ElementHandle Locator)))

;; --- State / sample params --------------------------------------------------

(defonce state (atom {:index 0 :numInputs 0 :countInputs 0}))

(def params
  {"username" "George"
   "password" "abc"
   "formal question" "Show formal question with space"
   "formal-question" "internal name: formal-question"
   "textarea" "Hello from Playwright"
   "remarks" "names of input not standard??"
   :name "Alice"
   :email "alice@example.com"
   :roles ["admin" "user"]})

;; --- Helpers ---------------------------------------------------------------

(defn safe-text [^ElementHandle el]
      (try
        (some-> (.textContent el) str/trim)
        (catch Exception _ nil)))

(defn safe-attr [^ElementHandle el attr]
      (try
        (.getAttribute el attr)
        (catch Exception _ nil)))

(defn tag-name [^ElementHandle el]
      (try
        (some-> (.evaluate el "el => el.tagName.toLowerCase()" (object-array [])) str)
        (catch Exception _ nil)))

(defn get-parent-label-text [^ElementHandle el]
      (try
        (let [js "var p = arguments[0].parentElement; while(p && p.tagName.toLowerCase() !== 'label'){ p = p.parentElement; } return p ? p.textContent : null;"
              raw (.evaluate el js (object-array [el]))]
             (when raw
                   (some-> raw str str/trim)))
        (catch Exception _ nil)))

(defn first-if-exists [^Locator locator]
      (try
        (let [cnt (.count locator)]
             (when (pos? cnt)
                   (.first locator)))
        (catch Exception _ nil)))

;; --- Field extraction -----------------------------------------------------

(defn extract-label-and-name [^Page page]
      (let [input-loc (.locator page "input")
            textarea-loc (.locator page "textarea")
            button-loc (.locator page "button")
            input-handles (try (seq (.elementHandles input-loc)) (catch Exception _ nil))
            textarea-handles (try (seq (.elementHandles textarea-loc)) (catch Exception _ nil))
            button-handles (try (seq (.elementHandles button-loc)) (catch Exception _ nil))
            all-inputs (concat (or input-handles []) (or textarea-handles []))

            label-for-fn
            (fn [^ElementHandle el]
                (let [id (safe-attr el "id")]
                     (or
                       (when (and id (not (str/blank? id)))
                             (let [selector (str "label[for=\"" id "\"]")
                                   lbl-loc (.locator page selector)
                                   lbl (first-if-exists lbl-loc)]
                                  (some-> lbl safe-text)))
                       (get-parent-label-text el))))]

           (let [field-maps
                 (for [el all-inputs]
                      (let [tag (tag-name el)
                            name-attr (safe-attr el "name")
                            id-attr (safe-attr el "id")
                            placeholder (safe-attr el "placeholder")
                            label-text (label-for-fn el)]
                           {:type        (or tag "")
                            :visible     label-text
                            :placeholder placeholder
                            :internal    name-attr
                            :id          id-attr
                            :element     el}))

                 button-maps
                 (for [el (or button-handles [])]
                      (let [visible (safe-text el)
                            name-attr (safe-attr el "name")
                            id-attr (safe-attr el "id")
                            internal (or name-attr (and visible (str/trim visible)) id-attr)]
                           {:type     "button"
                            :visible  visible
                            :internal internal
                            :id       id-attr
                            :element  el}))

                 all-fields (concat field-maps button-maps)
                 field-keys (fn [field]
                                (remove nil?
                                        [(some-> (:visible field) str/trim)
                                         (some-> (:visible field) str/lower-case str/trim)
                                         (:placeholder field)
                                         (:internal field)
                                         (:id field)]))
                 reverse-map
                 (reduce (fn [acc field]
                             (reduce (fn [a k]
                                         (assoc a k field))
                                     acc
                                     (field-keys field)))
                         {} all-fields)]
                {:fields all-fields
                 :lookup reverse-map})))

;; --- Filling logic ---------------------------------------------------------

(defn normalize-params [params]
      (into {}
            (map (fn [[k v]]
                     [(-> (str k) str/lower-case) v])
                 params)))

(defn safe-fill [^Page page field value]
      (try
        (let [v (str value)]
             (cond
               (and (:visible field) (not (str/blank? (:visible field))))
               (do
                 (println "Filling by label:" (:visible field))
                 (try
                   (.fill (.getByLabel page (str/trim (:visible field))) v)
                   (catch Exception e
                     (println "Label fill failed, fallback for" (:visible field) ":" (.getMessage e)))))

               (and (:placeholder field) (not (str/blank? (:placeholder field))))
               (do
                 (println "Filling by placeholder:" (:placeholder field))
                 (.fill (.locator page (str "[placeholder=\"" (:placeholder field) "\"]")) v))

               (and (:internal field) (not (str/blank? (:internal field))))
               (do
                 (println "Filling by name:" (:internal field))
                 (.fill (.locator page (str "[name=\"" (:internal field) "\"]")) v))

               (and (:id field) (not (str/blank? (:id field))))
               (do
                 (println "Filling by id:" (:id field))
                 (.fill (.locator page (str "#" (:id field))) v))

               :else
               (println "No selector available for field, skipping:" (select-keys field [:visible :placeholder :internal :id]))))
        (catch Exception e
          (println "Exception in safe-fill for field" (select-keys field [:visible :placeholder :internal :id]) "->" (.getMessage e)))))

(defn fill-fields! [^Page page params]
      (let [{:keys [fields]} (extract-label-and-name page)
            norm-params (normalize-params params)]
           (doseq [field fields]
                  (let [candidates (->> [(:visible field) (:placeholder field) (:internal field) (:id field)]
                                        (remove nil?)
                                        (map str)
                                        (map str/lower-case)
                                        distinct)
                        matched-key (some #(when (contains? norm-params %) %) candidates)
                        value (when matched-key (get norm-params matched-key))]
                       (if value
                         (safe-fill page field value)
                         (println "No param match for field:" (select-keys field [:visible :placeholder :internal :id])))))))

;; --- Submit detection ------------------------------------------------------

(defn find-submit-button [^Page page]
      (try
        (let [buttons (.elementHandles (.locator page "button"))
              matches (filter (fn [^ElementHandle btn]
                                  (let [txt (some-> (safe-text btn) str/lower-case)]
                                       (and txt (re-find #"submit|send" txt))))
                              (or buttons []))]
             (first matches))
        (catch Exception _
          nil)))

;; --- Main ------------------------------------------------------------------

;;; (def url "https://frankw42.github.io/public/index.html")
;;; (def url "https://frankw42.github.io/public/index.html")
(def url "http://localhost:8080/examples/sample.html")



(defn -main [& args]
      (println "Starting Playwright-based test...")
      (println "Current time is:" (try (ht/now) (catch Exception _ (java.time.Instant/now))))
      (let [pw (Playwright/create)
            browser-type (.chromium pw)
            launch-opts (doto (BrowserType$LaunchOptions.) (.setHeadless false))
            browser (.launch browser-type launch-opts)
            context (.newContext browser)
            page (.newPage context)]
           (try
             (.navigate page url)
             ;; Non-fatal check for <h1>
             (let [h1-loc (.locator page "h1")]
                  (when (pos? (try (.count h1-loc) (catch Exception _ 0)))
                        (println "Found <h1> on page:" (some-> (.textContent (.first h1-loc)) str/trim))))
             (fill-fields! page params)
             (when-let [submit-btn (find-submit-button page)]
                       (println "Clicking submit button:" (safe-text submit-btn))
                       (try
                         (.click submit-btn)
                         (catch Exception e
                           (println "Submit click failed:" (.getMessage e)))))
             (finally
               (Thread/sleep 35000)
               (try
                 (.close browser)
                 (.close pw)
                 (catch Exception e
                   (println "Error closing Playwright:" (.getMessage e))))))))