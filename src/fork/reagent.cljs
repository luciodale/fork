(ns fork.reagent
  (:require
   [fork.core :as core]
   [reagent.core :as r]))

(defn set-waiting
  [state path input-name bool]
  (core/set-waiting state path input-name bool))

(defn set-submitting
  [state path bool]
  (core/set-submitting state path bool))

(defn set-server-message
  [state path message]
  (core/set-server-message state path message))

(defn set-error
  [state path input-name message]
  (core/set-error state path input-name message))

(defn retrieve-event-value
  [evt]
  (core/element-value evt))

(defn- server-dispatch-logic
  [state config path]
  (let [set-waiting? (core/config-set-waiting? config)
        input-names (:clean-on-refetch config)]
    (swap! state (fn [s]
                   (cond-> s
                     (not-empty input-names)
                     (update-in path (fn [m] (apply update m :server dissoc input-names)))

                     set-waiting?
                     (core/set-waiting path (:name config) true))))))

(defn field-array
  [props component]
  [core/field-array props component])

(defn form
  [props _]
  (let [state (r/atom (core/initialize-state props))
        p (:path props)
        path (cond
               (and p (vector? p)) p
               (keyword? p) (vector p)
               :else [::global])
        form-id (or (:form-id props) (str (gensym)))
        handlers {:touched (fn [k] (core/touched state k))
                  :set-touched (fn [& ks] (core/set-touched ks state))
                  :set-untouched (fn [& ks] (core/set-untouched ks state))
                  :set-values #(core/set-values % state)
                  :disable (fn [& ks] (core/disable state ks))
                  :enable (fn [& ks] (core/enable state ks))
                  :disabled? #(core/disabled? state %)
                  :normalize-name #(core/normalize-name % props)
                  :set-handle-change #(core/set-handle-change % state)
                  :set-handle-blur #(core/set-handle-blur % state)
                  :handle-change #(core/handle-change % state)
                  :handle-blur #(core/handle-blur % state)
                  :send-server-request
                  (fn [config callback]
                    (core/send-server-request
                     callback (merge config
                                     props
                                     {:path path
                                      :state state
                                      :server-dispatch-logic
                                      #(server-dispatch-logic state config path)})))
                  :reset (fn [& [m]] (reset! state (merge {:values {}
                                                           :touched #{}}
                                                          m)))}]
    (r/create-class
     {:component-did-mount
      #(when-let [on-mount (:component-did-mount props)]
         (on-mount handlers))
      :reagent-render
      (fn [props component]
        (let [validation (when-let [val-fn (:validation props)]
                           (core/handle-validation @state val-fn))
              server-validation (core/resolve-server-validation
                                 (get-in @state (conj path :server)))
              on-submit-server-message (get-in @state (concat path [:server-message]))
              submitting? (get-in @state (concat path [:submitting?]))]
          [component
           {:props (:props props)
            :state state
            :path path
            :form-id form-id
            :values (:values @state)
            :errors validation
            :server-errors server-validation
            :on-submit-server-message on-submit-server-message
            :touched (:touched handlers)
            :set-touched (:set-touched handlers)
            :set-untouched (:set-untouched handlers)
            :submitting? submitting?
            :attempted-submissions (or (:attempted-submissions @state) 0)
            :successful-submissions (or (:successful-submissions @state) 0)
            :set-values (:set-values handlers)
            :disable (:disable handlers)
            :enable (:enable handlers)
            :disabled? (:disabled? handlers)
            :normalize-name (:normalize-name handlers)
            :set-handle-change (:set-handle-change handlers)
            :set-handle-blur (:set-handle-blur handlers)
            :handle-change (:handle-change handlers)
            :handle-blur (:handle-blur handlers)
            :send-server-request (:send-server-request handlers)
            :reset (:reset handlers)
            :handle-submit (fn [evt]
                             (core/handle-submit evt (merge props
                                                            {:path path
                                                             :state state
                                                             :server (get-in @state (concat path [:server]))
                                                             :form-id form-id
                                                             :validation validation
                                                             :already-submitting? submitting?
                                                             :reset (:reset handlers)})))}]))})))
