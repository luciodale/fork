---
sidebar_position: 1
---

# Intro

## Why Fork?

Forms are hard. Orchestrating their ever changing state, complex logic, and rich UI elements is quite a challenging task.

Fork tries to establish a pattern by abstracting away the bits of code that can be generalized to shift the developer's focus on the business logic. The following points represent the pillars Fork was built upon.

### Control

Fork is headless. It only provides the functionality, so the user builds the form components.

### Adaptable

Fork is pluggable. The user can leverage only what they need from the API.

### Simple

A thin layer of abstraction makes sure the library is intuitive and easy to use.

## Getting Started

Fork can be used with both **[reagent](https://github.com/reagent-project/reagent)** and **[re-frame](https://github.com/day8/re-frame)**. 
To add the dependency to your project include in your `deps.edn`:

 ```clojure
 fork {:mvn/version "three.number.version"}
 ``` 
 
:warning: Note that at this stage your project may require a restart for the library to be downloaded. 

Once the library is available, you can require it in any of your CLJS namespaces like this:

```clojure {4,6}
(ns your.namespace
  (:require
    ;; depending on whether you use reagent with/without re-frame
	[fork.re-frame :as fork]
    ;; or
	[fork.reagent :as fork-reagent])) 
```

Keep in mind that both `fork.re-frame` and `fork.reagent` APIs are identical, and you can choose what to require based on your specific use case.