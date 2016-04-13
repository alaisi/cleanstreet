(ns cleanstreet.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent]
            [re-frame.core :refer [register-sub]]))

(register-sub
 :paths
 (fn [db _]
   (reaction (get-in @db [:maps :cleaned :paths]))))

(register-sub
 :is-new-path
 (fn [db _]
   (reaction (get-in @db [:local :editing]))))

(register-sub
 :new-path
 (fn [db _]
   (reaction (get-in @db [:local :path]))))
