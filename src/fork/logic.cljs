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
  [new-values state]
  (swap! state
         #(-> %
              (update :values merge new-values)
              (update :touched (fn [x y]
                                 (apply conj x y)) (keys new-values)))))

(defn set-touched
  [names state]
  (swap! state update :touched
         (fn [x y] (apply conj x y)) names))

(defn set-untouched
  [names state]
  (swap! state update :touched
         (fn [x y] (apply disj x y)) names))

(defn disable-logic
  [current-set ks]
  (apply conj ((fnil into #{}) current-set) ks))

(defn enable-logic
  [current-set ks]
  (apply disj current-set ks))

(defn local-disable
  [state & [ks]]
  (swap! state update :disabled? #(disable-logic % ks)))

(defn local-enable
  [state & [ks]]
  (swap! state update :disabled? #(enable-logic % ks)))

(defn disabled?
  [state k]
  (get (:disabled? @state) k))

(defn handle-validation
  [state validation]
  (let [values (:values state)
        resolved (validation values)]
    (when-not (every? empty? resolved) resolved)))

(defn handle-change
  [evt state]
  (let [input-key (-> evt .-target (.getAttribute "name"))
        input-value (element-value evt)]
    (swap! state update :values assoc input-key input-value)))

(defn handle-blur
  [evt state]
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
               initial-values touched-values
               validation form-id]}]
  (when prevent-default? (.preventDefault evt))
  (on-submit-state-updates state form-id)
  (when (and (nil? validation) (every? #(false? (:waiting? %))
                                       (vals (:server db))))
    (on-submit
     {:values (:values @state)
      :dirty? (dirty? (:values @state) (merge initial-values
                                              touched-values))})))

(rf/reg-event-db
 ::server-set-waiting
 (fn [db [_ path input-key]]
   (assoc-in db [path :server input-key :waiting?] true)))

(defn send-server-request
  [evt http-fn {:keys [state path debounce throttle]}]
  (let [input-key (-> evt .-target (.getAttribute "name"))
        input-value (element-value evt)
        new-values (merge
                    (:values @state)
                    {input-key input-value})]
    (rf/dispatch [::server-set-waiting path input-key])
    (cond
      debounce (do
                 (js/clearTimeout (get-in @state [:debounce input-key]))
                 (swap! state update-in [:debounce input-key]
                        (fn [] (js/setTimeout
                                #(http-fn new-values) debounce))))
      throttle (when (not (get-in @state [:throttle input-key]))
                 (swap! state update-in [:throttle input-key]
                        (fn [] (js/setTimeout
                                #(do
                                   (http-fn new-values)
                                   (swap! state update :throttle dissoc input-key))
                                throttle))))
      :else
      (http-fn new-values))))

(defn on-submit
  "Set global variables in reframe db when submitting."
  [path]
  (rf/->interceptor
   :id :on-submit
   :before (fn [context]
             (update-in context [:coeffects :db]
                        #(assoc-in % [path :submitting?] true)))))

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

(defn set-status-code
  [db path status-code]
  (assoc-in db [path :status-code] status-code))
