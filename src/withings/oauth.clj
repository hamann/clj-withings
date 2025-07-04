(ns withings.oauth
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]
            [withings.config :as config]))

(def withings-auth-url "https://account.withings.com/oauth2_user/authorize2")
(def withings-token-url "https://wbsapi.withings.net/v2/oauth2")

(defn generate-auth-url
  "Generate OAuth2 authorization URL"
  [client-id redirect-uri]
  (let [params {"response_type" "code"
                "client_id" client-id
                "redirect_uri" redirect-uri
                "scope" "user.metrics"
                "state" (str (java.util.UUID/randomUUID))}
        query-string (str/join "&" (map (fn [[k v]] (str k "=" (java.net.URLEncoder/encode v "UTF-8"))) params))]
    (str withings-auth-url "?" query-string)))

(defn exchange-code-for-token
  "Exchange authorization code for access token"
  [client-id client-secret code redirect-uri]
  (let [response (http/post withings-token-url
                           {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                            :form-params {"action" "requesttoken"
                                         "grant_type" "authorization_code"
                                         "client_id" client-id
                                         "client_secret" client-secret
                                         "code" code
                                         "redirect_uri" redirect-uri}})]
    (if (= 200 (:status response))
      (let [token-data (json/parse-string (:body response) true)]
        (if (= 0 (:status token-data))
          (let [body (:body token-data)]
            {:access-token (:access_token body)
             :refresh-token (:refresh_token body)
             :expires-in (:expires_in body)
             :expires-at (+ (System/currentTimeMillis) (* (:expires_in body) 1000))
             :token-type (:token_type body)
             :scope (:scope body)})
          {:error (str "Token exchange failed: " (:error token-data))}))
      {:error (str "HTTP error: " (:status response) " " (:body response))})))

(defn refresh-access-token
  "Refresh access token using refresh token"
  [client-id client-secret refresh-token]
  (let [response (http/post withings-token-url
                           {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                            :form-params {"action" "requesttoken"
                                         "grant_type" "refresh_token"
                                         "client_id" client-id
                                         "client_secret" client-secret
                                         "refresh_token" refresh-token}})]
    (if (= 200 (:status response))
      (let [token-data (json/parse-string (:body response) true)]
        (if (= 0 (:status token-data))
          (let [body (:body token-data)]
            {:access-token (:access_token body)
             :refresh-token (:refresh_token body)
             :expires-in (:expires_in body)
             :expires-at (+ (System/currentTimeMillis) (* (:expires_in body) 1000))
             :token-type (:token_type body)
             :scope (:scope body)})
          {:error (str "Token refresh failed: " (:error token-data))}))
      {:error (str "HTTP error: " (:status response) " " (:body response))})))

(defn token-expired?
  "Check if token is expired (with 5 minute buffer)"
  [token-data]
  (let [expires-at (:expires-at token-data)
        current-time (System/currentTimeMillis)
        buffer-time (* 5 60 1000)] ; 5 minutes buffer
    (< expires-at (+ current-time buffer-time))))

(defn get-valid-token
  "Get a valid access token, refreshing if necessary"
  [config]
  (let [token-data (:token config)]
    (if (and token-data (not (token-expired? token-data)))
      token-data
      (if-let [refresh-token (:refresh-token token-data)]
        (do
          (println "Token expired, refreshing...")
          (let [refreshed (refresh-access-token 
                          (:client-id config)
                          (:client-secret config)
                          refresh-token)]
            (if (:error refreshed)
              (do
                (println "Token refresh failed:" (:error refreshed))
                nil)
              (do
                (config/write-config (assoc config :token refreshed))
                refreshed))))
        (do
          (println "No valid token or refresh token available. Please re-authorize.")
          nil)))))

(defn setup-oauth
  "Interactive OAuth setup"
  [client-id client-secret redirect-uri]
  (let [auth-url (generate-auth-url client-id redirect-uri)]
    (println "Please visit the following URL to authorize the application:")
    (println auth-url)
    (println)
    (print "Enter the authorization code from the redirect URL: ")
    (flush)
    (let [code (str/trim (read-line))]
      (if (str/blank? code)
        (println "No authorization code provided")
        (let [token-result (exchange-code-for-token client-id client-secret code redirect-uri)]
          (if (:error token-result)
            (println "Error:" (:error token-result))
            (let [config {:client-id client-id
                         :client-secret client-secret
                         :redirect-uri redirect-uri
                         :token token-result}]
              (config/write-config config)
              (println "OAuth setup complete!"))))))))