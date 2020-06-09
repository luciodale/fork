(ns fork.re-frame
  (:require
   [fork.core :as core]
   [reagent.core :as r]
   [re-frame.core :as rf]))

(defn set-waiting
  [db path input-name bool]
  (core/set-waiting db path input-name bool))

(defn set-submitting
  [state path bool]
  (core/set-submitting state path bool))

(defn set-status-code
  [db path status-code]
  (core/set-status-code db path status-code))

(rf/reg-event-db
 ::server-set-waiting
 (fn [db [_ path input-key bool]]
   (assoc-in db [path :server input-key :waiting?] bool)))

(rf/reg-sub
 ::db
 (fn [db [_ path]]
   (get db path)))

(rf/reg-event-db
 ::clean
 (fn [db [_ path]]
   (dissoc db path)))

(defn form
  [props _]
  (let [state (r/atom {:values (or (merge (:initial-values props)
                                          (:initial-touched props))
                                   {})
                       :touched (into #{} (keys (:initial-touched props)))})
        path (or (:path props) ::global)
        form-id (or (:form-id props) (str (gensym)))
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
                                 :set-waiting core/set-waiting
                                 :set-waiting-true
                                 (fn [input-name]
                                   (rf/dispatch [::server-set-waiting
                                                 (:path opts) input-name true]))})))
                  :reset (fn [& [m]] (reset! state (merge {:values {}
                                                           :touched #{}}
                                                          m)))}]
    (r/create-class
     {:component-did-mount
      #(when-let [on-mount (:component-did-mount props)]
         (on-mount handlers))
      :component-will-unmount
      (fn []
        (when (:clean-on-unmount? props)
          (rf/dispatch [::clean (:path props)])))
      :reagent-render
      (fn [props component]
        (let [db @(rf/subscribe [::db (:path props)])
              validation (when-let [val-fn (:validation props)]
                           (core/handle-validation @state val-fn))
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
            :reset (:reset handlers)
            :handle-submit #(core/handle-submit % (merge props
                                                          {:state state
                                                           :set-submitting (fn [db bool]
                                                                             (set-submitting db path bool))
                                                           :set-status-code (fn [db status-code]
                                                                              (set-status-code db path status-code))
                                                           :server (:server db)
                                                           :form-id form-id
                                                           :validation validation}))}]))})))

;; Re-frame utils that can be easily extended to provide more functionality

(defn on-submit
  "Set global variables in reframe db when submitting."
  ([]
   (on-submit ::global))
  ([path]
   (rf/->interceptor
    :id :on-submit
    :before (fn [context]
              (update-in context [:coeffects :db]
                         #(assoc-in % [path :submitting?] true))))))

(defn clean
  "Clean form state or a list of specified keys from reframe db."
  [path & [sub-path]]
  (let [path (or path ::global)]
    (rf/->interceptor
     :id :clean
     :after (fn [context]
              (if sub-path
                (update-in context (concat [:effects :db path]
                                           (butlast sub-path)) dissoc (last sub-path))
                (update-in context [:effects :db] dissoc path))))))
