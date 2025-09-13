(ns webtest.log
  (:require [clojure.java.io :as io]
            [webtest.paths :as paths]))

(defn append-log!
  "Append one or more maps to the run EDN log. Requires `state`."
  ([state m]
   (let [f (paths/edn-target state)]
     (io/make-parents f)
     (spit f (str (pr-str m) "\n") :append true)))
  ([state m & ms]
   (append-log! state (apply merge m (filter map? ms)))))