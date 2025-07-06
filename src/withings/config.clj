(ns withings.config
  (:require [babashka.process :as process]
            [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [clojure.java.io :as io]))

(def config-dir (str (System/getProperty "user.home") "/.config/clj-withings"))
(def config-file (str config-dir "/withings.json"))
(def secrets-file "secrets.yaml")

(defn ensure-config-dir
  "Ensure the configuration directory exists"
  []
  (let [dir (java.io.File. config-dir)]
    (when-not (.exists dir)
      (.mkdirs dir))))

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
  "Get Withings configuration from encrypted secrets
   Used in bb.edn: check-sops task"
  []
  (when (.exists (io/file secrets-file))
    (some-> (decrypt-secrets secrets-file)
            :withings)))

(defn read-config
  "Read configuration from file
   Used in bb.edn: test-token task"
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
  "Write configuration to file
   Used in bb.edn: setup task (via oauth/setup-oauth)"
  [config]
  (try
    (ensure-config-dir)
    (spit config-file (json/generate-string config {:pretty true}))
    (println "Configuration saved to" config-file)
    (catch Exception e
      (println "Error saving config:" (.getMessage e)))))

(defn get-credentials
  "Get credentials from command line args or SOPS secrets
   Used in bb.edn: setup task"
  [{:keys [client-id client-secret redirect-uri]}]
  (or
   (when (and client-id client-secret)
     {:client-id client-id
      :client-secret client-secret
      :redirect-uri redirect-uri})
   (when-let [secrets (get-withings-secrets)]
     (when (and (:client_id secrets) (:client_secret secrets))
       {:client-id (:client_id secrets)
        :client-secret (:client_secret secrets)
        :redirect-uri (or (:redirect_uri secrets) redirect-uri)}))))

(defn migrate-old-config
  "Migrate from old config location to new centralized location"
  []
  (let [old-withings-config (str (System/getProperty "user.home") "/.withings-config.json")
        old-strava-config (str (System/getProperty "user.home") "/.strava-config.json")]
    (ensure-config-dir)

    ;; Migrate Withings config
    (when (.exists (io/file old-withings-config))
      (try
        (let [old-config (-> old-withings-config slurp (json/parse-string true))]
          (spit config-file (json/generate-string old-config {:pretty true}))
          (println "✅ Migrated Withings config from" old-withings-config "to" config-file)
          (.delete (io/file old-withings-config))
          (println "🗑️  Removed old config file"))
        (catch Exception e
          (println "⚠️  Could not migrate Withings config:" (.getMessage e)))))

    ;; Migrate Strava config  
    (when (.exists (io/file old-strava-config))
      (try
        (let [old-config (-> old-strava-config slurp (json/parse-string true))
              new-strava-config (str config-dir "/strava.json")]
          (spit new-strava-config (json/generate-string old-config {:pretty true}))
          (println "✅ Migrated Strava config from" old-strava-config "to" new-strava-config)
          (.delete (io/file old-strava-config))
          (println "🗑️  Removed old config file"))
        (catch Exception e
          (println "⚠️  Could not migrate Strava config:" (.getMessage e)))))

    (println "🏁 Migration complete! All configs now in" config-dir)))