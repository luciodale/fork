<div align="center">
  <img src="https://raw.githubusercontent.com/luciodale/fork/master/packages/docs/public/logo-accent.svg" alt="fork logo" width="120" height="120" />
  <h1>fork</h1>
  <p>A form library for Reagent & Re-frame that handles state orchestration so you can focus on your UI.</p>

  [Documentation](https://koolcodez.com/projects/fork) &nbsp;&middot;&nbsp; [Clojars](https://clojars.org/fork) &nbsp;&middot;&nbsp; [GitHub](https://github.com/luciodale/fork)

</div>

## Install

```clojure
;; deps.edn
fork {:mvn/version "2.4.3"}

;; project.clj
[fork "2.4.3"]
```

## Quick Start

```clojure
(ns app.core
  (:require [fork.reagent :as fork]))

(defn my-form
  [{:keys [values handle-change handle-blur]}]
  [:div
   [:p "Read back: " (values "input")]
   [:input
    {:name "input"
     :value (values "input")
     :on-change handle-change
     :on-blur handle-blur}]])

(defn app []
  [fork/form {:initial-values {"input" "hello"}}
   my-form])
```

Fork takes two arguments: a config map and a render function. The render function receives a map of handlers and state accessors. Wire them to your inputs and Fork manages everything else.

## Features

- **State orchestration** &mdash; values, touched fields, dirty tracking, disabled state, and submission counters managed in a single ratom with composable, side-effect-free helpers
- **Pluggable validation** &mdash; bring any validation library (Vlad, Malli, custom functions). Fork blocks submission until errors clear and only shows errors after the field is touched
- **Field arrays** &mdash; dynamic field groups with insert, remove, and drag-and-drop reordering. Touched state tracks correctly across additions and deletions
- **Server request handling** &mdash; built-in debounce/throttle for server-side validation, waiting state tracking, and error injection. The form won't submit while a server request is pending
- **Reagent & Re-frame** &mdash; identical API for both. Switch between local state and global state by changing one require

## Validation

Any side-effect-free function works. Pass it via `:validation` and destructure `:errors` and `:touched`:

```clojure
[fork/form {:validation
            #(cond-> {}
               (empty? (get % "email"))
               (assoc "email" "Email is required"))
            :initial-values {"email" ""}
            :prevent-default? true
            :on-submit #(js/alert (:values %))}
 (fn [{:keys [values errors touched
              handle-change handle-blur handle-submit]}]
   [:form {:on-submit handle-submit}
    [:input
     {:name "email"
      :value (values "email")
      :on-change handle-change
      :on-blur handle-blur}]
    (when (touched "email")
      [:div.error (get errors "email")])
    [:button {:type "submit"} "Submit"]])]
```

## Field Arrays

Dynamic groups with insert and remove. The field array component must be a named function to avoid input focus loss:

```clojure
[fork/form
 {:initial-values {"people" [{"name" ""}]}
  :on-submit #(js/alert (:values %))}
 (fn [{:keys [handle-submit] :as props}]
   [:form {:on-submit handle-submit}
    [fork/field-array {:props props :name "people"}
     my-field-array-component]
    [:button {:type "submit"} "Submit"]])]
```

## Docs

Full documentation, configuration reference, and code examples at [koolcodez.com/projects/fork](https://koolcodez.com/projects/fork).

## License

MIT
