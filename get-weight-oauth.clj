#!/usr/bin/env bb

(require '[babashka.http-client :as http]
         '[babashka.cli :as cli]
         '[cheshire.core :as json]
         '[clojure.string :as str]
         '[clojure.java.io :as io])

(def withings-base-url "https://wbsapi.withings.net")
(def config-file (str (System/getProperty "user.home") "/.withings-config.json"))

(defn read-config
  "Read configuration from file"
  []
  (when (.exists (io/file config-file))
    (try
      (-> config-file
          slurp
          (json/parse-string true))
      (catch Exception e
        (println "Warning: Could not read config file:" (.getMessage e))
        nil))))

(defn token-expired?
  "Check if token is expired (with 5 minute buffer)"
  [token-data]
  (let [expires-at (:expires-at token-data)
        current-time (System/currentTimeMillis)
        buffer-time (* 5 60 1000)] ; 5 minutes buffer
    (< expires-at (+ current-time buffer-time))))

(defn refresh-access-token
  "Refresh access token using refresh token"
  [client-id client-secret refresh-token]
  (let [response (http/post (str withings-base-url "/v2/oauth2")
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

(defn write-config
  "Write configuration to file"
  [config]
  (try
    (spit config-file (json/generate-string config {:pretty true}))
    (catch Exception e
      (println "Error saving config:" (.getMessage e)))))

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
                (write-config (assoc config :token refreshed))
                refreshed))))
        (do
          (println "No valid token or refresh token available. Please run OAuth setup first.")
          nil)))))

(defn get-weight-measurements
  "Fetch weight measurements from Withings API using access token"
  [access-token]
  (let [response (http/post (str withings-base-url "/measure")
                           {:headers {"Authorization" (str "Bearer " access-token)
                                     "Content-Type" "application/x-www-form-urlencoded"}
                            :form-params {"action" "getmeas"
                                         "meastype" "1"  ; Weight measurement type
                                         "category" "1"   ; Real measurements
                                         "limit" "1"      ; Get only latest measurement
                                         "offset" "0"}})]
    (if (= 200 (:status response))
      (json/parse-string (:body response) true)
      (throw (ex-info "Failed to fetch measurements" {:response response})))))

(defn calculate-weight
  "Calculate actual weight value from API response (value * 10^unit)"
  [measurement]
  (let [value (:value measurement)
        unit (:unit measurement)]
    (* value (Math/pow 10 unit))))

(defn get-latest-weight
  "Get the latest weight measurement"
  [access-token]
  (let [response (get-weight-measurements access-token)]
    (if (= 0 (:status response))
      (let [measurements (get-in response [:body :measuregrps])
            latest-measurement (first measurements)]
        (if latest-measurement
          (let [measures (:measures latest-measurement)
                weight-measure (first (filter #(= 1 (:type %)) measures))]
            (if weight-measure
              {:weight (calculate-weight weight-measure)
               :date (:date latest-measurement)
               :unit "kg"}
              {:error "No weight measurement found"}))
          {:error "No measurements found"}))
      {:error (str "API error: " (:error response))})))

(defn print-weight-info
  "Pretty print weight information"
  [weight-info]
  (if (:error weight-info)
    (println "Error:" (:error weight-info))
    (let [date (java.time.Instant/ofEpochSecond (:date weight-info))
          formatted-date (.toString date)]
      (println (format "Latest weight: %.2f %s" (:weight weight-info) (:unit weight-info)))
      (println (format "Measured on: %s" formatted-date)))))

(defn -main [& args]
  (let [opts (cli/parse-opts args {:spec {:access-token {:alias :t
                                                        :desc "Withings API access token (optional if OAuth configured)"}
                                         :help {:alias :h
                                               :desc "Show help"}}})
        access-token (:access-token opts)]
    
    (cond
      (:help opts)
      (do
        (println "Usage: bb get-weight-oauth.clj [OPTIONS]")
        (println "")
        (println "Options:")
        (println "  -t, --access-token TOKEN  Withings API access token (optional if OAuth configured)")
        (println "  -h, --help               Show this help message")
        (println "")
        (println "Examples:")
        (println "  bb get-weight-oauth.clj                    # Use OAuth token from config")
        (println "  bb get-weight-oauth.clj -t your-token-here # Use provided token")
        (println "")
        (println "Note: Run 'bb oauth.clj --setup' first to configure OAuth authentication."))
      
      :else
      (try
        (let [token (or access-token
                       (when-let [config (read-config)]
                         (when-let [valid-token (get-valid-token config)]
                           (:access-token valid-token))))]
          (if token
            (-> token
                get-latest-weight
                print-weight-info)
            (do
              (println "Error: No access token available")
              (println "Either provide --access-token or run OAuth setup:")
              (println "  bb oauth.clj --setup --client-id YOUR_ID --client-secret YOUR_SECRET"))))
        (catch Exception e
          (println "Error fetching weight:" (.getMessage e)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))