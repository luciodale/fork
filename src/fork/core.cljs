(ns fork.core
  (:require
   [clojure.data :as data]))

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

(defn set-submitting
  [db path bool]
  (assoc-in db [path :submitting?] bool))

(defn set-waiting
  [db path input-name bool]
  (assoc-in db [path :server input-name :waiting?] bool))

(defn set-server-message
  [db path message]
  (assoc-in db [path :server-message] message))

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

(defn disable
  [state & [ks]]
  (swap! state update :disabled? #(disable-logic % ks)))

(defn enable
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

(defn dirty
  [values initial-values]
  (first (data/diff values (or initial-values {}))))

(defn handle-submit
  [evt {:keys [state server on-submit prevent-default?
               initial-values touched-values path
               validation form-id reset]}]
  (when prevent-default? (.preventDefault evt))
  (on-submit-state-updates state form-id)
  (when (and (nil? validation) (every? #(false? (:waiting? %)) (vals server)))
    (on-submit
     {:state state
      :path path
      :values (:values @state)
      :dirty (dirty (:values @state) (merge initial-values
                                            touched-values))
      :reset reset})))

(defn send-server-request
  [http-fn
   {:keys [state validation evt name value path set-waiting-true
           debounce throttle initial-values touched-values]}]
  (let [input-key name
        input-value value
        values (merge
                (:values @state)
                (when input-value
                  {input-key input-value}))
        touched (if (:on-blur evt)
                  (conj (:touched @state) input-key)
                  (:touched @state))
        props {:path path
               :dirty (dirty values (merge initial-values
                                           touched-values))
               :errors (when validation (handle-validation {:values values}
                                                           validation))
               :values values
               :touched touched
               :state state}]
    (set-waiting-true input-key)
    (cond
      debounce (do
                 (js/clearTimeout (get-in @state [:debounce input-key]))
                 (swap! state update-in [:debounce input-key]
                        (fn [] (js/setTimeout
                                #(http-fn props) debounce))))
      throttle (when (not (get-in @state [:throttle input-key]))
                 (swap! state update-in [:throttle input-key]
                        (fn [] (js/setTimeout
                                #(do
                                   (http-fn props)
                                   (swap! state update :throttle dissoc input-key))
                                throttle))))
      :else
      (http-fn props))))
