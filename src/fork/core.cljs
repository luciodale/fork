(ns fork.core
  (:require
   [clojure.data :as data]
   [reagent.core :as r]))

(defn- vec-remove
  [coll pos]
  (vec (concat (subvec coll 0 pos) (subvec coll (inc pos)))))

(defn touched
  [state k]
  (or (:attempted-submissions @state)
      (get (:touched @state) k)))

(defn initialize-state
  [props]
  (let [values (or (merge (:initial-values props)
                          (:initial-touched props))
                   {})
        initialized-state {:keywordize-keys (:keywordize-keys props)
                           :values values
                           :touched (into #{} (keys (:initial-touched props)))}]
    (if-let [user-provided-state (:state props)]
      (do (swap! user-provided-state merge initialized-state)
          user-provided-state)
      (r/atom initialized-state))))

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

(defn vectorize-path
  [path]
  (if (vector? path) path [path]))

(defn set-submitting
  [db path bool]
  (assoc-in db (concat (vectorize-path path) [:submitting?]) bool))

(defn set-waiting
  [db path input-name bool]
  (assoc-in db (concat (vectorize-path path) [:server input-name :waiting?]) bool))

(defn set-server-message
  [db path message]
  (assoc-in db (concat (vectorize-path path) [:server-message]) message))

(defn set-error
  [db path input-name message]
  (assoc-in db (concat (vectorize-path path) [:server input-name :errors]) message))

(defn resolve-server-validation
  [m]
  (not-empty
   (into {}
         (keep (fn [[k v]]
                 (when-let [err (:errors v)]
                   {k err}))
               m))))

(defn config-set-waiting?
  [config]
  (let [x (get config :set-waiting? :no-key)]
    (if (= :no-key x) true x)))

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

(defn fieldarray-handle-change
  [evt state vec-field-array-key idx]
  (let [path (conj vec-field-array-key idx)
        input-key (element-name :evt evt (:keywordize-keys @state))
        input-value (element-value evt)]
    (swap! state update-in (cons :values path) assoc input-key input-value)))

(defn fieldarray-handle-blur
  [evt state vec-field-array-key idx]
  (let [input-key (element-name :evt evt (:keywordize-keys @state))
        path (conj vec-field-array-key idx input-key)]
    (swap! state update :touched conj path)))

(defn set-handle-change
  [{:keys [value path]} state]
  (let [path (vectorize-path path)
        current-value (get-in @state (cons :values path))
        new-value (if (fn? value) (value current-value) value)
        resolved-new-value (if (seq? new-value)
                             (doall new-value)
                             new-value)]
    (swap! state assoc-in (cons :values path) resolved-new-value)))

(defn set-handle-blur
  [{:keys [value path]} state]
  (let [path (vectorize-path path)]
    (swap! state update :touched (if value conj disj) path)))

(defn fieldarray-insert
  [state vec-field-array-key m]
  (swap! state update-in (cons :values vec-field-array-key) (fnil conj []) m))

(defn- fieldarray-update-touched
  [touched path idx]
  (let [path-count (count path)]
    (->> touched
         (keep (fn [touched-el]
                 (if ;; to filter out only the relevant fieldarray groups
                     (and (vector? touched-el)
                          (= path (vec (take path-count touched-el))))
                   (let [[position curr-idx] (->> touched-el
                                                  (map-indexed #(when (number? %2) [%1 %2]))
                                                  (remove nil?)
                                                  (last))]
                     (cond
                       (> curr-idx idx) (update touched-el position dec)
                       (< curr-idx idx) touched-el
                       ;; remove field array group being deleted
                       :else nil))
                   touched-el)))
         (into #{}))))

(defn fieldarray-remove
  [state vec-field-array-key idx]
  (swap! state (fn [s]
                 (-> s
                     (update-in (cons :values vec-field-array-key) vec-remove idx)
                     (update :touched #(fieldarray-update-touched % vec-field-array-key idx))))))

(defn dirty
  [values initial-values]
  (first (data/diff values (or initial-values {}))))

(defn handle-submit
  [evt {:keys [state server on-submit prevent-default?
               initial-values touched-values path
               validation already-submitting? reset]}]
  (when prevent-default? (.preventDefault evt))
  (swap! state update :attempted-submissions inc)
  (when (and (not already-submitting?)
             (nil? validation)
             (nil? (resolve-server-validation server))
             (every? #(false? (:waiting? %)) (vals server)))
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
   {:keys [state validation evt name value path server-dispatch-logic
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
    (server-dispatch-logic)
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

(defn fieldarray-touched
  [state vec-field-array-key idx input-key]
  (or (:attempted-submissions @state)
      (get (:touched @state) (conj vec-field-array-key idx input-key))))

(defn field-array
  [props _]
  (let [state (get-in props [:props :state])
        field-array-key (:name props)
        vec-field-array-key (vectorize-path field-array-key)
        handlers {:set-handle-change
                  #(set-handle-change % state)
                  :set-handle-blur
                  #(set-handle-blur % state)
                  :handle-change
                  (fn [evt idx] (fieldarray-handle-change
                                 evt state vec-field-array-key idx))
                  :handle-blur
                  (fn [evt idx] (fieldarray-handle-blur
                                 evt state vec-field-array-key idx))
                  :remove
                  (fn [idx] (fieldarray-remove state vec-field-array-key idx))
                  :insert
                  (fn [m] (fieldarray-insert state vec-field-array-key m))
                  :touched
                  (fn [idx input-key] (fieldarray-touched
                                       state vec-field-array-key idx input-key))}]
    (fn [{:keys [props] :as args} component]
      (let [fields (get-in (:values props) vec-field-array-key)]
        [component props
         {:fieldarray/name field-array-key
          :fieldarray/options (:options args)
          :fieldarray/fields fields
          :fieldarray/touched (:touched handlers)
          :fieldarray/insert (:insert handlers)
          :fieldarray/remove (:remove handlers)
          :fieldarray/set-handle-change (:set-handle-change handlers)
          :fieldarray/set-handle-blur (:set-handle-blur handlers)
          :fieldarray/handle-change (:handle-change handlers)
          :fieldarray/handle-blur (:handle-blur handlers)}]))))
