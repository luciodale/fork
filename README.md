<p align="center">
  <img src="https://imgur.com/baEth6W.jpg" alt="drawing" width="150"/>
</p>

Yet another Form Library to add to the list. Inspired by [Formik](https://github.com/jaredpalmer/formik).

*For Reagent & Re-frame*

[![Clojars Project](https://img.shields.io/clojars/v/fork.svg)](https://clojars.org/fork)

## Why Fork?
If there is anything certain about us developers, it is that sooner or later we will have to deal with forms. No way around it.

One thing is when your project merely requires login, registration, and account page, but it's a whole different story when you need forms ALL OVER THE PLACE.

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
fork {:mvn/version "0.1.0"}
```

or

```clojure
fork {:git/url "https://github.com/luciodale/fork.git"
      :sha "0c7dc0beed9f7e0a9112515f6eb058ae4f45c71f"}
 ```

#### In Namespace

```clojure
(ns your.namespace
  (:require
   [fork.fork :as fork]))
```

### The Bare Minimum

```clojure
(defn foo []
  [fork/fork {:initial-values
              {"input" ""}}
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

Moving back to `:initial-values`, this key is always required to make *Fork* aware of all your form elements. If you don't need any initial values for your fields, you can safely use empty strings, just like in the snippet. Make sure to match the `:name` of your inputs with what you define in the `:initial-values` map to successfully link up the handlers. Do not use keywords for input names, as html casts them to strings anyways giving you `":input"`.

### How do I submit a form?

```clojure
(ns your.namespace
  (:require
   [fork.fork :as fork]
   [re-frame.core :as rf))

(rf/reg-event-fx
 :submit-handler
 [(fork/on-submit :form)]
 (fn [{db :db} [_ {:keys [values]}]]
   (js/alert values)
   {:db (fork/set-submitting db :form false)}))

(defn foo []
  [fork/fork {:initial-values
              {"input" ""}
              :path :form
              :prevent-default? true
              :clean-on-unmount? true
              :on-submit #(rf/dispatch [:submit-handler %])}
   (fn [{:keys [values
                handle-change
                handle-blur
                submitting?
                handle-submit]}]
     [:form
      {:on-submit handle-submit}
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

`:path` lets you choose where to store your form global state in Re-frame.

`:prevent-default?` does not automatically submit your form to the server.

`:clean-on-unmount?` resets the global state when your component is unmounted.

`:on-submit` lets you write your own submit logic in a Re-frame event.

#### The Flow

After clicking the submit button, the interceptor `(fork/on-submit :form)` sets `submitting?` to true, increases the `:submit-count`, and removes any `:external-errors` coming for example from a previously failed http request. Remember to pass `:form` to the interceptor function, and make sure that it matches the `:path` value you have given to *Fork*. At this stage, your event is executed and the only detail to remember is to set `:submitting?` to false when the form life cycle is completed. You can choose to handle the global state with your own functions or rely on some helpers like `fork/set-submitting`. It's really up to you.

You probably want to know more than the same old *Hello World* demonstration. Hence, I have prepared a REAL example that includes a server request and shows better what *Fork* can do for you.

```clojure
(ns your.namespace
  (:require
   [ajax.core :as ajax]
   [day8.re-frame.http-fx]
   [fork.fork :as fork]
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
  [fork/fork {:initial-values
              {"input" ""}
              :path :form
              :prevent-default? true
              :clean-on-unmount? true
              :on-submit #(rf/dispatch [:submit-handler %])}
   (fn [{:keys [values
                errors
                handle-change
                handle-blur
                submitting?
                handle-submit]}]
     [:form
      {:on-submit handle-submit}
      [:input
       {:name "input"
        :value (values "input")
        :on-change handle-change
        :on-blur handle-blur}]
      [:button
       {:type "submit"
        :disabled submitting?}
       "Submit Form"]
      (when-let [msg (:error-500 errors)]
        [:p msg])])])
```

A few things to keep in mind:

* Always return the db in you `:submit-handler` to not lose the interceptor updates i.e. `{:db db ...}`.
* You might choose to use the `fork/clean` interceptor to clean the whole state or parts of it i.e. `(fork/clean :form :submitting?)`.
* You don't really need to clean the state if your component is unmounted, as the `:clean-on-unmount?` option will take care of it.

### Cool, but what about validation?

Simply plug it in!

#### Define your validation

Create a function that takes `values` as parameter and returns a map with the following structure:

```
{:client
 {:on-change
  {"input" [[(your logic) :error-key-1 "first message"]
            [(your logic) :error-key-2 "second message"]]
   "another-input" [...]
   :input-independent [[(i.e. sum of all inputs must be 100)
                        :error-key "message"]
                       [...]]}
  :on-blur {...}
  :on-submit {...}}}
```

The required keys are `:client` and at least one among `:on-change`, `:on-blur`, and `:on-submit`, which all describe when the validation should take place. Use your existing input names such as `"input"` to link any eventual error to the respective fields in your form. Also, note that each input can have an indefined number of nested vectors to allow multiple validations with their own error messages.

You might end up in a situation where your validation does not need to be bound to any particular input. In that case, you can use a keyword such as `:input-independent` to organize your "general" logic, which might involve for example the sum of multiple inputs being equal to a specific value.

Lastly, if you add multiple validations per input, be sure to name the error keywords differently to avoid overwriting the previous error with the latest one evaluated. For example, if `:error-key-1` and `:error-key-2` were named the same, only the last one would have ended up in the errors map.

If you are questioning the purpose of the keywords, they are used to add the messages in the errors map to not resort on a string match approach. For example, when the first validation function for the `"input"` field returns false i.e. `(your logic)`, the errors map updates to:

```clojure
{:errors {"input" {:error-key-1 "first message"}}
```

Let's now build some real validation for our *Fork* component:

```clojure
(defn validation
  [values]
  {:client
   {:on-change
    {"input" [[(seq (values "input")) :error-1 "Can't be empty"]
              [(> (count (values "input")) 3) :error-2 "Must be > 3"]]}}})
```

As you might have guessed, this check is simply making sure that the input is not empty and that there are at least 4 chars.

#### Connect the wires

Let's integrate the validation with our *Fork* component to actually display the errors:

```clojure
(defn foo []
  [fork/fork {:initial-values
              {"input" ""}
              :path :form
              :prevent-default? true
              :clear-on-unmount? true
              :on-submit #(rf/dispatch [:submit-handler %])
              :validation validation}
   (fn [{:keys [values
                errors
                touched
                state
                handle-change
                handle-blur
                submitting?
                handle-submit]}]
     [:form
      {:on-submit handle-submit}
      [:input
       {:name "input"
        :value (values "input")
        :on-change handle-change
        :on-blur handle-blur}]
      (when (get touched "input")
          (for [[k msg] (get errors "input")]
            ^{:key k}
            [:p msg]))
      [:button
       {:type "submit"
        :disabled submitting?}
       "Submit Form"]
      (when-let [msg (:error-500 errors)]
        [:p msg])])])
```

Noticed anything new? We are simply passing the validation function along with a `:validation` key and destructuring `touched`. The latter comes in handy to improve the user experience in that the errors are not shown until the first `:on-blur` event is fired.

With the current logic in place, the form is always submitted regardless of the errors. To fix this, you might add a condition to disable the submit button, but I would advice to include some logic in the submit event:

```clojure
(rf/reg-event-fx
 :submit-handler
 [(fork/on-submit :form)]
 (fn [{db :db} [_ {:keys [values errors]}]]
   (if errors
     {:db (fork/set-submitting db :form false)}
     {:db db
      :http-xhrio
      {:method :post
       :uri "/submit-form"
       :params values
       :timeout 2000
       :format (ajax/transit-request-format)
       :response-format (ajax/transit-response-format)
       :on-success [:success]
       :on-failure [:failure]}})))
```

### Does Fork do anything else for me?

You bet it does. The keys you can currently access from your anonymous function are:

```clojure
(fn [{:keys [state
             values
             errors
             touched
             submitting?
             submit-count
             set-values
             disable
             enable
             disabled?
             handle-change
             handle-blur
             handle-submit]}]

  ...)
```

If you need more, don't hesitate to open a PR. Contributions and improvements are more than welcome!

#### Quick overview

Here is a demonstration on how to use the above handlers that have not been mentioned so far:

```clojure
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
 {:name "input"
  :value (values "input")
  :on-change handle-change
  :on-blur handle-blur
  :disabled (disabled? "input")}]
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

## More funky stuff

*Fork* has an API to ease the development of input arrays, which are those inputs that can be dynamically generated or deleted via clicking on buttons available in the UI. Before anything else, initiate an input array in your `:initial-values` with a key value pair like the following:

```clojure
:initial-values {"input-array" {0 {"foo" ""
                                   "bar" ""}}}
```

### Input Array

```clojure
[fork/input-array props
 {:name "input-array"
  :component your-component
  :args "Passed as second argument in your-component function"]

```

Let's see what `your-component` function could look like:

```clojure
(defn your-component
  [{:keys [values
           handle-change
           handle-blur
           array-key
           add
           delete]} _]
  [:div
   (for [[idx value] (get values array-key)]
     ^{:key idx}
     [:div
      [:div
       [:label "Foo"]
       [:input
        {:name "foo"
         :value (value "foo")
         :on-change #(handle-change % idx)
         :on-blur #(handle-blur % idx)}]]
      [:div
       [:label "Bar"]
       [:input
        {:name "bar"
         :value (value "bar")
         :on-change #(handle-change % idx)
         :on-blur #(handle-blur % idx)}]]
      [:button
       {:on-click #(delete % idx)}
       "Remove"]])
   [:button
    {:on-click add}
    "Add"]])
```

### How do I validate an input array?

Great question! With the help of some extra functions, you can keep your code tidy and clean.

Do you remember the validation function? Let's get back to it:

```clojure
(defn validation [values]
  {:client
   {:on-change
    {"input-array"
     (apply concat
            (map
             (fn [[idx {:strs [foo bar]}]]
               [[(not (empty? foo)) (str "foo" idx) "Foo can't be empty"]
                [(= "validate-more" foo) (str "foo-1" idx) "More on Foo"]
                [(not (empty? bar)) (str "bar" idx) "Bar can't be empty"]])
             (values "input-array")))}}})
```

We have added a function that generates our vector of vectors when evaluated. Note that you can have many checks also in this instance as long as you remember to pass the error keys in the component. Let's do it right now!

```clojure
(defn your-component
  [{:keys [values
           handle-change
           handle-blur
           array-key
           add
           delete
           input-array-errors]}]
  [:div
   (for [[idx value] (get values array-key)]
     ^{:key idx}
     [:div
      [:div
       [:label "Foo"]
       [:input
        {:name "foo"
         :value (value "foo")
         :on-change #(handle-change % idx)
         :on-blur #(handle-blur % idx)}]
       (for [[k error]
             (input-array-errors
              idx "foo" [(str "foo" idx)
                         (str "foo-1" idx)])]
         ^{:key k}
         [:div
          [:p.help error]])]
      [:div
       [:label "Bar"]
       [:input
        {:name "bar"
         :value (value "bar")
         :on-change #(handle-change % idx)
         :on-blur #(handle-blur % idx)}]
       (for [[k error]
             (input-array-errors
              idx "bar" [(str "bar" idx)])]
         ^{:key k}
         [:div
          [:p.help error]])]
      [:button
       {:on-click #(delete % idx [(str "foo" idx)
                                  (str "foo-1" idx)
                                  (str "bar" idx)])}
       "Remove"]])
   [:button
    {:on-click add}
    "Add"]])
```

As you can notice, you have to pass the error keys you used in your validation to both the `input-array-errors` and `delete` functions. You have to do this manual work because *Fork* leaves you with the choice to pick your own keys, thus knowing nothing about them.

## Can I go make my forms now?

Yes you can. This documentation should get you started with *Fork* the right way so that you can be productive with it. Ideas, comments (good or bad), and suggestions are always welcome!
