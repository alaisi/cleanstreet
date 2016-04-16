(ns cleanstreet.map
  (:require [reagent.core :as reagent]
            [re-frame.core :refer [subscribe dispatch]]
            [clojure.data :as data]))

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

(defn- build-path-renderer [{color :color} gmap]
  (doto (google.maps.DirectionsRenderer. (clj->js {:map gmap}))
    (.setOptions (clj->js {:preserveViewport true
                           :suppressMarkers true
                           :polylineOptions {:strokeColor color}}))))

(defn- update-path [{route :route :as path} gmap old-renderer]
  (let [renderer (or old-renderer
                     (build-path-renderer path gmap))]
    (if route
      (.setDirections renderer (js/JSON.parse route)))
    renderer))

(defn- update-marker [path gmap old-marker]
  (if (and old-marker (= (js->clj (-> old-marker .getPosition .toJSON))
                         (first (:nodes path))))
    old-marker
    (let [marker (google.maps.Marker. (clj->js {:map gmap
                                                :position (first (:nodes path))
                                                :title (:name path)}))
          show-info (fn []
                      (doto (google.maps.InfoWindow. (clj->js {:content (:name path)
                                                               :disableAutoPan true}))
                        (.open gmap marker)))]
      (when old-marker
        (.setMap old-marker nil))
      (.addListener marker "click" show-info)
      (show-info)
      marker)))

(defn- clear-dom [dom]
  (doseq [val (vals dom)]
    (.setMap val nil)))

(defn- update-dom [path gmap {:keys [renderer marker]}]
  (println "update-dom " path)
  (let [marker (update-marker path gmap marker)
        renderer (update-path path gmap renderer)]
    {:renderer renderer
     :marker marker}))

(defn- update-paths [paths state]
  (let [{gmap :map old-paths :paths doms :doms} @state
        [new removed _] (data/diff paths old-paths)
        _ (doseq [[id _] removed]
            (clear-dom (get doms id)))
        doms (apply dissoc (concat [doms] (map first removed)))
        doms (apply assoc (flatten [doms (map (fn [[id p]]
                                                [id (update-dom p gmap nil)])
                                              new)]))]
    (swap! state merge {:doms doms
                        :paths paths})))

(defn- update-new-path [{nodes :nodes :as path} editing state]
  (let [{gmap :map old-path :path dom :dom} @state
        {:keys [renderer marker info]} dom]
    (when (and renderer marker info (empty? nodes))
      (swap! state merge {:dom {:renderer (.setMap renderer nil)
                                :marker (.setMap marker nil)}
                          :path path}))
    (when-not (or (empty? nodes) (= path old-path))
      (swap! state merge {:dom (update-dom path gmap dom)
                          :path path}))))

(defn google-map []
  (let [state (atom {})
        paths (subscribe [:paths])
        editing (subscribe [:is-new-path])
        new-path (subscribe [:new-path])
        did-update (fn [component old-argv]
                     (update-paths @paths state)
                     (update-new-path @new-path @editing state))]
    (reagent/create-class {:reagent-render #(initial-render @paths @editing @new-path)
                           :component-did-mount (fn [component]
                                                  (on-mount state editing component)
                                                  (did-update component []))
                           :component-did-update did-update})))

#_(defn google-map []
  (let [paths (subscribe [:paths])
        editing (subscribe [:is-new-path])
        new-path (subscribe [:new-path])]
    [google-map-wrapper @paths @editing @new-path]))
