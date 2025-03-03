(ns httpkit.kit-times-test
  (:use clojure.test)
  (:require [org.httpkit.client :as http]
            [httpkit.stub :refer :all]))

(deftest test-times-verification
  (testing "passes when route is called expected number of times"
    (with-http-stub
      {"http://example.com"
       {:get (fn [_] {:status 200 :body "ok"})
        :times 2}}
      (http/get "http://example.com" {} (fn [_] nil))
      (http/get "http://example.com" {} (fn [_] nil))))

  (testing "fails when route is called less than expected times"
    (try
      (with-http-stub
        {"http://example.com"
         {:get (fn [_] {:status 200 :body "ok"})
          :times 2}}
        (http/get "http://example.com" {} (fn [_] nil)))
      (is false "Should have thrown an exception")
      (catch Exception e
        (is (= (.getMessage e)
               "Expected route 'http://example.com:get' to be called 2 times but was called 1 times")))))

  (testing "fails when route is called more than expected times"
    (try
      (with-http-stub
        {"http://example.com"
         {:get (fn [_] {:status 200 :body "ok"})
          :times 1}}
        (http/get "http://example.com" {} (fn [_] nil))
        (http/get "http://example.com" {} (fn [_] nil)))
      (is false "Should have thrown an exception")
      (catch Exception e
        (is (= (.getMessage e)
               "Expected route 'http://example.com:get' to be called 1 times but was called 2 times"))))))

(deftest test-method-specific-times
  (testing "passes when methods are called their expected number of times"
    (with-http-stub
      {"http://example.com"
       {:get (fn [_] {:status 200 :body "ok"})
        :post (fn [_] {:status 201 :body "created"})
        :times {:get 1 :post 2}}}
      (http/get "http://example.com" {} (fn [_] nil))
      (http/post "http://example.com" {} (fn [_] nil))
      (http/post "http://example.com" {} (fn [_] nil))))

  (testing "fails when any method is not called its expected number of times"
    (try
      (with-http-stub
        {"http://example.com"
         {:get (fn [_] {:status 200 :body "ok"})
          :post (fn [_] {:status 201 :body "created"})
          :times {:get 1 :post 2}}}
        (http/get "http://example.com" {} (fn [_] nil))
        (http/post "http://example.com" {} (fn [_] nil)))
      (is false "Should have thrown an exception")
      (catch Exception e
        (is (= (.getMessage e)
               "Expected route 'http://example.com:post' to be called 2 times but was called 1 times"))))))

(deftest test-times-edge-cases
  (testing "passes when route with :times 0 is never called"
    (with-http-stub
      {"http://example.com"
       {:get (fn [_] {:status 200 :body "ok"})
        :times 0}}))

  (testing "fails when route with :times 0 is called"
    (try
      (with-http-stub
        {"http://example.com"
         {:get (fn [_] {:status 200 :body "ok"})
          :times 0}}
        (http/get "http://example.com" {} (fn [_] nil)))
      (is false "Should have thrown an exception")
      (catch Exception e
        (is (= (.getMessage e)
               "Expected route 'http://example.com:get' to be called 0 times but was called 1 times"))))))
