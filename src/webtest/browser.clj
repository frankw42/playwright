(ns webtest.browser
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [com.microsoft.playwright Page Download Page$ScreenshotOptions]))

(defn- safe-filename [s]
  (-> (str s)
      (str/replace #"[<>:\"/\\|?*]" "-") ; Windows-illegal
      (str/replace #"\s+" "-")
      (str/replace #"-{2,}" "-")
      (str/replace #"^\.+$" "-")
      (str/replace #"^\s+|\s+$" "")))

(defn save-screenshot!
  [^Page page paths name]
  (let [dir (:screens paths)
        base (safe-filename name)
        f   (io/file dir (str base ".png"))]
    (io/make-parents f)
    (.screenshot page (doto (Page$ScreenshotOptions.)
                        (.setPath (.toPath f))
                        (.setFullPage true)))
    (.getAbsolutePath f)))

(defn save-download!
  [^Download dl paths fname]
  (let [dir (:downloads paths)
        base (safe-filename fname)
        f   (io/file dir base)]
    (io/make-parents f)
    (.saveAs dl (.toPath f))
    (.getAbsolutePath f)))