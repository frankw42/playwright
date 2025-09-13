(ns webtest.console
(:require [clojure.java.io :as io]
  [webtest.paths :as paths])
(:import [java.io OutputStream PrintStream PrintWriter]))

(defn tee-os ^OutputStream [a b]
  (proxy [OutputStream] []
    (write
      ([buf off len]
       (.write ^OutputStream a ^bytes buf (int off) (int len))
       (.write ^OutputStream b ^bytes buf (int off) (int len))))
    (flush [] (do (.flush ^OutputStream a) (.flush ^OutputStream b)))
    (close [] (do (.flush ^OutputStream b) (.close ^OutputStream b))))) ; never close 'a'

(defn with-console-tee [state thunk]
  (let [fpath (str (paths/artifact-path state "console") ".txt")
        file  (io/file fpath)]
    (io/make-parents file)
    (with-open [fos (io/output-stream file)]
      (let [origOut System/out
            origErr System/err
            psOut   (PrintStream. (tee-os origOut fos) true "UTF-8")
            psErr   (PrintStream. (tee-os origErr fos) true "UTF-8")
            wOut    (PrintWriter. psOut true)
            wErr    (PrintWriter. psErr true)]
        (try
          (System/setOut psOut)
          (System/setErr psErr)
          (binding [*out* wOut, *err* wErr]
            (thunk))
          (finally
            (.flush fos) (.flush psOut) (.flush psErr)
            (System/setOut origOut)
            (System/setErr origErr)))))))