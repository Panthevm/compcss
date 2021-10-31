(ns app.core
  (:require
   [reagent.dom :as dom]))

(defn view
  []
  [:div {:class ["min-h-screen"
                 "bg-red-100"]}

   "Hello World"])

(defn mount
  []
  (dom/render [view] (js/document.getElementById "root")))


(mount)
