(ns strava.oauth
  (:require [babashka.http-client :as http]
            [clojure.string :as str]
            [strava.config :as config]))

(def strava-auth-url "https://www.strava.com/oauth/authorize")
(def strava-token-url "https://www.strava.com/oauth/token")

(defn generate-auth-url
  "Generate Strava OAuth authorization URL"
  [client-id redirect-uri]
  (str strava-auth-url
       "?client_id=" client-id
       "&response_type=code"
       "&redirect_uri=" redirect-uri
       "&approval_prompt=force"
       "&scope=profile:write"))

(defn exchange-code-for-token
  "Exchange authorization code for access token"
  [client-id client-secret code]
  (let [response (http/post strava-token-url
                            {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                             :form-params {:client_id client-id
                                           :client_secret client-secret
                                           :code code
                                           :grant_type "authorization_code"}})]
    (config/parse-token-response response)))

(defn setup-oauth
  "Setup OAuth flow for Strava"
  [client-id client-secret redirect-uri]
  (let [auth-url (generate-auth-url client-id redirect-uri)]
    (println "1. Open this URL in your browser:")
    (println auth-url)
    (println)
    (println "2. After authorizing, you'll be redirected to your callback URL.")
    (println "3. Copy the 'code' parameter from the URL and paste it here:")
    (print "Authorization code: ")
    (flush)
    (let [code (read-line)]
      (if (str/blank? code)
        (println "No code provided. Setup cancelled.")
        (let [token-result (exchange-code-for-token client-id client-secret code)]
          (if (:error token-result)
            (println "Error exchanging code for token:" (:error token-result))
            (do
              (config/save-token token-result)
              (println "✅ Strava OAuth setup complete!")
              (println "Token saved. You can now use 'bb push-to-strava'."))))))))
