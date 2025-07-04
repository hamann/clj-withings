#!/usr/bin/env bb

(require '[babashka.process :as process]
         '[clj-yaml.core :as yaml])

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
      (println "Error decrypting secrets:" (.getMessage e))
      nil)))

(defn get-withings-config
  "Get Withings configuration from encrypted secrets"
  []
  (when-let [secrets (decrypt-secrets "secrets.yaml")]
    (get secrets "withings")))

(defn sops-available?
  "Check if SOPS is available in PATH"
  []
  (try
    (let [result (process/shell {:out :string :err :string} "which" "sops")]
      (zero? (:exit result)))
    (catch Exception _e
      false)))

(defn -main [& args]
  (cond
    (= (first args) "check")
    (if (sops-available?)
      (println "SOPS is available")
      (println "SOPS is not available. Install from: https://github.com/mozilla/sops"))

    (= (first args) "decrypt")
    (if-let [config (get-withings-config)]
      (do
        (println "Withings configuration:")
        (println "  Client ID:" (get config "client_id"))
        (println "  Redirect URI:" (get config "redirect_uri"))
        (println "  Client Secret: [REDACTED]"))
      (println "Failed to decrypt secrets"))

    :else
    (do
      (println "Usage: bb sops-helper.clj [check|decrypt]")
      (println "  check   - Check if SOPS is available")
      (println "  decrypt - Decrypt and show secrets (redacted)"))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
