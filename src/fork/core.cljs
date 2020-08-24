(ns fork.core
  (:require
   [clojure.data :as data]
   [clojure.walk :as walk]))

(defn initialize-state
  [props]
  (let [kw? (:keywordize-keys props)
        values (or (merge (:initial-values props)
                          (:initial-touched props))
                   {})]
    {:keywordize-keys (:keywordize-keys props)
     :values (if kw? (walk/keywordize-keys values) values)
     ;; TODO - support nested initial-touched keys
     :touched (into #{} (map (if kw? keyword identity)
                             (keys (:initial-touched props))))}))

(defn element-value
  [evt]
  (let [type (-> evt .-target .-type)]
    (case type
      "checkbox"
      (-> evt .-target .-checked)
      (-> evt .-target .-value))))

(defn element-name
  [t v keywordize?]
  (let [el-name (case t
                  :evt (-> v .-target (.getAttribute "name"))
                  :node (-> v (.getAttribute "name"))
                  v)]
    (if keywordize? (keyword el-name) el-name)))

(defn normalize-name
  [k {:keys [keywordize-keys]}]
  (if (and keywordize-keys (keyword? k))
    (subs (str k) 1)
    k))

(defn set-values
  [new-values state]
  (swap! state
         #(-> %
              (update :values merge new-values)
              (update :touched (fn [x y]
                                 (apply conj x y)) (keys new-values)))))

(defn set-submitting
  [db path bool]
  (assoc-in db (concat path [:submitting?]) bool))

(defn set-waiting
  [db path input-name bool]
  (assoc-in db (concat path [:server input-name :waiting?]) bool))

(defn set-server-message
  [db path message]
  (assoc-in db (concat path [:server-message]) message))

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
  (let [input-key (element-name :evt evt (:keywordize-keys @state))
        input-value (element-value evt)]
    (swap! state update :values assoc input-key input-value)))

(defn handle-blur
  [evt state]
  (let [input-key (element-name :evt evt (:keywordize-keys @state))]
    (swap! state update :touched conj input-key)))

(defn on-submit-state-updates
  [state form-id]
  (let [input-names (->> (.-elements
                          (js/document.getElementById form-id))
                         array-seq
                         (mapv #(element-name :node % (:keywordize-keys @state)))
                         (remove nil?))]
    (swap! state
           #(-> %
                (update :touched
                        (fn [x y]
                          (apply conj x y))
                        input-names)
                (update :attempted-submissions inc)))))

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
    (swap! state update :successful-submissions inc)
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
