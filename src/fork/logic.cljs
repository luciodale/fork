(ns fork.logic
  (:require
   [re-frame.core :as rf]))

(defn- element-value
  [evt]
  (let [type (-> evt .-target .-type)]
    (case type
      "checkbox"
      (-> evt .-target .-checked)
      (-> evt .-target .-value))))

(defn errors
  [errors & [external-errors]]
  (not-empty
   (into {}
         (filter second
                 (merge-with
                  merge
                  errors
                  external-errors)))))

(defn- generate-error-map
  "Transform the validation evaluated function
  into the errors format in the state."
  [resolved-validation]
  (zipmap (keys resolved-validation)
          (map
           (fn [vec-validation]
             (apply merge
                    (keep
                     (fn [[bool k msg]]
                       (when-not bool {k msg}))
                     vec-validation)))
           (vals resolved-validation))))

(defn- filter-validation
  [resolved-validation keys-seq]
  (into {}
        (filter (fn [[validation-key _]]
                  (or (keyword? validation-key)
                      (some #(= validation-key %) keys-seq)))
                resolved-validation)))

(defn- validation->error-map
  "Filter the validation based on the inputs
  triggering it. Keywords are kept, as they
  are used for general errors."
  ([resolved-validation]
   (generate-error-map resolved-validation))
  ([resolved-validation keys-seq]
   (->> (filter-validation resolved-validation keys-seq)
        (generate-error-map))))

(defmulti validate (fn [[location] _] location))

(defmethod validate :client
  [[_ selector] {:keys [state validation new-values]}]
  (some-> (merge (:values @state) new-values)
          validation
          :client
          selector
          (validation->error-map (keys new-values))))

#_(defmethod validate :server
  [[_ selector] {:keys [state validation new-values props]}]
  (let [values (merge (:values @state) new-values)
        filtered-validation (some-> values
                                    validation
                                    :server
                                    selector
                                    (filter-validation (keys new-values)))]
    (doseq [[k request-fn] filtered-validation]
      (request-fn (merge
                   (dissoc props
                           :handle-blur
                           :handle-change
                           :disable :enable
                           :state :validation
                           :set-values :handle-submit)
                   {:input k
                    :values values})))))

(defmethod validate :submit
  [_ {:keys [state validation]}]
  (let [resolved-validation (-> (:values @state) validation :client)
        validation-on-change (:on-change resolved-validation)
        validation-on-blur (:on-blur resolved-validation)
        validation-on-submit (:on-submit resolved-validation)
        whole-validation (merge-with
                          ;; replace with into with next cljs release
                          #(reduce conj %1 %2)
                          validation-on-change
                          validation-on-blur
                          validation-on-submit)]
    (validation->error-map whole-validation)))

(defn- gen-error-map
  "Dispatch to right validation function based on the event
  triggering the validation."
  ([{:keys [state validation]}]
   (validate [:submit]
             {:state state
              :validation validation}))
  ([{:keys [state validation] :as props}
    selector new-values]
   (validate [:client selector]
             {:state state
              :validation validation
              :new-values new-values})))

(defn set-values
  [new-values {:keys [state validation] :as props}]
  (swap! state
         #(-> %
              (update :values merge new-values)
              (update :errors merge (merge-with merge
                                     (gen-error-map props :on-blur new-values)
                                     (gen-error-map props :on-change new-values))))))

(defn- disable-logic
  [current-set ks]
  (apply conj ((fnil into #{}) current-set) ks))

(defn- enable-logic
  [current-set ks]
  (apply disj current-set ks))

(defn local-disable
  [{state :state} & ks]
  (swap! state update :disabled? #(disable-logic % ks)))

(defn local-enable
  [{state :state} & ks]
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

(defn handle-change
  "Update values and validate in one swap. If validation
  is not provided the :errors key will take an empty map."
  [evt {:keys [state validation] :as props}]
  (let [input-key (-> evt .-target .-name)
        input-value (element-value evt)
        new-values {input-key input-value}]
    (swap! state #(-> %
                      (assoc-in [:values input-key] input-value)
                      (update :errors merge (gen-error-map props :on-change new-values))))))

(defn handle-blur
  "Update touched and validate in one swap. If validation
  is not provided the :errors key will take an empty map."
  [evt {:keys [state validation] :as props}]
  (let [input-key (-> evt .-target .-name)
        input-value (element-value evt)
        values {input-key input-value}]
    (swap! state #(-> %
                      (assoc-in [:touched input-key] true)
                      (update :errors merge (gen-error-map props :on-blur values))))))

(defn handle-on-change-input-array
  "Update the value of input array."
  [evt {:keys [state validation] :as props} input-array-key idx]
  (let [input-key (-> evt .-target .-name)
        input-value (element-value evt)
        old-input-array (get (:values @state) input-array-key)
        new-input-array (assoc-in old-input-array [idx input-key] input-value)
        new-values {input-array-key new-input-array}]
    (swap! state #(-> %
                      (assoc-in [:values input-array-key] new-input-array)
                      (update :errors merge (gen-error-map props :on-change new-values))))))

(defn handle-on-blur-input-array
  "Update touched of input array."
  [evt {:keys [state validation] :as props} input-array-key idx]
  (let [input-key (-> evt .-target .-name)
        input-array-vals (get (:values @state) input-array-key)
        old-input-array-touched (get (:touched @state) input-array-key)
        new-input-array-touched (or (true? old-input-array-touched)
                                    (assoc-in old-input-array-touched
                                              [idx input-key] true))
        values {input-array-key input-array-vals}]
    (swap! state #(-> %
                      (assoc-in [:touched input-array-key] new-input-array-touched)
                      (update :errors merge (gen-error-map props :on-blur values))))))

(defn input-array-errors
  "Help retrieve the array errors per input."
  [{:keys [touched errors array-key values]}
   idx input-key error-ks]
  (when (and (or (true? (get touched array-key))
                 (get-in touched [array-key idx input-key])))
    (select-keys (get-in errors [array-key]) error-ks)))

(defn add-to-input-array
  "Add new group or individual array input."
  [{:keys [state initial-values]} input-array-key]
  (swap! state update-in [:values input-array-key]
         (fn [input-map]
           (let [sorted-idxs (sort < (keys input-map))
                 max-num (apply max sorted-idxs)
                 idx (reduce
                      (fn [old-num new-num]
                        (cond
                          (> (- new-num old-num) 1) (reduced (+ old-num 1))
                          (= new-num max-num) (+ max-num 1)
                          :else new-num)) -1
                      sorted-idxs)]
             (assoc input-map idx
                    (get-in initial-values [input-array-key 0]))))))

(defn delete-from-input-array
  "Remove group or individual array input and clean
  local state from its utils."
  [{:keys [state initial-values]} input-array-key idx err-keys]
  (swap! state
         #(-> %
              (update-in [:values input-array-key] dissoc idx)
              (update-in [:touched input-array-key]
                         (fn [touched]
                           (or (true? touched)
                               (dissoc touched idx))))
              (update-in [:errors input-array-key]
                         (fn [m] (not-empty (apply dissoc m err-keys)))))))

(defn touch-all
  [state]
  (assoc state :touched
         (let [input-names (keys (:values state))]
           (zipmap input-names
                   (take (count input-names)
                         (repeat true))))))


(defn handle-submit
  [evt {:keys [state on-submit validation
               prevent-default? initial-values]
        :as props}]
  (when prevent-default? (.preventDefault evt))
  (swap! state #(-> %
                    (touch-all)
                    (update :errors merge (gen-error-map props))))
  (on-submit
   {:errors (errors (:errors @state))
    :values (:values @state)
    :dirty? (not= (:values @state) initial-values)}))

(defn on-submit
  "Set global variables in reframe db when submitting."
  [path]
  (rf/->interceptor
   :id :on-submit
   :before (fn [context]
             (-> context
                 (assoc-in [:coeffects :db path :submitting?] true)
                 (update-in [:coeffects :db path :submit-count] inc)
                 (update-in [:coeffects :db path] dissoc :external-errors)))))

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

(defn set-external-errors
  [db path errors-map]
  (update-in db [path :external-errors] merge errors-map))
