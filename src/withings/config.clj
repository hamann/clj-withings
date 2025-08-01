(ns withings.config
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]))

(def config-dir (str (System/getProperty "user.home") "/.config/clj-withings"))
(def config-file (str config-dir "/withings.json"))

(defn ensure-config-dir
  "Ensure the configuration directory exists"
  []
  (let [dir (java.io.File. config-dir)]
    (when-not (.exists dir)
      (.mkdirs dir))))

(defn read-secrets
  "Read secrets from local JSON file"
  []
  (let [secrets-file (str config-dir "/secrets.json")]
    (when (.exists (io/file secrets-file))
      (try
        (-> secrets-file
            slurp
            (json/parse-string true))
        (catch Exception e
          (println "Warning: Could not read secrets file:" (.getMessage e))
          nil)))))

(defn write-secrets
  "Write secrets to local JSON file"
  [secrets]
  (let [secrets-file (str config-dir "/secrets.json")]
    (try
      (ensure-config-dir)
      (spit secrets-file (json/generate-string secrets {:pretty true}))
      ;; Set restrictive permissions for basic security
      (.setReadable (io/file secrets-file) false false)
      (.setReadable (io/file secrets-file) true true)
      (.setWritable (io/file secrets-file) false false)
      (.setWritable (io/file secrets-file) true true)
      (println "Secrets saved to" secrets-file)
      (catch Exception e
        (println "Error saving secrets:" (.getMessage e))))))

(defn get-withings-secrets
  "Get Withings configuration from local secrets file"
  []
  (some-> (read-secrets) :withings))

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
  "Get credentials from command line args or local secrets file"
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