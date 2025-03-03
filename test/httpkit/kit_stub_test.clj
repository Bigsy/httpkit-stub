(ns httpkit.kit-stub-test
  (:require [clojure.test :refer :all]
            [httpkit.stub :refer :all]
            [org.httpkit.client :as http]))

(deftest test-simple-get
  (testing "Basic GET request with string URL"
    (with-http-stub {"http://example.com" 
                    {:get (fn [_] {:status 200
                                 :headers {"Content-Type" "text/plain"}
                                 :body "Hello World"})}}
      (let [p (promise)]
        (http/get "http://example.com" {}
                 (fn [{:keys [status headers body]}]
                   (is (= 200 status))
                   (is (= "text/plain" (get headers "Content-Type")))
                   (is (= "Hello World" body))
                   (deliver p :done)))
        @p))))

(deftest test-pattern-matching
  (testing "Pattern matching for URLs"
    (with-http-stub {#"http://example.com/\d+" 
                    {:get (fn [_] {:status 200
                                 :body "Numbered resource"})}}
      (let [p (promise)]
        (http/get "http://example.com/123" {}
                 (fn [{:keys [status body]}]
                   (is (= 200 status))
                   (is (= "Numbered resource" body))
                   (deliver p :done)))
        @p))))

(deftest test-method-specific-response
  (testing "Different responses for different HTTP methods"
    (with-http-stub {"http://example.com" 
                    {:post (fn [_] {:status 201 :body "Created"})
                     :get (fn [_] {:status 200 :body "OK"})}}
      (let [p1 (promise)
            p2 (promise)]
        (http/post "http://example.com" {}
                  (fn [{:keys [status body]}]
                    (is (= 201 status))
                    (is (= "Created" body))
                    (deliver p1 :done)))
        (http/get "http://example.com" {}
                 (fn [{:keys [status body]}]
                   (is (= 200 status))
                   (is (= "OK" body))
                   (deliver p2 :done)))
        [@p1 @p2]))))

(deftest test-query-params
  (testing "Query params matching"
    (with-http-stub {"http://example.com/api" 
                    {:get (fn [req] 
                           (if (= (get-in req [:query-params :q]) "test")
                             {:status 200 :body "Found"}
                             {:status 404 :body "Not Found"}))}}
      (let [p (promise)]
        (http/get "http://example.com/api" {:query-params {:q "test"}}
                 (fn [{:keys [status body]}]
                   (is (= 200 status))
                   (is (= "Found" body))
                   (deliver p :done)))
        @p))))

(deftest test-any-method
  (testing "Any method matching"
    (with-http-stub {"http://example.com" 
                    {:any (fn [_] {:status 200 :body "Any"})}}
      (let [p1 (promise)
            p2 (promise)]
        (http/get "http://example.com" {}
                 (fn [{:keys [body]}]
                   (is (= "Any" body))
                   (deliver p1 :done)))
        (http/post "http://example.com" {}
                  (fn [{:keys [body]}]
                    (is (= "Any" body))
                    (deliver p2 :done)))
        [@p1 @p2]))))

(deftest test-http-stub-in-isolation
  (testing "throws exception for unmatched routes in isolation mode"
    (let [p (promise)]
      (try
        (with-http-stub-in-isolation
          {"http://example.com/matched" 
           {:get (fn [_] {:status 200 :body "OK"})}}
          (http/get "http://example.com/unmatched" {}
                   (fn [response]
                     (deliver p response)))
          (is false "Should have thrown an exception"))
        (catch Exception e
          (is (re-find #"No matching stub route" (.getMessage e)))))))
  
  (testing "matches routes correctly in isolation mode"
    (let [p (promise)]
      (with-http-stub-in-isolation
        {"http://example.com/matched" 
         {:get (fn [_] {:status 200 :body "OK"})}}
        (http/get "http://example.com/matched" {}
                 (fn [{:keys [status body]}]
                   (is (= 200 status))
                   (is (= "OK" body))
                   (deliver p :done))))
      (is (= :done @p)))))

(deftest test-global-http-stub-in-isolation
  (testing "global stub in isolation mode throws exception for unmatched routes"
    (let [p (promise)]
      (try
        (with-global-http-stub-in-isolation {"http://example.com" 
                                           {:get (fn [_] {:status 200})}}
          (http/get "http://other.com" {}
                   (fn [response]
                     (is false "Should not get here")
                     (deliver p :done))))
        (catch Exception e
          (is (re-find #"No matching stub route found" (.getMessage e)))
          (deliver p :done)))
      @p))

  (testing "global stub in isolation mode matches routes and returns response"
    (let [p (promise)]
      (with-global-http-stub-in-isolation {"http://example.com" 
                                         {:get (fn [_] {:status 200 :body "success"})}}
        (http/get "http://example.com" {}
                 (fn [{:keys [status body]}]
                   (is (= 200 status))
                   (is (= "success" body))
                   (deliver p :done)))
        @p)))

  (testing "global stub in isolation mode preserves dynamic bindings across multiple calls"
    (let [p1 (promise)
          p2 (promise)]
      (with-global-http-stub-in-isolation {"http://example.com" 
                                         {:get (fn [_] {:status 200 :body "first"})}}
        (http/get "http://example.com" {}
                 (fn [{:keys [body]}]
                   (is (= "first" body))
                   (deliver p1 :done)))
        (try
          (http/get "http://other.com" {}
                   (fn [_] (deliver p2 :unexpected)))
          (catch Exception e
            (is (re-find #"No matching stub route found" (.getMessage e)))
            (deliver p2 :done))))
      [@p1 @p2])))

(deftest test-global-http-stub
  (testing "matches routes correctly with global stub"
    (let [p (promise)]
      (with-global-http-stub
        {"http://example.com/matched" 
         {:get (fn [_] {:status 200 :body "OK"})}}
        (http/get "http://example.com/matched" {}
                 (fn [{:keys [status body]}]
                   (is (= 200 status))
                   (is (= "OK" body))
                   (deliver p :done))))
      (is (= :done @p))))

  (testing "preserves global stub across multiple calls"
    (let [p1 (promise)
          p2 (promise)]
      (with-global-http-stub
        {"http://example.com" 
         {:get (fn [_] {:status 200 :body "First"})
          :post (fn [_] {:status 201 :body "Second"})}}
        (http/get "http://example.com" {}
                 (fn [{:keys [status body]}]
                   (is (= 200 status))
                   (is (= "First" body))
                   (deliver p1 :done)))
        (http/post "http://example.com" {}
                  (fn [{:keys [status body]}]
                    (is (= 201 status))
                    (is (= "Second" body))
                    (deliver p2 :done))))
      (is (= [:done :done] [@p1 @p2]))))

  (testing "allows real HTTP requests for unmatched routes"
    (let [p (promise)]
      (with-global-http-stub
        {"http://example.com/matched" 
         {:get (fn [_] {:status 200 :body "OK"})}}
        ;; Using a mock for the real HTTP request since we don't want actual network calls in tests
        (with-redefs [org.httpkit.client/request
                     (fn [_ cb] (cb {:status 404 :body "Not Found"}))]
          (http/get "http://example.com/unmatched" {}
                   (fn [{:keys [status body]}]
                     (is (= 404 status))
                     (is (= "Not Found" body))
                     (deliver p :done)))))
      (is (= :done @p)))))
