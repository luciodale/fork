<p align="center">
  <img src="https://imgur.com/baEth6W.jpg" alt="drawing" width="150"/>
</p>

Yet another Form Library to add to the list. Inspired by [Formik](https://github.com/jaredpalmer/formik).

*For Reagent & Re-frame*

[LIVE DEMO HERE](https://luciodale.github.io/fork/)

[![Clojars Project](https://img.shields.io/clojars/v/fork.svg)](https://clojars.org/fork)

## Why Fork?
Forms are hard. Orchestrating their ever changing state, complex logic, and rich UI elements is quite a challenging task.

**Fork** tries to establish a pattern by abstracting away the bits of code that can be generalized to shift the developer's focus on the features implementation. The following points represent the pillars *Fork* was built upon:

* Control - *You code your form components*
* Adaptable - *Plug it in and take only what you need*
* Separation of concerns - *Logic and UI live in different places*
* CSS Free - *You manage the style*

It's worth to point out that from v2.0.0 this library doesn't necessarily require re-frame. In fact, you can use it exclusively with Reagent.

As at this state you must be dying of curiosity, I will dive right into the implementation details, hoping that this will help you save the day... and some nerves.

## API

### Require Fork

#### In Deps

```clojure
fork {:mvn/version "2.4.3"}
```

or

```clojure
fork {:git/url "https://github.com/luciodale/fork.git"
      :sha "sha commit"}
 ```

#### In Namespace

```clojure
(ns your.namespace
  (:require
    ;; depending on what you want to use
	[fork.re-frame :as fork]
	[fork.reagent :as fork-reagent]))
```

Note that the APIs of `fork.re-frame` and `fork.reagent` are identical, so I will be using only the former in the following examples.

### The Bare Minimum

```clojure
(defn my-form
  [{:keys [values handle-change handle-blur]}]
  [:div
   [:p "Read back: " (values "input")]
   [:input
    {:name "input"
     :value (values "input")
     :on-change handle-change
     :on-blur handle-blur}]])

(defn foo []
  [fork/form {:initial-values
              {"input" "hello"}}
   my-form])
 ```

Notice that *Fork* takes only two parameters. The first one is a map of config, and the second one is a function that returns your form component. Many API helpers are accessible as first argument of the form function that wraps your form component.

Starting from the config map, `:initial-values` might be provided to make *Fork* aware of any of your prefilled form values. Make sure to match the `:name` of your inputs with what you define in the `:initial-values` map to successfully link up the two. You may want to use keywords for input names. If you do, remember to set the option `:keywordize-keys` to true. If you don't need to set default values for your fields, you can simply discard the key.

### Can I go anonymous?

You can also return your component in an anonymous function, but you have to be careful not to cause unwanted re-renderings by updating any external state. The following code is an example of what you want to avoid, when using an anonymous function:

```clojure
;;!!!!! BAD CODE !!!!!

(defn foo []
  (let [external-input (r/atom nil)]
    (fn []
      [:div
       [:input
        {:value @external-input
         :on-change #(reset! external-input
                             (-> % .-target .-value))}]
       [fork/form {}
        (fn [{:keys [values
                     form-id
                     handle-change
                     handle-blur] :as props}]
          [:form
           {:id form-id}
           [:input
            {:name "name"
             :value (values "name")
             :on-change #(do (reset! external-input "some-value")
                             (handle-change %))
             :on-blur handle-blur}]])]])))

;;!!!!! BAD CODE !!!!!

```

Briefly, the `"name"` input will lose focus every time its `:on-change` event is dispatched. This happens because the handler creates a new value for the `external-input` state, which sparks the re-rendering of the whole `foo` component.

As a solution, you might keep the anonymous function in place as long as you remember to use the `foo` component exclusively for the logic related to `fork/form`.

### How do I submit a form?

```clojure
(ns your.namespace
  (:require
   [fork.re-frame :as fork]
   [re-frame.core :as rf]))

(rf/reg-event-fx
 :submit-handler
 (fn [{db :db} [_ {:keys [values dirty path]}]]
   ;; dirty tells you whether the values have been touched before submitting.
   ;; Its possible values are nil or a map of changed values
   {:db (fork/set-submitting db path true)
    :dispatch-later [{:ms 1000
                      :dispatch [:resolved-form path values]}]}))

(rf/reg-event-fx
 :resolved-form
 (fn [{db :db} [_ path values]]
   (js/alert values)
   {:db (fork/set-submitting db path false)}))

(defn foo []
  [fork/form {:path [:form]
              :form-id "form-id"
              :prevent-default? true
              :clean-on-unmount? true
              :on-submit #(rf/dispatch [:submit-handler %])}
    (fn [{:keys [values
                 form-id
                 handle-change
                 handle-blur
                 submitting?
                 handle-submit]}]
      [:form
       {:id form-id
        :on-submit handle-submit}
       [:input
        {:name "input"
         :value (values "input")
         :on-change handle-change
         :on-blur handle-blur}]
       [:button
        {:type "submit"
         :disabled submitting?}
         "Submit Form"]])])
```

Let's examine what has been added step by step:

* Require Re-frame in your namespace
* Create a Re-frame effect that will be called upon submitting
* Pass the newly created effect to *Fork* along with `:path` and other options
* Destructure `handle-submit` and `submitting?` to be used in your UI
* Wrap your inputs in a form tag and add a submit button

If some parts look a bit obscure, they will be explained thoroughly in the following paragraphs.

#### Params

`:form-id` makes fork aware of your form elements. If it is not specified, a random id will be generated and will be provided through the same `:form-id` key. - Key

`:state` lets you pass a ratom that will be used as a db. Useful if you need to access it outside the fork component. - Ratom

`:path` lets you choose where to store your form global events i.e. server related stuff. - Keyword/String OR Vector of keys

`:keywordize-keys` allows you to work with keywords instead of strings. - Boolean

`:prevent-default?` does not automatically send your form to the server on submit. - Boolean

`:clean-on-unmount?` resets the state when your component is unmounted. (Useful when used with re-frame). - Boolean

`:validation` to pass a validation function that gives you the form values in a map as single param. - Function

`:initial-values` to pre-populate the inputs. - Map

`:initial-touched` to pre-populate the inputs and set them as touched. - Map

`:on-submit` lets you write your own submit logic. It gives you a map with `:state :path :values :dirty :reset` keys. - Function

`:component-did-mount` to perform any logic after the component is mounted. It takes a function and provides one argument that consists of a map of handlers: `set-touched, set-untouched, set-values, disable, enable, disabled?, handle-change, handle-blur, send-server-request` - Function

#### The Flow

After clicking the submit button, your `:on-submit` function is invoked. Remember to set submitting? to `true` with the handler `fork/set-submitting`. After your eventual ajax call, do not forget to set the submitting? value back to false with the same handler to handle the form life cycle.

You probably want to know more than the same old *Hello World* demonstration. Hence, I have prepared a better example that includes a server request and shows better what *Fork* can do for you.

```clojure
(ns your.namespace
  (:require
   [ajax.core :as ajax]
   [day8.re-frame.http-fx]
   [fork.re-frame :as fork]
   [re-frame.core :as rf]))

(rf/reg-event-fx
 :success
 (fn [{db :db} [_ result path]]
   {:db (-> db
            (assoc :result result)
            (fork/set-submitting path false)
            (fork/set-server-message path "Registration successful!"))}))

(rf/reg-event-fx
 :failure
 (fn [{db :db} [_ result path]]
   {:db (-> db
            (fork/set-submitting path false)
            (fork/set-server-message path "Registration failed!"))}))

(rf/reg-event-fx
 :submit-handler
 (fn [{db :db} [_ {:keys [values path]}]]
   {:db (fork/set-submitting db path true)
    :http-xhrio
    {:method :post
     :uri "/submit-form"
     :params values
     :timeout 2000
     :format (ajax/transit-request-format)
     :response-format (ajax/transit-response-format)
     :on-success [:success path]
     :on-failure [:failure path]}}))

(defn foo []
  [fork/form {:form-id "id"
              :path [:form]
              :prevent-default? true
              :clean-on-unmount? true
              :on-submit #(rf/dispatch [:submit-handler %])}
   (fn [{:keys [values
                form-id
                handle-change
                handle-blur
                submitting?
		on-submit-server-message
                handle-submit]}]
     [:form
      {:id form-id
       :on-submit handle-submit}
      [:input
       {:name "input"
        :value (values "input")
        :on-change handle-change
        :on-blur handle-blur}]
      [:button
       {:type "submit"
        :disabled submitting?}
       "Submit Form"]
      [:p on-submit-server-message]])])
```

### Cool, but what about validation?

Simply plug in any library of your choice that is side effect free, or build your custom validation.

#### More details

All you have to do is to pass a function that takes `values` as only parameter. The returned data will be accessible via the key `errors`, which can be destructured from the props.

Let's now build some real validation for our *Fork* component using for example the [Vlad](https://github.com/logaan/vlad) library:

```clojure
(def validation
  (vlad/join (vlad/attr ["name"]
                        (vlad/chain
                         (vlad/present)
                         (vlad/length-in 3 15)))
             (vlad/attr ["password"]
                        (vlad/chain
                         (vlad/present)
                         (vlad/length-over 7)))))
```

#### Connect the wires

Let's integrate the validation with our *Fork* component to actually display the errors:

```clojure
[fork/form {:path [:form]
            :form-id "id"
            :validation #(vlad/field-errors validation %)
            :prevent-default? true
            :clean-on-unmount? true
            :on-submit #(rf/dispatch [:submit-handler %])}
 (fn [{:keys [values
              form-id
              errors
              touched
              handle-change
              handle-blur
              submitting?
              handle-submit]}]
   [:form
    {:id form-id
     :on-submit handle-submit}
    [:input
     {:name "name"
      :value (values "name")
      :on-change handle-change
      :on-blur handle-blur}]
    (when (touched "name")
      [:div (first (get errors (list "name")))])
    [:input
     {:name "password"
      :value (values "password")
      :on-change handle-change
      :on-blur handle-blur}]
    (when (touched "password")
      [:div (first (get errors (list "password")))])
    [:button
     {:type "submit"
      :disabled submitting?}
     "Submit Form"]])]
```

Noticed anything new? We are simply passing the vlad validation function along with a `:validation` key and destructuring `:touched`. The latter comes in handy to improve the user experience in that the errors are not shown until the first `:on-blur` event is fired.

When a validation function is provided, the submit button will do nothing until all errors are cleared. The only variable that does change is `:attempted-submissions`, which is incremented every time the submission is attempted. Contrairily, `:successful-submissions` gets updated only when the developer provided on-submit function is invoked.

#### Little Vlad note:

To perform `password` and `confirm-password` validation I recommend using the helper `vlad/equals-value`, as this really simplifies your logic. Briefly, you can define your validation like the following snippet:

```clojure
(def form-validation
  (fn [password]
    (vlad/join
     (vlad/attr ["password"]
                (vlad/chain (vlad/present)
                            (vlad/join (vlad/length-in 6 128))))
     (vlad/attr ["confirm-password"]
                (vlad/chain
                 (vlad/equals-value
                  password
                  {:message "Confirm Password must be same as password"}))))))
```

and pass the password value when giving the function to *Fork* i.e.

```clojure
[fork/form {:validation
            #(vlad/field-errors
              ;; passing password to the function
              (form-validation (get % "password"))
              %)
            ...}
	    ...]
```

### Keywords as input names

By passing the option `{:keywordize-keys true}` to *Fork*, the values and all other state keys will be treated as keywords instead of strings. To make sure the input names are properly registered, use the function `normalize-name` available in the props, when giving the name to the input components i.e. `{:name (normalize-name :bar/foo)}`. Namespaced keys are also supported. If `normalize-name` is used when `keywordize-keys` is not set, the function will simply return the same value.

### Dealing with server requests

Since version `1.1.0`, the handler `send-server-request` provides a way of performing server side validation in callbacks like `on-blur` or `:on-change`, or any other operation that involves your backend code. Here is an example of how it works:

```clojure
(rf/reg-event-fx
 :server-request
 (fn [_ [_ props]]
   ;; faking a server request
   {:dispatch-later [{:ms 2000 :dispatch [:response props]}]}))

(rf/reg-event-fx
 :response
 (fn [{db :db}
      [_ {:keys [state path values dirty errors touched]}]]
   ;; only dispatch http request when client side errors are clear
   (when (empty? (get errors "email"))
     {:db
      ;; assuming bar@foo.com is already taken
      (if (not= "bar@foo.com" (get values "email"))
        (fork/set-waiting db path "email" false)
        (-> db
            (fork/set-error path "email" "Email Already Exists")
            (fork/set-waiting path "email" false)))})))

(defn foo []
  [fork/form {:path :form ;; or [:path :to :form]
              :prevent-default? true
	      :validation #(cond-> {}
                              (empty? (get % "email"))
                              (assoc "email" "Email can't be empty"))
              :on-submit #(js/alert %)}
   (fn [{:keys [form-id
                values
		server-errors
                handle-change
                handle-blur
                handle-submit
                send-server-request]}]
     [:div
      [:form
       {:id form-id
        :on-submit handle-submit}
       [:input
        {:name "email"
         :value (values "email")
         :on-blur handle-blur
         :on-change (fn [evt]
                      (handle-change evt)
                      (send-server-request
                       {:name "email"
			;; this retrieves the most up to date value
                        :value (fork/retrieve-event-value evt)
			;; defaults to true
			:set-waiting? true
			;; to clean up relevant state before each http request
			:clean-on-refetch ["email"]
                        :debounce 500}
                       #(rf/dispatch [:server-request %])))}]
	   [:div (or (get errors "email")
	             (get server-errors "email"))]
       [:button
        {:type "submit"}
        "Submit"]]])])
```

After destructuring `:send-server-request`, this function is invoked within the `:on-change` handler. It takes two parameters being:

- A config map with the following keys

```clojure
{;; REQUIRED - as it's used as a state holder internally
 :name "email"
 ;; OPTIONAL - if not provided the callback will receive the old value
 :value (fork/retrieve-event-value evt)
 ;; OPTIONAL - relevant in the :on-blur case as it sets the `touched` property
 :evt :on-change
 ;; OPTIONAL - to stop the form from submitting while the server request is being processed
 :set-waiting? true
 ;; OPTIONAL - gets rid of any metadata about the provided keys at each server request
 :clean-on-refetch ["email"]
 ;; OPTIONAL & MUTUALLY EXCLUSIVE
 :throttle 500
 :debounce 500}
```

- A function that performs the server request, which will be invoked with the following map keys as args `:state :path :values :touched :errors :dirty` -

To prevent the form submission while waiting for a server response, a `:waiting? true` key value pair is stored in the state and needs to be set to false after the server logic is resolved. You can do this yourself or use `(fork/set-waiting db path "email" false)`, as shown above. Now, the form can be submitted.

### Global accessible helpers

There are several global helpers: `set-waiting`, `set-submitting`, `set-server-message`, and `set-error`.

Note that they do not include side effects at their core. Contrarily, they are meant to simply operate on the old state in order to return the updated one.

When used with re-frame, these handlers can be safely called on the `db` of your events. In a purely reagent context, they can be provided as arguments of the `swap!` function i.e. `(swap! state f/set-submitting path true)`.

The reason behind their side effect free design is to make them composable, thus preventing the triggering of multiple state updates. In practice, this approach favors this:

```clojure
(swap! state #(-> %
                  (f/set-submitting path true)
                  (update :some-key inc)))
```

 Instead of the inefficient version:

```clojure
(swap! state f/set-submitting path true)
(swap! state update :some-key inc)
```

### Field Array

The field array becomes useful when the user might provide zero, one, or many entries for a specific form component. For instance, let's assume we want to collect the user siblings' name. Clearly, there is no way of knowing in advance how many siblings the user has, so we can provide a field array to solve the issue.

```clojure
(defn field-array-fn
  [_props
   {:fieldarray/keys [fields
                      insert
                      remove
                      handle-change
                      handle-blur]}]
  [:<>
   (map-indexed
    (fn [idx field]
      ^{:key idx}
      [:div
       [:div
        [:label "First Name"]
        [:input
         {:name "first-name"
          :value (get field "first-name")
          :on-change #(handle-change % idx)
          :on-blur #(handle-blur % idx)}]]
       [:div
        [:label "Last Name"]
        [:input
         {:name "last-name"
          :value (get field "last-name")
          :on-change #(handle-change % idx)
          :on-blur #(handle-blur % idx)}]]
       [:button
        {:type "button"
         :on-click #(when (> (count fields) 1) (remove idx))}
        [:span "Remove"]]])
    fields)
   [:button
    {:type "button"
     :on-click #(insert {"first-name" "" "last-name" ""})}
    "Add new person"]])

(defn fork-fieldarray []
  [fork/form
   {:on-submit #(js/alert (:values %))
    :initial-values {"siblings" [{"first-name" "" "last-name" ""}]}
    :prevent-default? true}
   (fn [{:keys [handle-submit] :as props}]
     [:div
      [:form
       {:on-submit handle-submit}
       [:h2 "Siblings"]
       [:div
        [fork/field-array {:props props
                           :name "siblings"}
         field-array-fn]
        [:br][:br]
        [:div [:button "Submit"]]]]])])
```

The `field-array-fn` must be a different reagent component to avoid the inputs focus loss. A comprehensive example of the fieldarray functionality can be found [here](https://github.com/luciodale/fork/blob/master/examples/fieldarray.cljs).

What follows is a list of the fieldarray available handlers:

```clojure
:fieldarray/name ;; name chosen for the field array
:fieldarray/options ;; it holds any user custom props they need in the fieldarray component scope
:fieldarray/fields ;; the fieldarray data. It's a vector of maps
:fieldarray/touched ;; (touched idx :my-input)
:fieldarray/insert ;; (insert {:my-field-array-input "hello"})
:fieldarray/remove ;; (remove idx)
:fieldarray/set-handle-change ;; same as the one in main props (see below)
:fieldarray/set-handle-blur ;; same as the one in main props (see below)
:fieldarray/handle-change ;; (handle-change evt idx)
:fieldarray/handle-blur ;; (handle-blur evt idx)}
:fieldarray/current-target-idx ;; returns idx of droppable item location (current-target-idx :field-array-key)
:fieldarray/current-dragged-idx ;; returns idx of dragged item (current-dragged-idx :field-array-key)
:fieldarray/next-droppable-target? ;; true if hovered item is > than dragged (next-droppable-target? :field-array-key idx)
:fieldarray/prev-droppable-target? ;; true if hovered item is < than dragged (prev-droppable-target? :field-array-key idx)
:fieldarray/drag-and-drop-handlers ;; all drag and drop handlers
;; i.e. [:div (merge {:class ...} (drag-and-drop-handlers :field-array-key idx))]
```

### Does Fork do anything else for me?

You bet it does. The keys you can currently access from your form function are:

```clojure
[{:keys
  [db ;; <- only re-frame
   props
   state
   reset
   values
   dirty
   form-id
   errors
   server-errors
   touched
   set-touched
   set-untouched
   submitting?
   normalize-name
   attempted-submissions
   successful-submissions
   set-values
   disable
   enable
   disabled?
   set-handle-change
   set-handle-blur
   handle-change
   handle-blur
   handle-submit
   on-submit-server-message
   send-server-request]}]
```
#### Quick overview

Here is a demonstration on how to use the above handlers that have not been mentioned so far:

```clojure
;; db is simply the dereferenced re-frame state that fork uses for global logic

;; state is the local ratom used for the full form core logic
(swap! state assoc :something :new)

;; to remove full state
(reset)

;; to reset state to given map
(reset {:values {"name" "John"}
        :touched #{"name"}})

(set-touched "input" "another-input")

(set-untouched "input" "another-input")

(set-values {"input" "new-value"})

(set-values {"input" "new-value"
             "another-input" "new-value-too"})

(disable "input")

(disable "input" "another-input")

(enable "input")

(enable "input" "another-input")

;; input component
[:input
{...
 :name (normalize-name :foo/bar)
 :disabled (disabled? "input")
 ...}]

(set-handle-change
 {:value "Joe" ;; or (fn [previous-val] "Joe")
  :path ["name"]})

(set-handle-blur
 {:value true ;; or false
  :path ["name"]})
```

For what concerns the `:props` key, you can use it as a way of passing arguments to the form component. Here is a quick example:

```clojure
(defn my-form
  [{:keys [props ...]}]
  ;; props accessible in here!
  ...
  )

(defn foo []
  [fork/form {:props {:arg1 "foo" :arg2 "bar"}
              ...}
   my-form])
```

## Do I really need to build all components from the ground up?

Certainly not, *Fork* gives you some pre-built inputs, yet you are condemning yourself to the Bulma CSS framework if you ever choose to go down that route. Creating your own wrappers would work much better, allowing you to retain full control on the style. Having said that, these are the few components that ship with *Fork*.

### Bulma CDN

The quickest way to get Bulma is to require the CSS in the header of your index.html file:

```
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/bulma/0.7.5/css/bulma.min.css"/>

<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.9.0/css/all.min.css"/>
```

### Input

```clojure
(ns your.namespace
  (:require
   [fork.bulma :as bulma]))
```

```clojure
[bulma/input props
 {:name "input"
  :label "First Name"
  :placeholder "Joe"
  :type "text"
  :class "your-css-class"}]
```

To get all the props from your form function in one shot, you can add :as props just like this: `{:keys [values ..] :as props}`

### Text Area

```clojure
[bulma/textarea props
 {:name "area"
  :label "Summary"
  :placeholder "Max 400 words"
  :type "text"
  :class "your-css-class"}]
```

### Checkbox

```clojure
[bulma/checkbox props
 {:name "agree"
  :text "Plain text or component as well"}]
```

If you pass a component to `:text` such as `[:div "Some text"]`, add the `display: inline;` style to the element.

### Dropdowns

```clojure
[bulma/pretty-dropdown props
 {:label "Optional Label"
  :name "pretty-dropdown"
  :options [{"key-1" 1}
            {"key-2" 2}
            {"key-3" 3}]
  :class "Optional Css Class"}]
```

```clojure
[bulma/dropdown props
 {:label "Optional Label"
  :name "pretty-dropdown"
  :options [{"key-1" 1}
            {"key-2" 2}
            {"key-3" 3}]
  :class "Optional Css Class"}]
```

## Can I go make my forms now?

Yes you can. This documentation should get you started with *Fork* the right way so that you can be productive with it. Ideas, comments (good or bad), and suggestions are always welcome!
