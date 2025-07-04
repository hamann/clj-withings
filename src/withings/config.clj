(ns withings.config
  (:require [babashka.process :as process]
            [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [clojure.java.io :as io]))

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

(defn get-credentials
  "Get credentials from command line args or SOPS secrets"
  [opts]
  (let [client-id (:client-id opts)
        client-secret (:client-secret opts)
        redirect-uri (:redirect-uri opts)
        secrets (get-withings-secrets)]
    (cond
      ; Use provided command line args
      (and client-id client-secret)
      {:client-id client-id
       :client-secret client-secret
       :redirect-uri redirect-uri}
      
      ; Use SOPS secrets
      (and secrets (get secrets :client_id) (get secrets :client_secret))
      {:client-id (get secrets :client_id)
       :client-secret (get secrets :client_secret)
       :redirect-uri (or (get secrets :redirect_uri) redirect-uri)}
      
      ; Neither available
      :else
      nil)))