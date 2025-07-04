#!/usr/bin/env bb

(require '[babashka.http-client :as http]
         '[babashka.cli :as cli]
         '[cheshire.core :as json]
         '[clojure.string :as str]
         '[clojure.java.io :as io]
         '[babashka.process :as process]
         '[clj-yaml.core :as yaml])

(def withings-auth-url "https://account.withings.com/oauth2_user/authorize2")
(def withings-token-url "https://wbsapi.withings.net/v2/oauth2")
(def config-file (str (System/getProperty "user.home") "/.withings-config.json"))
(def secrets-file "secrets.yaml")

(defn decrypt-secrets
  "Decrypt secrets file using SOPS"
  [secrets-file]
  (try
    (let [result (process/shell {:out :string
                                :err :string} 
                               "sops" "-d" secrets-file)]
      (if (zero? (:exit result))
        (yaml/parse-string (:out result))
        (throw (ex-info "SOPS decryption failed" {:error (:err result)}))))
    (catch Exception e
      (println "Warning: Could not decrypt secrets:" (.getMessage e))
      nil)))

(defn get-withings-secrets
  "Get Withings configuration from encrypted secrets"
  []
  (when (.exists (io/file secrets-file))
    (when-let [secrets (decrypt-secrets secrets-file)]
      (get secrets :withings))))

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

(defn write-config
  "Write configuration to file"
  [config]
  (try
    (spit config-file (json/generate-string config {:pretty true}))
    (println "Configuration saved to" config-file)
    (catch Exception e
      (println "Error saving config:" (.getMessage e)))))

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
                (write-config (assoc config :token refreshed))
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
              (write-config config)
              (println "OAuth setup complete!"))))))))

(defn -main [& args]
  (let [opts (cli/parse-opts args {:spec {:setup {:desc "Setup OAuth2 authentication"
                                                 :alias :s}
                                         :client-id {:desc "Withings client ID"
                                                    :alias :c}
                                         :client-secret {:desc "Withings client secret"
                                                        :alias :S}
                                         :redirect-uri {:desc "OAuth redirect URI"
                                                       :alias :r
                                                       :default "http://localhost:8080/callback"}
                                         :test-token {:desc "Test current token"
                                                     :alias :t}
                                         :help {:desc "Show help"
                                               :alias :h}}})]
    
    (cond
      (:help opts)
      (do
        (println "Usage: bb oauth.clj [OPTIONS]")
        (println)
        (println "Options:")
        (println "  -s, --setup              Setup OAuth2 authentication")
        (println "  -c, --client-id ID       Withings client ID")
        (println "  -S, --client-secret SEC  Withings client secret")
        (println "  -r, --redirect-uri URI   OAuth redirect URI (default: http://localhost:8080/callback)")
        (println "  -t, --test-token         Test current token validity")
        (println "  -h, --help               Show this help message")
        (println)
        (println "Examples:")
        (println "  bb oauth.clj --setup --client-id YOUR_ID --client-secret YOUR_SECRET")
        (println "  bb oauth.clj --setup                                               # Use SOPS secrets")
        (println "  bb oauth.clj --test-token"))
      
      (:setup opts)
      (let [client-id (:client-id opts)
            client-secret (:client-secret opts)
            redirect-uri (:redirect-uri opts)
            secrets (get-withings-secrets)]
        (cond
          ; Use provided command line args
          (and client-id client-secret)
          (setup-oauth client-id client-secret redirect-uri)
          
          ; Use SOPS secrets
          (and secrets (get secrets :client_id) (get secrets :client_secret))
          (let [sops-client-id (get secrets :client_id)
                sops-client-secret (get secrets :client_secret)
                sops-redirect-uri (or (get secrets :redirect_uri) redirect-uri)]
            (println "Using credentials from SOPS secrets file")
            (setup-oauth sops-client-id sops-client-secret sops-redirect-uri))
          
          ; Neither available
          :else
          (do
            (println "Error: Client credentials required")
            (println "Either:")
            (println "  1. Provide --client-id and --client-secret")
            (println "  2. Configure secrets.yaml with SOPS and run: bb oauth.clj --setup"))))
      
      (:test-token opts)
      (let [config (read-config)]
        (if config
          (let [token (get-valid-token config)]
            (if token
              (println "Token is valid, expires at:" (java.time.Instant/ofEpochMilli (:expires-at token)))
              (println "No valid token available")))
          (println "No configuration found. Run setup first.")))
      
      :else
      (println "Use --help for usage information"))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))