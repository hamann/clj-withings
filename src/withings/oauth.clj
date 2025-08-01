(ns withings.oauth
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]
            [withings.config :as config]))

(def withings-auth-url "https://account.withings.com/oauth2_user/authorize2")
(def withings-token-url "https://wbsapi.withings.net/v2/oauth2")

(defn parse-token-response
  "Parse token response from Withings API"
  [response]
  (if-not (= 200 (:status response))
    {:error (str "HTTP error: " (:status response) " " (:body response))}
    (let [token-data (json/parse-string (:body response) true)]
      (if-not (= 0 (:status token-data))
        {:error (str "Token request failed: " (:error token-data))}
        (let [body (:body token-data)]
          {:access-token (:access_token body)
           :refresh-token (:refresh_token body)
           :expires-in (:expires_in body)
           :expires-at (+ (System/currentTimeMillis) (* (:expires_in body) 1000))
           :token-type (:token_type body)
           :scope (:scope body)})))))

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
  (-> (http/post withings-token-url
                 {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                  :form-params {"action" "requesttoken"
                                "grant_type" "authorization_code"
                                "client_id" client-id
                                "client_secret" client-secret
                                "code" code
                                "redirect_uri" redirect-uri}})
      parse-token-response))

(defn refresh-access-token
  "Refresh access token using refresh token"
  [client-id client-secret refresh-token]
  (-> (http/post withings-token-url
                 {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                  :form-params {"action" "requesttoken"
                                "grant_type" "refresh_token"
                                "client_id" client-id
                                "client_secret" client-secret
                                "refresh_token" refresh-token}})
      parse-token-response))

(def token-buffer-ms (* 5 60 1000))

(defn token-expired?
  "Check if token is expired (with 5 minute buffer)"
  [token-data]
  (< (:expires-at token-data)
     (+ (System/currentTimeMillis) token-buffer-ms)))

(defn get-valid-token
  "Get a valid access token, refreshing if necessary"
  [config]
  (let [token-data (:token config)]
    (if (and token-data (not (token-expired? token-data)))
      token-data
      (when-let [refresh-token (:refresh-token token-data)]
        (println "Token expired, refreshing...")
        ;; Get credentials from secrets file, not from config
        (when-let [secrets (config/get-withings-secrets)]
          (let [refreshed (refresh-access-token
                           (:client_id secrets)
                           (:client_secret secrets)
                           refresh-token)]
            (if (:error refreshed)
              (do
                (println "Token refresh failed:" (:error refreshed))
                nil)
              (do
                (config/write-config (assoc config :token refreshed))
                refreshed))))))))

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
            (let [config {:token token-result}] ; Only store the token, not credentials
              (config/write-config config)
              (println "OAuth setup complete!"))))))))