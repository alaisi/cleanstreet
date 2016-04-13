(ns cleanstreet.core
  (:require [reagent.core :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync]]
            [cleanstreet.subs]
            [cleanstreet.handlers]
            [cleanstreet.views]))

(enable-console-print!)

(defn ^:export main []
  (dispatch-sync [:initialize-db])
  (reagent/render [cleanstreet.views/app]
                  (.getElementById js/document "app")))
