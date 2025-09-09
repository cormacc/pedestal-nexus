(ns examples.todos
  (:require [io.pedestal.connector :as conn]
            [io.pedestal.http.jetty :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.connector.test :as test]
            [cormacc.pedestal-nexus :as pedestal-nexus]))


;; Nexus / effect handling

(def nexus-system {:!store (atom {})})

;; There's a built-in :http/response effect -- add whatever else
;; you need for your app here
(def nexus
  {:nexus/system->state (fn [{:keys [!store] :as _sys}] @!store)

   :nexus/effects
   {:state/assoc-in
    (fn [_ctx {:keys [!store] :as _sys} path v]
      (swap! !store assoc-in path v))}})

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

(defn new-list [list-name]
  {:id (str (gensym "l"))
   :name  list-name
   :items {}})

(defn new-list-item [item-name]
  {:id (str (gensym "i"))
   :name  item-name
   :done? false})

(defn get-list-by-id [db list-id]
  (get db list-id))

(defn get-list-item-by-id [db list-id item-id]
  (get-in db [list-id :items item-id] nil))

(defn todo-create-list [context]
  (let [list-name (get-in context [:request :query-params :name] "Unnamed List")
        ;;TODO: This id stuff is nasty / impure
        {:keys [id] :as new-list}  (new-list list-name)
        url       (route/url-for :todo-show-list :params {:list-id id})]
    [[:state/assoc-in [id] new-list]
     [:http/response :created new-list "URL:" url]]))

(defn todo-show-list [{:keys [request state] :as context}]
  (let [list-id    (get-in request [:path-params :list-id])
        the-list (when list-id (get-list-by-id state list-id))]
    (if [the-list]
      [[:http/response :ok the-list]]
      [[:http/response :not-found (str "List not found: " list-id)]])))

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

(def routes
  ;;`(with-nexus ...)` requires a var argument prefix, to allow automatic generation of
  ;;interceptor name from the handler fn name
  ;;It returns a seq of two interceptors:
  ;;1. A generic nexus interceptor that extracts and executes the actions/effects
  ;;2. The wrapped handler interceptor
  ;;If you want to chain additional interceptors, you can use something like the following...
  ;;`(into [an-interceptor another-interceptor] (with-nexus #'wrapped-handler))`
  ;;You may not need to though -- the effect handlers on the nexus map should allow
  ;;many (?most?) use cases to be covered by composing a sequence of actions/effects in a single
  ;;handler rather than chaining additional interceptors.
  #{["/todo" :post (with-nexus #'todo-create-list)]
    ["/todo" :get (with-nexus #'echo)]
    ["/todo/:list-id" :get  (with-nexus #'todo-show-list)]
    ["/todo/:list-id" :post  (with-nexus #'todo-create-list-item)]
    ["/todo/:list-id/:item-id" :get  (with-nexus #'todo-show-list-item)]
    ["/todo/:list-id/:item-id" :put  (with-nexus #'echo) :route-name :list-item-update]
    ["/todo/:list-id/:item-id" :delete  (with-nexus #'echo) :route-name :list-item-delete]})


;; Start and stop the server...
(defn- create-connector [port routes]
  (-> (conn/default-connector-map port)
      (conn/with-default-interceptors)
      (conn/with-routes routes)
      (http/create-connector nil)))

(defn create! [port routes]
  (conn/start! (create-connector port routes)))

(defn destroy! [connector]
  (conn/stop! connector))

(defn test-request-with [connector verb url]
  (test/response-for connector verb url))

(defonce !connector (atom nil))

(defn start! []
  (reset! !connector
          (create! 8890 routes)))

(defn stop! []
  (when @!connector
    (destroy! @!connector))
  (reset! !connector nil))

(defn restart! []
  (stop!)
  (start!))

(defn test-request [verb url]
  (test-request-with @!connector verb url))
