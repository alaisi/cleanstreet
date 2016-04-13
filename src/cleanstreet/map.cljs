(ns cleanstreet.map
  (:require [reagent.core :as reagent]
            [re-frame.core :refer [subscribe dispatch]]
            [clojure.data :as data]))

(def ^:const DRIVING google.maps.TravelMode.DRIVING)
(def ^:const OK google.maps.DirectionsStatus.OK)

(def directions (google.maps.DirectionsService.))

(defn- initial-render [& _]
  [:div {:style {:height "600px"}}])

(defn- build-map [component]
  (let [map-canvas (reagent/dom-node component)
        map-options (clj->js {:zoom 16
                              :mapTypeId google.maps.MapTypeId.HYBRID
                              :center {:lat 60.47431090908283
                                       :lng 25.376529693603516 }})]
    (google.maps.Map. map-canvas map-options)))

(defn- on-mount [state editing component]
  (let [map (build-map component)]
    (swap! state assoc :map map)
    (.addListener map "click"
                  #(when @editing
                     (dispatch [:new-path-marker
                                (js->clj (-> % .-latLng .toJSON))])))))

(defn- build-path-renderer [gmap]
  (doto (google.maps.DirectionsRenderer. (clj->js {:map gmap}))
    (.setOptions (clj->js {:preserveViewport true
                           :suppressMarkers true
                           :polylineOptions {:strokeColor "#eab"}}))))

(defn- render-path [path renderer]
  (letfn [(render [route status]
            (if-not (= status OK)
              (.warn js/console status route)
              (.setDirections renderer route)))]
    (.route directions
            (clj->js {:origin (first path)
                      :destination (last path)
                      :waypoints (map (partial hash-map :location)
                                      (take 8 (butlast (rest path))))
                      :travelMode DRIVING})
            render)))

(defn- update-path [path gmap old-renderer]
  (let [renderer (or old-renderer (build-path-renderer gmap))]
    (render-path path renderer)
    renderer))

(defn- update-paths [paths state]
  (let [{gmap :map old-paths :paths renderers :renderers} @state
        [new removed _] (data/diff paths old-paths)
        renderers (apply dissoc (concat [renderers]
                                        (remove nil? removed)))
        renderers (apply assoc (flatten [renderers
                                         (map (fn [p]
                                                [p (update-path p gmap nil)])
                                              (remove nil? new))]))]
    (swap! state merge {:renderers renderers
                        :paths paths})
    (doseq [p (remove nil? removed)]
      (.setMap (get renderers p) nil))))

(defn- update-new-path [path editing state]
  (let [{gmap :map old-path :path renderer :renderer} @state]
    (when-not (= path old-path)
      (if-not (empty? path)
        (swap! state merge {:renderer (update-path path gmap renderer)
                            :path path})))))

(defn google-map []
  (let [state (atom {})
        paths (subscribe [:paths])
        editing (subscribe [:is-new-path])
        new-path (subscribe [:new-path])
        did-update (fn []
                     (update-paths @paths state)
                     (update-new-path @new-path @editing state))]
    (reagent/create-class {:reagent-render #(initial-render @paths @editing @new-path)
                           :component-did-mount (fn [component]
                                                  (on-mount state editing component)
                                                  (did-update))
                           :component-did-update did-update})))
