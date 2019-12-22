<p align="center">
  <img src="https://imgur.com/baEth6W.jpg" alt="drawing" width="150"/>
</p>

Yet another Form Library to add to the list. Inspired by [Formik](https://github.com/jaredpalmer/formik).

*For Reagent & Re-frame*

[![Clojars Project](https://img.shields.io/clojars/v/fork.svg)](https://clojars.org/fork)

<img src="https://s5.gifyu.com/images/a-2019-12-19_153050.gif" width="80%" />

## Why Fork?
If there is anything certain about us developers, it is that sooner or later we will have to deal with forms. No way around it.

**Fork** tries to establish a pattern by abstracting away the bits you have written over and over to shift your focus on the features implementation. The following points represent the pillars *Fork* was built upon:

* Control - *You code your form components*
* Adaptable - *Plug it in and take only what you need*
* Separation of concerns - *The state is kept local with a Ratom, while Re-frame handles global events*
* CSS Free - *You manage the style*

As at this state you must be dying of curiosity, I will dive right into the code implementation hoping that this will help you save the day... and some nerves.

## API

### Require Fork

#### In Deps

```clojure
fork {:mvn/version "1.0.1"}
```

or

```clojure
fork {:git/url "https://github.com/luciodale/fork.git"
:sha "last sha commit here"}
 ```

#### In Namespace

```clojure
(ns your.namespace
  (:require
  [fork.core :as fork]))
```

### The Bare Minimum

```clojure
(defn foo []
  [fork/form {:initial-values
              {"input" "hello"}}
   (fn [{:keys [values
                handle-change
                handle-blur]}]
     [:div
      [:p "Read back: " (values "input")]
      [:input
       {:name "input"
        :value (values "input")
        :on-change handle-change
        :on-blur handle-blur}]])])
```

Notice that *Fork* takes only two parameters. The first one is a map of utilities you provide, and the second one is an anonymous function that returns your component. It is by destructuring the first and only param of the anonymous function that you get all the goodies straight from the API.

Starting from `:initial-values`, this key might be provided to make *Fork* aware of any of your prefilled form values. Make sure to match the `:name` of your inputs with what you define in the `:initial-values` map to successfully link up the handlers. Do not use keywords for input names, as html casts them to strings anyways giving you `":input"`. If you don't need to set default values for your fields, you can discard this key.

### How do I submit a form?

```clojure
(ns your.namespace
  (:require
  [fork.core :as fork]
   [re-frame.core :as rf))

(rf/reg-event-fx
 :submit-handler
 [(fork/on-submit :form)]
 (fn [{db :db} [_ {:keys [values]}]]
   (js/alert values)
   {:db (fork/set-submitting db :form false)}))

(defn foo []
  [fork/form {:path :form
              :form-id "id"
              :prevent-default?
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

If some parts look a bit obscure, the following detailed explanation will get rid of all your doubts.

#### Params

`:form-id` makes fork aware of your form elements. If it is not specified, a random id will be generated and will be provided through the same `:form-id` key. It is mandatory to use it.

`:path` lets you choose where to store your form global state in Re-frame.

`:prevent-default?` does not automatically submit your form to the server.

`:clean-on-unmount?` resets the global state when your component is unmounted.

`:on-submit` lets you write your own submit logic in a Re-frame event.

#### The Flow

After clicking the submit button, the interceptor `(fork/on-submit :form)` sets `submitting?` to true and removes any `:external-errors` coming for example from a previously failed http request. Remember to pass `:form` to the interceptor function, and make sure that it matches the `:path` value you have given to *Fork*. At this stage, your event is executed and the only detail to remember is to set `:submitting?` to false when the form life cycle is completed. You can choose to handle the global state with your own functions or rely on some helpers like `fork/set-submitting`. It's really up to you.

You probably want to know more than the same old *Hello World* demonstration. Hence, I have prepared a REAL example that includes a server request and shows better what *Fork* can do for you.

```clojure
(ns your.namespace
  (:require
   [ajax.core :as ajax]
   [day8.re-frame.http-fx]
   [fork.core :as fork]
   [re-frame.core :as rf))

(rf/reg-event-fx
 :success
 [(fork/clean :form)]
 (fn [{db :db} [_ result]]
   {:db (assoc db :result result)}))

(rf/reg-event-fx
 :failure
 (fn [{db :db} [_ result]]
   {:db (-> db
            (fork/set-submitting :form false)
            (fork/set-external-errors :form {:error-500 "You got a 500!"}))}))

(rf/reg-event-fx
 :submit-handler
 [(fork/on-submit :form)]
 (fn [{db :db} [_ {:keys [values]}]]
   {:db db
    :http-xhrio
    {:method :post
     :uri "/submit-form"
     :params values
     :timeout 2000
     :format (ajax/transit-request-format)
     :response-format (ajax/transit-response-format)
     :on-success [:success]
     :on-failure [:failure]}}))

(defn foo []
  [fork/form {:form-id "id"
              :path :form
              :prevent-default? true
              :clean-on-unmount? true
              :on-submit #(rf/dispatch [:submit-handler %])}
   (fn [{:keys [values
                form-id
                external-errors
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
       "Submit Form"]
      (when-let [msg (:error-500 external-errors)]
        [:p msg])])])
```

A few things to keep in mind:

* Always return the db in you `:submit-handler` to not lose the interceptor updates i.e. `{:db db ...}`.
* You might choose to use the `fork/clean` interceptor to clean the whole state or parts of it i.e. `(fork/clean :form :submitting?)`.
* You don't really need to clean the state if your component is unmounted, as the `:clean-on-unmount?` option will take care of it.

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
[fork/form {:path :form
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

Noticed anything new? We are simply passing the vlad validation function along with a `:validation` key and destructuring `touched`. The latter comes in handy to improve the user experience in that the errors are not shown until the first `:on-blur` event is fired.

When a validation function is provided, the submit button will do nothing until all errors are cleared. The only variable that does change is `submit-count`, which is incremented every time the `on-click` event is fired.

### Does Fork do anything else for me?

You bet it does. The keys you can currently access from your anonymous function are:

```clojure
(fn [{:keys
      [db
       state
       values
       form-id
       errors
       external-errors
       touched
       submitting?
       submit-count
       set-values
       disable
       enable
       disabled?
       handle-change
       handle-blur
       handle-submit]}])
```
#### Quick overview

Here is a demonstration on how to use the above handlers that have not been mentioned so far:

```clojure
;; db is simply the dereferenced re-frame state that fork uses for external matters

(swap! state assoc :something :new)

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
 :disabled (disabled? "input")
 ...}]
```

#### State Warning

Use the state directly only if you really know what you are doing, as it is the ratom that manages the whole form. You might find it useful to deref and print the ratom in your console for debugging reasons.

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
[fork/input props
 {:name "input"
  :label "First Name"
  :placeholder "Joe"
  :type "text"
  :class "your-css-class"}]
```

To get all the props from your anonymous function in one shot, you can add :as props just like this: `{:keys [values ..] :as props}`

### Text Area

```clojure
[fork/textarea props
 {:name "area"
  :label "Summary"
  :placeholder "Max 400 words"
  :type "text"
  :class "your-css-class"}]
```

### Checkbox

```clojure
[fork/checkbox props
 {:name "agree"
  :text "Plain text or component as well"}]
```

If you pass a component to `:text` such as `[:div "Some text"]`, add the `display: inline;` style to the element.

### Dropdowns

```clojure
[fork/pretty-dropdown props
 {:label "Optional Label"
  :name "pretty-dropdown"
  :options {"key-1" 1
            "key-2" 2
            "key-3" 3}
  :class "Optional Css Class"}]
```

```clojure
[fork/dropdown props
 {:label "Optional Label"
  :name "pretty-dropdown"
  :options {"key-1" 1
            "key-2" 2
            "key-3" 3}
  :class "Optional Css Class"}]
```

## Can I go make my forms now?

Yes you can. This documentation should get you started with *Fork* the right way so that you can be productive with it. Ideas, comments (good or bad), and suggestions are always welcome!
