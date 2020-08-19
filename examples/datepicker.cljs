(ns examples.datepicker
  (:require
   ;; for CSS use:
   ;; https://unpkg.com/react-dates@16.3.2/lib/css/_datepicker.css
   [cljsjs.react-dates]
   [cljs.pprint :as pprint]
   [fork.reagent :as fork]))

(defn react-dates-single-date-picker
  [k {:keys [values state set-values]}]
  [:> js/ReactDates.SingleDatePicker
   {:date (get values k)
    :display-format "DD MMM YYYY"
    :on-focus-change (fn [e]
                       (swap! state #(assoc-in % [:focus k] (.-focused e))))
    :focused (get-in @state [:focus k])
    :on-date-change #(set-values {k %})}])

(defn react-dates-date-range-picker
  [k {:keys [values state set-values]}]
  [:> js/ReactDates.DateRangePicker
   {:start-date (get-in values [k :startDate])
    :end-date (get-in values [k :endDate])
    :display-format "DD MMM YYYY"
    :on-focus-change (fn [e]
                       (swap! state #(assoc-in % [:focus k] e)))
    :focused-input (get-in @state [:focus k])
    :on-dates-change #(set-values {k (js->clj % :keywordize-keys true)})}])

(defn view []
  [:<>
   [fork/form
    {:keywordize-keys true
     :prevent-default? true
     :on-submit #(js/alert (:values %))
     :initial-values {:dummy/input "Just an input"
                      :single-date-picker (js/moment)
                      :date-range-picker {:startDate (js/moment)}}}
    (fn [{:keys [state
                 form-id
                 values
                 normalize-name
                 handle-change
                 handle-blur
                 handle-submit] :as props}]
      [:div
       [:pre (with-out-str (pprint/pprint @state))]
       [:h3 "This is an example of how to wire up react dates to fork."]
       [:p "The datepickers share very common APIs, so the implementation details can be easily ported"]
       [:form
        {:id form-id
         :on-submit handle-submit}
        [:div
         [:div
          [:label "Dummy input"]]
         [:input
          {:type "text"
           :name (normalize-name :dummy/input)
           :value (:dummy/input values)
           :on-change handle-change
           :on-blur handle-blur}]]
        [:br]
        [:div
         [react-dates-single-date-picker :single-date-picker props]]
        [:br]
        [:div
         [react-dates-date-range-picker :date-range-picker props]]
        [:br]
        [:button
         {:type "submit"}
         "Submit!"]]])]])
