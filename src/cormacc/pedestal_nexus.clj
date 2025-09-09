(ns cormacc.pedestal-nexus
  (:require [clojure.core.async :as async]
            [nexus.core :as nexus]))

;; See https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status
(def http-status-codes
  {;;Informational
   :continue 100
   ;;Successful
   :ok 200
   :created 201
   :accepted 202
   :no-content 204
   :reset-content 205
   :partial-content 206
   ;;Redirection
   ;;Error
   :bad-request 400
   :unauthorized 401
   :forbidden 403
   :not-found 404
   :method-not-allowed 405
   :not-acceptable 406
   :request-timeout 408
   :conflict 409
   :gone 410
   :length-required 411
   :precondition-failed 412
   :content-too-large 413
   :uri-too-long 414
   :unsupported-media-type 415
   :im-a-teapot 418
   ;;Server error
   :internal-server-error 500
   :not-implemented 501
   :bad-gateway 502
   :service-unavailable 503
   :gateway-timeout 504
   :http-version-not-supported 505
   :variant-also-negotiates 506
   :not-extended 510
   :network-authentication-required 511})

(defn lookup-status-code [code] (or (some->> code (get http-status-codes)) (:internal-server-error http-status-codes)))

(def nexus-core
  {:nexus/effects
   {:http/response
    (fn [{:keys [dispatch-data] :as _ctx} _sys status body & {:as headers}]
      (let [status-code  (if (keyword? status)
                           (lookup-status-code status)
                           status)]
        (async/go
          (async/>! (:result-chan dispatch-data) (assoc dispatch-data :response
                                                          {:status status-code
                                                           :body body
                                                           :headers headers})))))}})

(defn- find-deep [pred data]
  (->> data
       (tree-seq coll? seq)
       (some #(when (pred %) [%]))))

(defn includes-action [actions action-id]
  (some?
   (find-deep #(and (vector? %) (= action-id (first %))) actions)))

(defn- create-nexus-interceptor [nexus-app system]
  (let [nexus (merge-with merge nexus-core nexus-app)
        system-deref (:nexus/system->state nexus)]
    {:name :nexus-interceptor
     :enter
     (fn [context]
       (assoc context
              :state (system-deref system)
              :actions []))
     :leave
     (fn [{:keys [actions] :as context}]
       (let [result-chan (async/chan)
             ;;N.B. the context arg passed here is available as :dispatch-data within the nexus effect context map
             {:keys [errors]} (nexus/dispatch nexus system (assoc context :result-chan result-chan) actions)]
         ;;TODO: Optionally pass in an error callback...
         (when-let [error (->> errors (keep :err) first)]
           (throw error))
         (if (includes-action actions :http/response)
           ;;Wait for the response on an async channel
           result-chan
           ;;Return the context and let pedestal do its default handling
           context)))}))

(defn- var-name=>keyword [the-var]
  (-> the-var meta :name keyword))

(defn- =>action-interceptor [action-generator]
  {:name (var-name=>keyword action-generator)
   :enter
   (fn [context]
     (assoc context :actions (action-generator context)))})

(defn create-nexus-wrapper [nexus system]
  (let [nexus-interceptor (create-nexus-interceptor nexus system)]
    (fn [action-generator]
      [nexus-interceptor (=>action-interceptor action-generator)])))
