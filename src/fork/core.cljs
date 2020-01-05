(ns fork.core
  (:require
   [fork.logic :as logic]
   [reagent.core :as r]
   [re-frame.core :as rf]))

(defn form
  [props component]
  (let [state (r/atom {:values (or (merge (:initial-values props)
                                          (:initial-touched props))
                                   {})
                       :touched (into #{}
                                      (keys (:initial-touched props)))})
        form-id (or (:form-id props)
                    (str (gensym)))
        handlers {:set-touched (fn [& ks] (logic/set-touched ks state))
                  :set-untouched (fn [& ks] (logic/set-untouched ks state))
                  :set-values #(logic/set-values % state)
                  :disable (fn [& ks] (logic/local-disable state ks))
                  :enable (fn [& ks] (logic/local-enable state ks))
                  :disabled? #(logic/disabled? state %)
                  :handle-change #(logic/handle-change % state)
                  :handle-blur #(logic/handle-blur % state)
                  :send-server-request (fn [e f & [opt]]
                                         (logic/send-server-request
                                          e f (merge opt
                                                     {:state state
                                                      :path (:path props)})))}]
    (r/create-class
     {:component-did-mount
      #((:component-did-mount props) handlers)
      :component-will-unmount
      (fn []
        (when (:clean-on-unmount? props)
          (rf/dispatch [::logic/clean (:path props)])))
      :reagent-render
      (fn [props component]
        (let [db @(rf/subscribe [::logic/db (:path props)])
              validation (when-let [val-fn (:validation props)]
                           (logic/handle-validation @state val-fn))
              on-submit-response (get (:on-submit-response props) (:status-code db))]
          [component
           {:props (:props props)
            :state state
            :db db
            :path (:path props)
            :form-id form-id
            :values (:values @state)
            :errors validation
            :on-submit-response on-submit-response
            :touched (:touched @state)
            :set-touched (:set-touched handlers)
            :set-untouched (:set-untouched handlers)
            :submitting? (:submitting? db)
            :submit-count (:submit-count @state)
            :set-values (:set-values handlers)
            :disable (:disable handlers)
            :enable (:enable handlers)
            :disabled? (:disabled? handlers)
            :handle-change (:handle-change handlers)
            :handle-blur (:handle-blur handlers)
            :send-server-request (:send-server-request handlers)
            :handle-submit #(logic/handle-submit % (merge props
                                                          {:state state
                                                           :db db
                                                           :form-id form-id
                                                           :validation validation}))}]))})))

;; ---- Re-frame utils that can be easily extended to provide more functionality ---- ;;

(defn on-submit
  "Interceptor to be chained with the on-submit handler."
  [path]
  (logic/on-submit path))

(defn clean
  "Interceptor that can be chained to clean the whole re-frame form state or parts of it."
  [path & sub-path]
  (logic/clean path sub-path))

(defn set-submitting
  [db path bool]
  (logic/set-submitting db path bool))

(defn set-waiting
  [db path input-name bool]
  (logic/set-waiting db path input-name bool))

(defn set-status-code
  [db path status-code]
  (logic/set-status-code db path status-code))

;; ---- Input templates using Bulma CSS Framework ---- ;;

(defn input
  [{:keys [values touched handle-change handle-blur disabled?]}
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
  [{:keys [values touched handle-change handle-blur disabled?]}
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
  [{:keys [values touched handle-change handle-blur disabled?]}
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
     " " text]]])

(defn- dropdown-inner
  [active? ref
   {:keys [values handle-change handle-blur]}
   {:keys [label name names options class]}]
  [:div.field
   [:label.label label]
   [:div.dropdown
    {:class (str (when active? "is-active")
                 " " class)
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
  [props opts]
  (let [!ref (atom nil)
        active? (r/atom false)
        handler (fn [e]
                  (if (.contains @!ref (.-target e))
                    (reset! active? (not @active?))
                    (reset! active? false)))
        ref-val (fn [el] (reset! !ref el))
        names (into {} (:options opts))]
    (r/create-class
     {:display-name (str "dropdown-" name)
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
  [{:keys [values touched handle-change handle-blur disabled?]}
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
