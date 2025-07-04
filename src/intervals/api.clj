(ns intervals.api
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [intervals.config :as config]))

(def intervals-api-url "https://intervals.icu/api/v1")

(defn post-weight
  "Post weight data to intervals.icu"
  [api-key athlete-id weight-kg date]
  (let [url (str intervals-api-url "/athlete/" athlete-id "/wellness-bulk")
        payload [{:id date :weight weight-kg}]
        response (http/put url
                          {:headers {"Content-Type" "application/json"}
                           :basic-auth ["API_KEY" api-key]
                           :body (json/generate-string payload)})]
    (if (= 200 (:status response))
      {:success true :message "Weight uploaded successfully"}
      {:error (str "HTTP error: " (:status response) " " (:body response))})))

(defn post-weight-with-auth
  "Post weight data using configured API key"
  [weight-kg date]
  (let [api-key (config/get-intervals-api-key)
        athlete-id (config/get-intervals-athlete-id)]
    (cond
      (not api-key) {:error "No intervals.icu API key configured"}
      (not athlete-id) {:error "No intervals.icu athlete ID configured"}
      :else (post-weight api-key athlete-id weight-kg date))))