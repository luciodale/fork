(ns fieldarray
  (:require
   [reagent.core :as r]
   [fork.re-frame :as fork]
   [cljs.pprint :as pprint]))

;; Showing nested field arrays and drag and drop on top level field arrays

(defn field-array-2
  [{:keys [normalize-name]}
   {:fieldarray/keys [fields
                      name
                      insert
                      remove
                      touched
                      handle-change
                      handle-blur]}]
  [:div {:style {:padding "1em" :margin "0.5em" :background "green"}}
   (doall
    (->> fields
         (map-indexed
          (fn [idx field]
            ^{:key (str name idx)}
            [:div
             [:input
              {:name (normalize-name :field-array-2/input)
               :value (get field :field-array-2/input)
               :on-change #(handle-change % idx)
               :on-blur #(handle-blur % idx)}]
             [:button
              {:type "button"
               :on-click #(remove idx)}
              "Remove"]
             (when (touched idx :field-array-2/input)
               [:p "I have been touched!"])]))))
   [:br]
   [:button
    {:type "button"
     :on-click #(insert {:field-array-2/input (rand-nth [":)" ":D" "^_^" ":O"])})}
    "Add nested field array"]])

(defn field-array-1
  [{:keys [normalize-name] :as props}
   {:fieldarray/keys [fields
                      name
                      insert
                      remove
                      touched
                      handle-change
                      handle-blur
                      current-dragged-idx
                      next-droppable-target?
                      prev-droppable-target?
                      drag-and-drop-handlers]}]
  [:div
   (doall
    (->> fields
         (map-indexed
          (fn [idx field]
            ^{:key (str name idx)}
            [:div
             (merge
              (drag-and-drop-handlers :field-array-1 idx)
              {:style (cond-> {:padding "1em"
                               :margin "1em"
                               :background "blue"}

                        (= idx (current-dragged-idx :field-array-1))
                        (assoc :opacity 0.5)

                        (next-droppable-target? :field-array-1 idx)
                        (assoc :border-bottom "10px solid black")

                        (prev-droppable-target? :field-array-1 idx)
                        (assoc :border-top "10px solid black"))})
             [:div "DRAG ME!"]
             [:br]
             [:br]
             [:input
              {:name (normalize-name :field-array-1/input)
               :value (get field :field-array-1/input)
               :on-change #(handle-change % idx)
               :on-blur #(handle-blur % idx)}]
             (when (touched idx :field-array-1/input)
               [:p "I have been touched!"])
             [:br]
             [fork/field-array {:props props
                                :name [:field-array-1 idx :field-array-2]}
              field-array-2]
             [:br]
             [:button
              {:type "button"
               :on-click #(remove idx)}
              "Remove"]]))))
   [:button
    {:type "button"
     :on-click #(insert {:field-array-1/input "new input!"
                         :field-array-2
                         [{:field-array-2/input "new input nested!"}]})}
    "Add field array"]])

(defn view
  []
  (r/with-let [state (r/atom nil)]
    [fork/form {:state state
                :keywordize-keys true
                :initial-values {:field-array-1
                                 [{:field-array-1/input "hello1"
                                   :field-array-2
                                   [{:field-array-2/input "hello nested1"}]}
                                  {:field-array-1/input "hello2"
                                   :field-array-2
                                   [{:field-array-2/input "hello nested2"}]}
                                  {:field-array-1/input "hello3"
                                   :field-array-2
                                   [{:field-array-2/input "hello nested3"}]}]}}
     (fn [props]
       [:form.manage-pages
        [fork/field-array {:props props
                           :name :field-array-1}
         field-array-1]
        [:br]
        [:div
         [:pre (with-out-str (pprint/pprint @(:state props)))]]])]))
