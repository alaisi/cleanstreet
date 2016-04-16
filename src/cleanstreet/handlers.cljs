(ns cleanstreet.handlers
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [re-frame.core :refer [register-handler dispatch path trim-v after debug]]
            [matchbox.core :as m]
            [matchbox.registry :as mr]
            [matchbox.async :as ma]
            [cljs.core.async :refer [<! put!]]))


;;; Firebase

(def fb-conn (m/connect "https://cleanstreet.firebaseio.com/" "default"))

(defn- init-firebase! []
  (go
   (dispatch [:initialize-fb (<! (ma/deref< fb-conn))])))

(defn- update-firebase! [db]
  (go
   (.debug js/console
           (<! (ma/reset!< fb-conn (:maps db))))))

;;; Google maps directions

(def directions (google.maps.DirectionsService.))

(defn build-route-request [nodes]
  (clj->js {:origin (first nodes)
            :destination (last nodes)
            :optimizeWaypoints false
            :waypoints (map (partial hash-map :location)
                            (take 8 (butlast (rest nodes))))
            :travelMode google.maps.TravelMode.DRIVING}))

;;; Helpers

(defn- build-new-path []
  (let [colors ["#fce94f" "#edd400" "#c4a000"
                "#fcaf3e" "#f57900" "#ce5c00"
                "#e9b96e" "#c17d11" "#8f5902"
                "#8ae234" "#73d216" "#4e9a06"
                "#729fcf" "#3465a4" "#204a87"
                "#ad7fa8" "#75507b" "#5c3566"
                "#ef2929" "#cc0000" "#a40000"
                "#eeeeec" "#d3d7cf" "#babdb6"
                "#888a85" "#555753" "#"]]
    {:id (str (random-uuid))
     :color (rand-nth colors)
     :nodes []}))

(def local-mw [(when ^boolean js/goog.DEBUG debug) trim-v])
(def remote-mw (conj local-mw (after update-firebase!)))

;;; Handlers

(register-handler
 :initialize-db
 (fn [_ _]
   ;;(init-firebase!)
   {:local {:editing false :path (build-new-path)}
    :maps {:cleaned {:paths {}}}}))

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
 :set-name
 local-mw
 (fn [db [name]]
   (assoc-in db [:local :path :name] name)))

(register-handler
 :undo-path-marker
 local-mw
 (fn [db _]
   (let [nodes (vec (butlast (get-in db [:local :path :nodes])))]
     (if (> 2 (count nodes))
       (-> db
           (assoc-in [:local :path :nodes] nodes)
           (assoc-in [:local :path :route] nil))
       (do
         (.route directions
                 (build-route-request nodes)
                 #(dispatch [:path-routed %1 %2]))
         (assoc-in db [:local :path :nodes] nodes))))))

(register-handler
 :new-path-marker
 local-mw
 (fn [db [lat-lng]]
   (let [nodes (conj (get-in db [:local :path :nodes]) lat-lng)]
     (when (< 1 (count nodes))
       (.route directions
               (build-route-request nodes)
               #(dispatch [:path-routed %1 %2])))
     (assoc-in db [:local :path :nodes] nodes))))

(register-handler
 :path-routed
 local-mw
 (fn [db [route status]]
   (if-not (= status google.maps.DirectionsStatus.OK)
     (do (.warn js/console status route) db)
     (assoc-in db [:local :path :route] (js/JSON.stringify route)))))

(register-handler
 :add-path
 remote-mw
 (fn [db [path]]
   (-> db
       (assoc-in [:maps :cleaned :paths (:id path)] path)
       (assoc :local {:editing false :path (build-new-path)}))))

