(ns cleanstreet.views
  (:require [reagent.core :as reagent]
            [re-frame.core :refer [subscribe dispatch]]
            [cleanstreet.map :as map]))

(defn app []
  (let [editing (subscribe [:is-new-path])
        new-path (subscribe [:new-path])]
    (fn []
      [:div
       [:label {:for "editing"} "Editing"]
       [:input#editing {:type "checkbox"
                        :checked @editing
                        :on-change #(dispatch [:toggle-edit])}]
       [:button {:on-click #(dispatch [:add-path @new-path])
                 :disabled (not @editing)}
        "Save"]
       [:button {:on-click #(dispatch [:undo-path-marker])
                 :disabled (not @editing)}
        "Undo"]
       [map/google-map]])))
