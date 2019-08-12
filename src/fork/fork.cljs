(ns fork.fork
  (:require
   [fork.logic :as logic]
   [reagent.core :as r]
   [re-frame.core :as rf]))

(defn fork
  [props component]
  (let [state (r/atom {:values (:initial-values props)})]
    (r/create-class
     {:component-will-unmount
      (fn []
        (when (:clean-on-unmount? props)
          (rf/dispatch [::logic/clean (:path props)])))
      :reagent-render
      (fn [props components]
        (let [props (merge props {:state state
                                  :validation (or (:validation props)
                                                  (constantly nil))})
              db @(rf/subscribe [::logic/db (:path props)])]
          [component
           {:state state
            :path (:path props)
            :values (:values @state)
            :validation (:validation props)
            :initial-values (:initial-values props)
            :errors (logic/errors (:errors @state)
                                    (:external-errors db))
            :touched (:touched @state)
            :submitting? (:submitting? db)
            :submit-count (:submit-count db)
            :set-values #(logic/set-values % props)
            :disable #(logic/local-disable props %)
            :enable #(logic/local-enable props %)
            :disabled? #(logic/disabled? (:disabled? @state) (:disabled? db) %)
            :handle-change #(logic/handle-change % props)
            :handle-blur #(logic/handle-blur % props)
            :handle-submit #(logic/handle-submit % props)}]))})))

;; ---- Re-frame utils that can be easily extended to provide more functionality ---- ;;

(defn on-submit
  "Interceptor to be chained with the on-submit handler."
  [path]
  (logic/on-submit path))

(defn clean
  "Interceptor that can be chained to clean the whole re-frame form state or parts of it."
  [path & sub-path]
  (logic/clean path sub-path))

(defn disable
  [db path & ks]
  (logic/global-disable db path ks))

(defn enable
  [db path & ks]
  (logic/global-enable db path ks))

(defn set-submitting
  [db path bool]
  (logic/set-submitting db path bool))

(defn set-external-errors
  [db path errors-map]
  (logic/set-external-errors db path errors-map))

;; ---- Input array to handle dynamically generated forms ---- ;;

(defn input-array
  [props {:keys [name component args]}]
  [component (merge
              (dissoc props :handle-change :handle-blur)
              {:array-key name
               :handle-change
               (fn [evt idx]
                 (logic/handle-on-change-input-array
                  evt props name idx))
               :handle-blur
               (fn [evt idx]
                 (logic/handle-on-blur-input-array
                  evt props name idx))
               :input-array-errors
               (fn [idx input-key err-ks]
                 (logic/input-array-errors
                  (merge props {:array-key name})
                  idx input-key err-ks))
               :add
               (fn [evt]
                 (.preventDefault evt)
                 (logic/add-to-input-array
                  props name))
               :delete
               (fn [evt idx err-ks]
                 (.preventDefault evt)
                 (logic/delete-from-input-array
                  props name idx err-ks))}) args])

;; ---- Input templates using Bulma CSS Framework ---- ;;

(defn input
  [{:keys [values errors touched handle-change handle-blur]}
   {:keys [label placeholder name type class]}]
  [:div.field {:class class}
   [:label.label label]
   [:div.control
    [:input.input
     {:name name
      :placeholder placeholder
      :type type
      :value (values name "")
      :on-change handle-change
      :on-blur handle-blur}]]
   (when (get touched name)
     (for [[k msg] (get errors name)]
       ^{:key k}
       [:p.help msg]))])

(defn textarea
  [{:keys [values errors touched handle-change handle-blur]}
   {:keys [label placeholder name class]}]
  [:div.field {:class class}
   [:label.label label]
   [:div.control
    [:textarea.textarea
     {:name name
      :value (values name "")
      :placeholder placeholder
      :on-change handle-change
      :on-blur handle-blur}]]
   (when (get touched name)
     (for [[k msg] (get errors name)]
       ^{:key k}
       [:p.help msg]))])

(defn checkbox
  [{:keys [values errors touched handle-change handle-blur]}
   {:keys [name class text]}]
  [:div.field {:class class}
   [:div.control
    [:label.checkbox
     [:input
      {:name name
       :type "checkbox"
       :checked (values name false)
       :on-change handle-change
       :on-blur handle-blur}]
     " " text]]
   (when (get touched name)
     (for [[k msg] (get errors name)]
       ^{:key k}
       [:p.help msg]))])
