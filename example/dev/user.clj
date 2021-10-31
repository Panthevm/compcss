(ns user
  (:require
   [compcss.core]
   [figwheel.main.api]))


(def figwheel-options
  {:id "app"

   :options
   {:main       'app.core
    :output-to  "resources/public/js/app.js"
    :output-dir "resources/public/js/out"}

   :config
   {:watch-dirs ["src"]
    :css-dirs   ["resources/public/css"]
    :mode :serve
    :ring-server-options
    {:port 3000}}})

(defn my-handler
  [handler]
  (fn [configuration db]
    (prn "Stylesheets" (count (:compcss.core/output-stylesheets db)))
    (handler configuration db)))

(defn -main
  [& {:as args}]
  (compcss.core/create-watcher
   {:input
    {:css ["resources/public/css/src"]
     :clj ["src"]}
    :handler
    (-> compcss.core/after-middleware
        my-handler
        compcss.core/before-middleware)
    :output
    {:css "resources/public/css/clean.css"}})
  (figwheel.main.api/start figwheel-options))
    
