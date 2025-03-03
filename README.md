# httpkit-stub

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.bigsy/httpkit-stub.svg)](https://clojars.org/org.clojars.bigsy/httpkit-stub)

A library for stubbing out HTTP Kit requests in Clojure.

## Installation

Add the following dependency to your project.clj:

```clojure
[org.clojars.bigsy/httpkit-stub "0.0.1"]
```

## Usage

```clojure
(ns myapp.test.core
   (:require [org.httpkit.client :as http])
   (:use httpkit.stub))
```

The public interface consists of macros:

* `with-http-stub` - lets you override HTTP requests that match keys in the provided map
* `with-http-stub-in-isolation` - does the same but throws if a request does not match any key
* `with-global-http-stub`
* `with-global-http-stub-in-isolation`

### Examples

```clojure
;; Basic example
(with-http-stub
  {"https://api.weather.com/v1/current"
   (fn [request] {:status 200 :headers {} :body "Sunny, 72°F"})}
  @(http/get "https://api.weather.com/v1/current"))

;; Route matching examples
(with-http-stub
  {;; Exact string match:
   "https://api.github.com/users/octocat"
   (fn [request] {:status 200 :headers {} :body "{\"name\": \"The Octocat\"}"})

   ;; Exact string match with query params:
   "https://api.spotify.com/v1/search?q=beethoven&type=track"
   (fn [request] {:status 200 :headers {} :body "{\"tracks\": [...]}"})

   ;; Regexp match:
   #"https://([a-z]+).stripe.com/v1/customers"
   (fn [req] {:status 200 :headers {} :body "{\"customer\": \"cus_123\"}"})

   ;; Match based on HTTP method:
   "https://api.slack.com/api/chat.postMessage"
   {:post (fn [req] {:status 200 :headers {} :body "{\"ok\": true}"})}

   ;; Match multiple HTTP methods:
   "https://api.dropbox.com/2/files"
   {:get    (fn [req] {:status 200 :headers {} :body "{\"entries\": [...]}"})
    :delete (fn [req] {:status 401 :headers {} :body "{\"error\": \"Unauthorized\"}"})
    :any    (fn [req] {:status 200 :headers {} :body "{\"status\": \"success\"}"})}

   ;; Match using query params as a map
   {:address "https://api.openai.com/v1/chat/completions" :query-params {:model "gpt-4"}}
   (fn [req] {:status 200 :headers {} :body "{\"choices\": [...]}"})

   ;; If not given, the stub response status will be 200 and the body will be "".
   "https://api.twilio.com/2010-04-01/Messages"
   (constantly {})}

 ;; Your tests with requests here
 )
```

### Call Count Validation

You can specify and validate the number of times a route should be called using the `:times` option. There are two supported formats:

#### Simple Format
The `:times` option can be specified as a sibling of the HTTP methods:

```clojure
(with-http-stub
  {"https://api.example.com/data"
   {:get (fn [_] {:status 200 :body "ok"})
    :times 2}}
  
  ;; This will pass - route is called exactly twice as expected
  @(http/get "https://api.example.com/data")
  @(http/get "https://api.example.com/data"))
```

#### Per-Method Format
For more granular control, `:times` can be a map specifying counts per HTTP method:

```clojure
(with-http-stub
  {"https://api.example.com/data"
   {:get (fn [_] {:status 200 :body "ok"})
    :post (fn [_] {:status 201 :body "created"})
    :times {:get 2 :post 1}}}
  
  ;; This will pass - GET called twice, POST called once
  @(http/get "https://api.example.com/data")
  @(http/get "https://api.example.com/data")
  @(http/post "https://api.example.com/data"))
```



## License

MIT License

Copyright (c) 2025

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
