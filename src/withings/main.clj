(ns withings.main
  (:require [babashka.cli :as cli]
            [withings.oauth :as oauth]
            [withings.api :as api]
            [withings.config :as config]))

(defn setup-command [opts]
  (if (:help opts)
    (do
      (println "Usage: bb -m clj-withings.main setup [OPTIONS]")
      (println)
      (println "Options:")
      (println "  -c, --client-id ID       Withings client ID")
      (println "  -S, --client-secret SEC  Withings client secret")
      (println "  -r, --redirect-uri URI   OAuth redirect URI (default: http://localhost/callback)")
      (println "  -h, --help               Show this help message")
      (println)
      (println "Examples:")
      (println "  bb -m clj-withings.main setup --client-id YOUR_ID --client-secret YOUR_SECRET")
      (println "  bb -m clj-withings.main setup                 # Use SOPS secrets"))
    (if-let [creds (config/get-credentials opts)]
      (do
        (when-not (and (:client-id opts) (:client-secret opts))
          (println "Using credentials from SOPS secrets file"))
        (oauth/setup-oauth (:client-id creds) (:client-secret creds) (:redirect-uri creds)))
      (do
        (println "Error: Client credentials required")
        (println "Either:")
        (println "  1. Provide --client-id and --client-secret")
        (println "  2. Configure secrets.yaml with SOPS and run: bb -m clj-withings.main setup")))))

(defn weight-command [_opts]
  (let [result (api/get-weight-with-auth)]
    (if (:error result)
      (do
        (println "Error:" (:error result))
        (System/exit 1))
      (do
        (println "Latest weight:" (:formatted-weight result))
        (println "Measured on:" (:formatted-date result))))))

(defn test-token-command [_opts]
  (let [config (config/read-config)]
    (if config
      (let [token (oauth/get-valid-token config)]
        (if token
          (println "Token is valid, expires at:" (java.time.Instant/ofEpochMilli (:expires-at token)))
          (println "No valid token available")))
      (println "No configuration found. Run setup first."))))

(defn check-sops-command [_opts]
  (let [secrets (config/get-withings-secrets)]
    (if secrets
      (do
        (println "SOPS configuration is working")
        (println "Found client_id:" (boolean (:client_id secrets)))
        (println "Found client_secret:" (boolean (:client_secret secrets)))
        (println "Found redirect_uri:" (boolean (:redirect_uri secrets))))
      (println "SOPS configuration not found or not working"))))

(defn -main [& args]
  (let [commands {"setup" setup-command
                  "weight" weight-command
                  "test-token" test-token-command
                  "check-sops" check-sops-command}
        [command & rest-args] args]
    (if command
      (if-let [cmd-fn (get commands command)]
        (let [opts (cli/parse-opts rest-args {:spec {:client-id {:desc "Withings client ID"
                                                               :alias :c}
                                                   :client-secret {:desc "Withings client secret"
                                                                  :alias :S}
                                                   :redirect-uri {:desc "OAuth redirect URI"
                                                                 :alias :r
                                                                 :default "http://localhost/callback"}
                                                   :help {:desc "Show help"
                                                         :alias :h}}})]
          (cmd-fn opts))
        (do
          (println "Unknown command:" command)
          (println "Available commands: setup, weight, test-token, check-sops")))
      (do
        (println "Usage: bb -m clj-withings.main <command> [options]")
        (println "Commands:")
        (println "  setup       Setup OAuth2 authentication")
        (println "  weight      Get current weight")
        (println "  test-token  Test current token validity")
        (println "  check-sops  Check SOPS configuration")))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))