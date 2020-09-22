(ns multiple-dropdown
  (:require
   [fork.reagent :as fork]))

(defn view []
  [fork/form {:initial-values {"status" []}}
   (fn [{:keys [set-handle-change handle-blur values]}]
     [:form
      [:select
       {:multiple true
        :name "status"
        :value (values "status")
        :on-change #(set-handle-change
                     {:value (let [opts (array-seq (-> % .-target .-options))]
                               (keep (fn [x] (when (.-selected x) (.-value x))) opts))
                      :path ["status"]})
        :on-blur handle-blur}
       [:option {:value "married"}
        "Married"]
       [:option {:value "single"}
        "Single"]
       [:option {:value "pns"}
        "Prefer Not To Say"]]])])
