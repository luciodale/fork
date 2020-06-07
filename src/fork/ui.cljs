(ns fork.ui
  (:require
   [reagent.core :as r]))

;; UI components using Bulma CSS Framework

(defn input
  [{:keys [values handle-change handle-blur disabled?]}
   {:keys [label placeholder name type class]}]
  [:div.field {:class class}
   [:label.label label]
   [:div.control
    [:input.input
     {:name name
      :placeholder placeholder
      :type type
      :value (values name "")
      :disabled (disabled? name)
      :on-change handle-change
      :on-blur handle-blur}]]])

(defn textarea
  [{:keys [values handle-change handle-blur disabled?]}
   {:keys [label placeholder name class]}]
  [:div.field {:class class}
   [:label.label label]
   [:div.control
    [:textarea.textarea
     {:name name
      :value (values name "")
      :placeholder placeholder
      :disabled (disabled? name)
      :on-change handle-change
      :on-blur handle-blur}]]])

(defn checkbox
  [{:keys [values handle-change handle-blur disabled?]}
   {:keys [name class text]}]
  [:div.field {:class class}
   [:div.control
    [:label.checkbox
     [:input
      {:name name
       :type "checkbox"
       :checked (values name false)
       :disabled (disabled? name)
       :on-change handle-change
       :on-blur handle-blur}]
     (str " " text)]]])

(defn- dropdown-inner
  [active? ref
   {:keys [values handle-change]}
   {:keys [label name names options class]}]
  [:div.field
   [:label.label label]
   [:div.dropdown
    {:class (str (when active? "is-active") " " class)
     :ref ref}
    [:div.dropdown-trigger
     [:div.button
      {:aria-haspopup "true"
       :aria-controls (str "dropdown-" name)}
      [:span (get names (values name))]
      [:span.icon.is-small
       [:i.fas.fa-angle-down
        {:aria-hidden "true"}]]]]
    [:div.dropdown-menu
     {:id (str "dropdown-" name)
      :role "menu"}
     [:div.dropdown-content
      (for [option options]
        ^{:key (ffirst option)}
        [:a.dropdown-item
         [:option
          {:name name
           :value (ffirst option)
           :on-click handle-change}
          (first (vals option))]])]]]])

(defn pretty-dropdown
  [_ opts]
  (let [!ref (atom nil)
        active? (r/atom false)
        handler (fn [e]
                  (if (.contains @!ref (.-target e))
                    (reset! active? (not @active?))
                    (reset! active? false)))
        ref-val (fn [el] (reset! !ref el))
        names (into {} (:options opts))
        display-name (:display-name opts)]
    (r/create-class
     {:display-name display-name
      :component-did-mount
      (fn []
        (js/document.addEventListener
         "mouseup" handler))
      :component-will-unmount
      (fn []
        (js/document.removeEventListener
         "mouseup" handler))
      :reagent-render
      (fn [props opts]
        [dropdown-inner
         @active? ref-val
         props (merge opts {:names names})])})))

(defn dropdown
  [{:keys [values handle-change handle-blur disabled?]}
   {:keys [label name options class]}]
  [:div.field
   {:class class}
   [:label.label label]
   [:div.control
    [:div.select
     [:select
      {:name name
       :value (values name "")
       :on-change handle-change
       :on-blur handle-blur
       :disabled (disabled? name)}
      (for [option options]
        ^{:key (ffirst option)}
        [:option
         {:value (ffirst option)}
         (first (vals option))])]]]])
