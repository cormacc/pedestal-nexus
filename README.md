# cormacc/pedestal-nexus

A simple library to support data-driven handler implementation in [Pedestal](https://pedestal.io) using the [Nexus](https://github.com/cjohansen/nexus) action dispatch library.

This repo is very much WIP - untested and poorly documented.

## Why
In brief, this library allows the FCIS (Functional Core, Imperative Shell) pattern to be used in backend API development using pedestal. It does so using Christian Johansen's action dispatch library, Nexus, allowing you to write pure functional handlers that receive the Pedestal context as input (like a typical Pedestal interceptor), and return a vector of actions and effects for subsequent execution using nexus. See example below:

``` clojure
(defn todo-show-list-item [{:keys [request state] :as context}]
  (let [{:keys [list-id item-id]} (get request :path-params)
        item (get-list-item-by-id state list-id item-id)]
    (if [item]
      [[:http/response :ok item]]
      [[:http/response :not-found (str "List not found: " list-id " / " item-id)]])))

(defn todo-create-list-item [{:keys [state request]}]
  (if-let [list-id (get-in request [:path-params :list-id])]
    (let [item-name  (get-in request [:query-params :name] "Unnamed Item")
          {:keys [id] :as new-item} (new-list-item item-name)]
      (if (contains? state list-id)
        [[:state/assoc-in [list-id :items  id] new-item]
         [:http/response :created new-item]]
        [[:http/response :not-found (str "List not found: " list-id)]]))))
```



## Usage
First, define your nexus map (which contains all the action and effect implementations), and the nexus system and system deref function. These are described comprehensively in the nexus documentation. The example below includes a single effect implementation -- `:state/assoc-in`, which updates data in the application state store atom included in the nexus system.

``` clojure
(def nexus-system {:!store (atom {})})

;; There's a built-in :http/response effect -- add whatever else
;; you need for your app here
(def nexus
  {:nexus/system->state (fn [{:keys [!store] :as _sys}] @!store)

   :nexus/effects
   {:state/assoc-in
    (fn [_ctx {:keys [!store] :as _sys} path v]
      (swap! !store assoc-in path v))}})
```

The `create-nexus-wrapper` function closes around the nexus map and system and returns a function you can use to wrap data-driven handler entries in your route table as illustrated below:

``` clojure
;; This generates a wrapper function used to wrap the data-driven handlers in a
;; pedestal interceptor chain
(def with-nexus (pedestal-nexus/create-nexus-wrapper nexus nexus-system))

;; Each data-driven handler is a function that receives the pedestal context
;; and returns a vector of nexus actions and/or effects
;; The context is augmented with a :state key whose value is the current output
;; of the system deref function defined under :nexus/system->state
(defn echo [{:keys [request]}]
  ;; The :http/response effect has the form [:http/response <response-code> <body> & <headers>]
  ;; The response code can be a number, or one of the keys defined in a lookup table
  [[:http/response :ok request]])

(def routes
  ;;`(with-nexus ...)` requires a var argument prefix, to allow automatic generation of
  ;;interceptor name from the handler fn name
  ;;It returns a seq of two interceptors:
  ;;1. A generic nexus interceptor that extracts and executes the actions/effects
  ;;2. The wrapped handler interceptor
  #{["/todo" :get (with-nexus #'echo)]})

```

N.B. the generated wrapper augments your nexus map with a single built-in effect, `:http/response`.
The effect has the form `[:http/response <code> <body> {:headers {<headers>}}]`.
- <code> :: This may be a number, or a corresponding keyword -- see [the implementation](./src/cormacc/pedestal_nexus.clj) for the full list.
- <body> :: The response body.
- <headers> :: An optional map, defining the response headers. 

Use of this library doesn't interfere with the operation of standard interceptors -- you can mix and match traditional Pedestal handlers and interceptors with data-driven handlers as illustrated below:

``` clojure

(def routes
  ;;If you want to chain additional interceptors, you can use something like the following...
  ;;`(into [an-interceptor another-interceptor] (with-nexus #'wrapped-handler))`
  ;;You may not need to though -- the effect handlers on the nexus map should allow
  ;;many (?most?) use cases to be covered by composing a sequence of actions/effects in a single
  ;;handler rather than chaining additional interceptors.
  #{;; Route using standard interceptors
    ["/todo" :post [db-interceptor list-create]]
    ;;Route using data-driven handler
    ["/todo" :get (with-nexus #'echo)]
    ;;Data-driven handler preceeded by a standard interceptor
    ["/todo/:list-id" :get  (into [db-interceptor] (with-nexus #'todo-show-list))]
    ["/todo/:list-id" :post  (into (with-nexus #'todo-create-list-item) [some-other-interceptor])]})
```


A [complete usage example](./examples/src/examples/todos.clj) has been provided in the [./examples](./examples)
subdirectory of this repository. It's the todo example from the pedestal tutorials re-written using pure data handlers.

## Run the test suite
Run the project's tests

    $ bb test     # run Clojure tests
    $ bb test:bb  # run Babashka tests
    $ bb test:all # run both Clojure and Babashka tests


## Build and deploy
Run the project's CI pipeline and build a JAR (this will fail until you edit the tests to pass):

    $ bb ci

This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF`
directory inside `target/classes` and the JAR in `target`. You can update the version (and SCM tag)
information in generated `pom.xml` by updating `build.clj`.

Install it locally (requires the `ci` task be run first):

    $ bb install

Run the projects's CI pipeline and deploy it to Clojars --
needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment
variables:

    $ bb ci:deploy

Your library will be deployed to net.clojars.cormacc/pedestal-nexus on clojars.org by default.

## Acknowledgments

This library was inspired by (and partly adapted from) a similar effort by Ovi Stoica ([@ovistoica](https://github.com/ovistoica)) for Ring, and the nexus library and learning materials provided by Christian Johansen ([@cjohansen](https://github.com/cjohansen)) and Magnar Sveen ([@magnars](https://github.com/magnars)) 

## License
Copyright © 2025 Cormac Cannon
Distributed under the [MIT License](https://opensource.org/license/mit).
