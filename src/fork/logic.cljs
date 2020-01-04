(ns fork.logic
  (:require
   [re-frame.core :as rf]))

(defn element-value
  [evt]
  (let [type (-> evt .-target .-type)]
    (case type
      "checkbox"
      (-> evt .-target .-checked)
      (-> evt .-target .-value))))

(defn set-values
  [new-values {:keys [state] :as props}]
  (swap! state
         #(-> %
              (update :values merge new-values)
              (update :touched (fn [x y]
                                 (apply conj x y)) (keys new-values)))))

(defn set-touched
  [names {:keys [state]}]
  (swap! state update :touched
         (fn [x y] (apply conj x y)) names))

(defn set-untouched
  [names {:keys [state]}]
  (swap! state update :touched
         (fn [x y] (apply disj x y)) names))

(defn disable-logic
  [current-set ks]
  (apply conj ((fnil into #{}) current-set) ks))

(defn enable-logic
  [current-set ks]
  (apply disj current-set ks))

(defn local-disable
  [{state :state} & [ks]]
  (swap! state update :disabled? #(disable-logic % ks)))

(defn local-enable
  [{state :state} & [ks]]
  (swap! state update :disabled? #(enable-logic % ks)))

(defn global-disable
  [db path & [ks]]
  (update-in db [path :disabled?] #(disable-logic % ks)))

(defn global-enable
  [db path & [ks]]
  (update-in db [path :disabled?] #(enable-logic % ks)))

(defn disabled?
  [local global k]
  (get (clojure.set/union local global) k))

(defn handle-validation
  [state {:keys [validation]}]
  (let [values (:values state)
        resolved (validation values)]
    (when-not (every? empty? resolved) resolved)))

(defn handle-change
  [evt {:keys [state] :as props}]
  (let [input-key (-> evt .-target (.getAttribute "name"))
        input-value (element-value evt)]
    (swap! state update :values assoc input-key input-value)))

(defn handle-blur
  [evt {:keys [state]}]
  (let [input-key (-> evt .-target .-name)]
    (swap! state update :touched conj input-key)))

(defn on-submit-state-updates
  [state form-id]
  (let [input-names (->> (.-elements
                          (js/document.getElementById form-id))
                         array-seq
                         (mapv #(.getAttribute % "name"))
                         (remove nil?))]
    (swap! state
           #(-> %
                (update :touched
                        (fn [x y]
                          (apply conj x y))
                        input-names)
                (update :submit-count inc)))))

(defn dirty?
  [values initial-values]
  (cond
    (and (seq values) (seq initial-values))
    (every? false?
            (mapv
             (fn [[k v]]
               (= v (or (get initial-values k) "")))
             values))
    (seq values) (some? (some not-empty (vals values)))
    (and (empty? values) (empty? initial-values)) false
    :else true))

(defn handle-submit
  [evt {:keys [state db on-submit prevent-default?
               initial-values validation form-id]}]
  (when prevent-default? (.preventDefault evt))
  (on-submit-state-updates state form-id)
  (when (and (nil? validation) (every? #(false? (:waiting? %))
                                       (vals (:server db))))
    (on-submit
     {:values (:values @state)
      :dirty? (dirty? (:values @state) initial-values)})))

(rf/reg-event-db
 ::server-set-waiting
 (fn [db [_ path input-key]]
   (assoc-in db [path :server input-key :waiting?] true)))

(defn send-server-request
  [evt http-fn {:keys [state path debounce]}]
  (let [input-key (-> evt .-target (.getAttribute "name"))
        input-value (element-value evt)
        new-values (merge
                    (:values @state)
                    {input-key input-value})]
    (if debounce
      (do
        (js/clearTimeout (get-in @state [:debounce input-key]))
        (rf/dispatch [::server-set-waiting path input-key])
        (swap! state update-in [:debounce input-key]
               (fn [] (js/setTimeout
                       #(http-fn new-values) debounce))))
      (do
        (rf/dispatch [::server-set-waiting path input-key])
        (http-fn new-values)))))

(defn on-submit
  "Set global variables in reframe db when submitting."
  [path]
  (rf/->interceptor
   :id :on-submit
   :before (fn [context]
             (update-in context
                        [:coeffects :db]
                        (fn [db]
                          (-> db
                              (assoc-in [path :submitting?] true)
                              (update path dissoc :external-errors)))))))

(defn clean
  "Clean form state or a list of specified keys from reframe db."
  [path & [sub-path]]
  (rf/->interceptor
   :id :clean
   :after (fn [context]
            (if sub-path
              (update-in context (concat [:effects :db path]
                                         (butlast sub-path)) dissoc (last sub-path))
              (update-in context [:effects :db] dissoc path)))))

(rf/reg-sub
 ::db
 (fn [db [_ path]]
   (get db path)))

(rf/reg-event-db
 ::clean
 (fn [db [_ path]]
   (dissoc db path)))

(defn set-submitting
  [db path bool]
  (assoc-in db [path :submitting?] bool))

(defn set-waiting
  [db path input-name bool]
  (assoc-in db [path :server input-name :waiting?] bool))

(defn set-external-errors
  [db path errors-map]
  (update-in db [path :external-errors] merge errors-map))
