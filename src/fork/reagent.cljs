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

(defn form
  [props _]
  (let [state (r/atom {:values (or (merge (:initial-values props)
                                          (:initial-touched props))
                                   {})
                       :touched (into #{} (keys (:initial-touched props)))})
        path (or (:path props) ::global)
        form-id (or (:form-id props)
                    (str (gensym)))
        handlers {:set-touched (fn [& ks] (core/set-touched ks state))
                  :set-untouched (fn [& ks] (core/set-untouched ks state))
                  :set-values #(core/set-values % state)
                  :disable (fn [& ks] (core/disable state ks))
                  :enable (fn [& ks] (core/enable state ks))
                  :disabled? #(core/disabled? state %)
                  :handle-change #(core/handle-change % state)
                  :handle-blur #(core/handle-blur % state)
                  :send-server-request
                  (fn [e f & [opts]]
                    (core/send-server-request
                     e f (merge opts
                                {:state state
                                 :path path
                                 :set-waiting-true
                                 (fn [input-name]
                                   (swap! state #(core/set-waiting %
                                                                   path
                                                                   input-name
                                                                   true)))})))
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
              on-submit-server-message (get-in @state [path :server-message])]
          [component
           {:props (:props props)
            :state state
            :path path
            :form-id form-id
            :values (:values @state)
            :errors validation
            :on-submit-server-message on-submit-server-message
            :touched (:touched @state)
            :set-touched (:set-touched handlers)
            :set-untouched (:set-untouched handlers)
            :submitting? (get-in @state [path :submitting?])
            :submit-count (:submit-count @state)
            :set-values (:set-values handlers)
            :disable (:disable handlers)
            :enable (:enable handlers)
            :disabled? (:disabled? handlers)
            :handle-change (:handle-change handlers)
            :handle-blur (:handle-blur handlers)
            :send-server-request (:send-server-request handlers)
            :reset (:reset handlers)
            :handle-submit (fn [evt]
                             (core/handle-submit evt (merge props
                                                            {:path path
                                                             :state state
                                                             :server (get-in @state [path :server])
                                                             :form-id form-id
                                                             :validation validation})))}]))})))
