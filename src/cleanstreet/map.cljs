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
                                       :lng 25.376529693603516 }})
        map (google.maps.Map. map-canvas map-options)]
    (.addListener map "click"
                  #(dispatch [:new-path-marker
                              (js->clj (-> % .-latLng .toJSON))]))
    map))

(defn- build-path-renderer [{color :color} gmap]
  (google.maps.DirectionsRenderer. (clj->js {:map gmap
                                             :preserveViewport true
                                             :suppressMarkers true
                                             :polylineOptions {:strokeColor color}})))

(defn- update-path [{route :route :as path} gmap old-renderer]
  (let [renderer (or old-renderer
                     (build-path-renderer path gmap))]
    (if route
      (.setDirections renderer (clj->js route)))
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
    (if val (.setMap val nil)))
  {:renderer nil :marker nil})

(defn- update-dom [path gmap {:keys [renderer marker]}]
  (let [marker (update-marker path gmap marker)
        renderer (update-path path gmap renderer)]
    {:renderer renderer
     :marker marker}))

(defn- update-paths [paths old-paths doms gmap]
  (let [[new removed _] (data/diff paths old-paths)
        _ (println (count new) (count removed))
        _ (doseq [[id _] removed]
            (clear-dom (get doms id)))
        doms (apply dissoc (concat [doms] (map first removed)))
        doms (apply assoc (flatten [doms (map (fn [[id p]]
                                                [id (update-dom p gmap nil)])
                                              new)]))]
    doms))

(defn- update-new-path [{nodes :nodes :as path} old-path dom gmap]
  (if (empty? nodes)
    (clear-dom dom)
    (if (= path old-path)
      dom
      (update-dom path gmap dom))))

(defn google-map-wrapper [paths editing new-path]
  (let [state (atom {})
        did-update (fn [component [_ old-paths last-edit]]
                     (let [[_ paths editing new-path] (reagent/argv component)]
                       (swap!
                        state
                        (fn [{gmap :map doms :doms dom :new-dom :as local}]
                          (merge local
                                 {:new-dom
                                  (update-new-path new-path last-edit dom gmap)}
                                 {:doms
                                  (update-paths paths old-paths doms gmap)})))))
        did-mount (fn [component]
                    (swap! state assoc :map (build-map component)))]
    (reagent/create-class {:reagent-render (fn []
                                             [:div {:style {:height "600px"}}])
                           :component-did-mount did-mount
                           :component-did-update did-update})))

(defn google-map []
  (let [paths (subscribe [:paths])
        editing (subscribe [:is-new-path])
        new-path (subscribe [:new-path])]
    [google-map-wrapper @paths @editing @new-path]))
