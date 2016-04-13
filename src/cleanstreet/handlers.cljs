(ns cleanstreet.handlers
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [re-frame.core :refer [register-handler dispatch path trim-v after debug]]
            [matchbox.core :as m]
            [matchbox.registry :as mr]
            [matchbox.async :as ma]
            [cljs.core.async :refer [<! put!]]))


(def fb-conn (m/connect "https://cleanstreet.firebaseio.com/" "default"))

(defn- init-firebase! []
  (go
   (dispatch [:initialize-fb (<! (ma/deref< fb-conn))])))

(defn- update-firebase! [db]
  (go
   (.debug js/console
           (<! (ma/reset!< fb-conn (:maps db))))))

(def local-mw [(when ^boolean js/goog.DEBUG debug) trim-v])
(def remote-mw (conj local-mw (after update-firebase!)))

(register-handler
 :initialize-db
 (fn [_ _]
   (init-firebase!)
   {:local {:editing false :path []}
    :maps {:cleaned {:paths []}}}))

(register-handler
 :initialize-fb
 local-mw
 (fn [db [maps]]
   (assoc db :maps maps)))

(register-handler
 :toggle-edit
 local-mw
 (fn [db _]
   (update-in db [:local :editing] not)))

(register-handler
 :undo-path-marker
 local-mw
 (fn [db _]
   (update-in db [:local :path] (comp vec butlast))))

(register-handler
 :new-path-marker
 local-mw
 (fn [db [lat-lng]]
   (update-in db [:local :path] conj lat-lng)))

(register-handler
 :add-path
 remote-mw
 (fn [db [path]]
   (-> db
       (update-in [:maps :cleaned :paths] conj path)
       (assoc :local {:editing false :path []}))))

