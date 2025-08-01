(ns strava.config
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [babashka.http-client :as http]))

(def config-dir (str (System/getProperty "user.home") "/.config/clj-withings"))
(def strava-config-file (str config-dir "/strava.json"))
(def strava-token-url "https://www.strava.com/oauth/token")
(def token-buffer-ms (* 5 60 1000)) ; 5 minutes buffer

(defn ensure-config-dir
  "Ensure the configuration directory exists"
  []
  (let [dir (java.io.File. config-dir)]
    (when-not (.exists dir)
      (.mkdirs dir))))

(defn get-strava-secrets
  "Get Strava secrets from local JSON file"
  []
  (let [secrets-file (str config-dir "/secrets.json")]
    (when (.exists (io/file secrets-file))
      (try
        (let [secrets (-> secrets-file slurp (json/parse-string true))]
          (:strava secrets))
        (catch Exception e
          (println "Warning: Could not read secrets file:" (.getMessage e))
          nil)))))

(defn read-strava-config
  "Read Strava configuration from file
   Used in bb.edn: test-strava-token task"
  []
  (when (.exists (io/file strava-config-file))
    (try
      (-> strava-config-file
          slurp
          (json/parse-string true))
      (catch Exception e
        (println "Warning: Could not read Strava config file:" (.getMessage e))
        nil))))

(defn write-strava-config
  "Write Strava configuration to file"
  [config]
  (try
    (ensure-config-dir)
    (spit strava-config-file (json/generate-string config {:pretty true}))
    (println "Strava configuration saved to" strava-config-file)
    (catch Exception e
      (println "Error saving Strava config:" (.getMessage e)))))

(defn save-token
  "Save OAuth token to config file"
  [token-data]
  (let [existing-config (or (read-strava-config) {})
        existing-token (:token existing-config)
        updated-config (assoc existing-config :token token-data)]
    ;; Only write config if the token data has actually changed
    (when (not= existing-token token-data)
      (write-strava-config updated-config))))

(defn update-token
  "Update existing token in config"
  [token-data]
  (save-token token-data))

(defn parse-token-response
  "Parse token response and extract relevant fields"
  [response]
  (if (= 200 (:status response))
    (let [body (json/parse-string (:body response) true)
          expires-at (:expires_at body)]
      {:access_token (:access_token body)
       :refresh_token (:refresh_token body)
       :expires_at (when expires-at (* 1000 expires-at))}) ; Convert to milliseconds only if present
    {:error (str "Token request failed: " (:status response) " " (:body response))}))

(defn refresh-access-token
  "Refresh an expired access token"
  [client-id client-secret refresh-token]
  (let [response (http/post strava-token-url
                            {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                             :form-params {:client_id client-id
                                           :client_secret client-secret
                                           :refresh_token refresh-token
                                           :grant_type "refresh_token"}})]
    (parse-token-response response)))

(defn token-expired?
  "Check if token is expired (with buffer)"
  [token-data]
  (if (and token-data (:expires-at token-data))
    (let [expires-at (:expires-at token-data)
          now (System/currentTimeMillis)]
      (< expires-at (+ now token-buffer-ms)))
    true)) ; If no token or no expiry, consider it expired

(defn get-valid-token
  "Get a valid access token, refreshing if necessary
   Used in bb.edn: test-strava-token task"
  [config]
  (let [token-data (:token config)]
    (if (and token-data (not (token-expired? token-data)))
      token-data
      ;; Token is expired or doesn't exist, try to refresh
      (when-let [refresh-token (:refresh_token token-data)]
        (when-let [secrets (get-strava-secrets)]
          (let [refreshed (refresh-access-token
                           (:client_id secrets)
                           (:client_secret secrets)
                           refresh-token)]
            (if (:error refreshed)
              nil
              (do
                (update-token refreshed)
                refreshed))))))))

(defn get-valid-strava-token
  "Get a valid Strava token, refreshing if necessary"
  []
  (when-let [config (read-strava-config)]
    (get-valid-token config)))

(defn get-strava-credentials
  "Get Strava credentials from command line args or local secrets file"
  [{:keys [client-id client-secret redirect-uri]}]
  (or
   (when (and client-id client-secret)
     {:client-id client-id
      :client-secret client-secret
      :redirect-uri redirect-uri})
   (when-let [secrets (get-strava-secrets)]
     (when (and (:client_id secrets) (:client_secret secrets))
       {:client-id (:client_id secrets)
        :client-secret (:client_secret secrets)
        :redirect-uri (or (:redirect_uri secrets) redirect-uri)}))))
