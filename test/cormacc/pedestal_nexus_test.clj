(ns cormacc.pedestal-nexus-test
  (:require [clojure.test :refer [deftest is testing]]
            [cormacc.pedestal-nexus :as sut])) ; system under test

(deftest includes-action
  (testing "Should return false given no actions"
    (is (false? (sut/includes-action [] :http/response))))
  (testing "Should return false given no matching actions"
    (is (false? (sut/includes-action [[:bla/bla 1]
                                      [:bleh/bleh 2 4 3]]
                                     :http/response))))
  (testing "Should return true given single matching action"
    (is (true? (sut/includes-action [[:http/response :ok "Bla"]]
                                    :http/response))))
  (testing "Should return true given nested match"
    (is (true? (sut/includes-action [[:do/something 9]
                                     [:do/something-async {:then [:http/response :ok "Bla"]}]]
                                    :http/response))))
;;
  )
