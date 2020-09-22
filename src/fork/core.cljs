(ns fork.core
  (:require
   [clojure.data :as data]
   [clojure.walk :as walk]))

(defn- vec-remove
  [coll pos]
  (vec (concat (subvec coll 0 pos) (subvec coll (inc pos)))))

(defn touched
  [state k]
  (or (:attempted-submissions @state)
      (get (:touched @state) k)))

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
  [evt state name idx]
  (let [input-key (element-name :evt evt (:keywordize-keys @state))
        input-value (element-value evt)]
    (swap! state update-in [:values name idx] assoc input-key input-value)))

(defn fieldarray-handle-blur
  [evt state name idx]
  (let [input-key (element-name :evt evt (:keywordize-keys @state))]
    (swap! state update :touched conj [name idx input-key])))

(defn set-handle-change
  [{:keys [value path]} state]
  (let [path (if (vector? path) path [path])
        current-value (get-in @state (cons :values path))
        new-value (doall (if (fn? value) (value current-value) value))]
    (swap! state assoc-in (cons :values path) new-value)))

(defn set-handle-blur
  [{:keys [value path]} state]
  (let [path (if (vector? path) path [path])]
    (swap! state update :touched (if value conj disj) path)))

(defn fieldarray-add
  [state name m]
  (swap! state update-in [:values name] (fnil conj []) m))

(defn- fieldarray-update-touched
  [touched name idx]
  (into #{} (keep (fn [el]
                    (if (or (not (vector? el))
                            (and (vector? el)
                                 (not= name (first el))))
                      ;;to return touched element if it's not the fieldarray in question
                      el
                      ;; to delete and update indexes of fieldarray
                      (let [[n curr-idx k] el]
                        (cond
                          (> curr-idx idx) [n (dec curr-idx) k]
                          (< curr-idx idx) el
                          :else nil)))) touched)))

(defn fieldarray-remove
  [state name idx]
  (swap! state (fn [s]
                 (-> s
                     (update-in [:values name] vec-remove idx)
                     (update :touched #(fieldarray-update-touched % name idx))))))

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

(defn field-array
  [props _]
  (let [state (get-in props [:props :state])
        name (:name props)
        handlers {:set-handle-change
                  #(set-handle-change % state)
                  :set-handle-blur
                  #(set-handle-blur % state)
                  :handle-change
                  (fn [evt idx] (fieldarray-handle-change
                                 evt state name idx))
                  :handle-blur
                  (fn [evt idx] (fieldarray-handle-blur
                                 evt state name idx))
                  :remove
                  (fn [idx] (fieldarray-remove state name idx))
                  :insert
                  (fn [m] (fieldarray-add state name m))}]
    (fn [{:keys [props] :as args} component]
      (let [fields (get (:values props) name)]
        [component props
         {:fieldarray/name name
          :fieldarray/options (:options args)
          :fieldarray/fields fields
          :fieldarray/insert (:insert handlers)
          :fieldarray/remove (:remove handlers)
          :fieldarray/set-handle-change (:set-handle-change handlers)
          :fieldarray/set-handle-blur (:set-handle-blur handlers)
          :fieldarray/handle-change (:handle-change handlers)
          :fieldarray/handle-blur (:handle-blur handlers)}]))))
