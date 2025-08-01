(ns intervals.config
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]))

(defn get-intervals-secrets
  "Get all intervals.icu secrets from local JSON file"
  []
  (let [config-dir (str (System/getProperty "user.home") "/.config/clj-withings")
        secrets-file (str config-dir "/secrets.json")]
    (when (.exists (io/file secrets-file))
      (try
        (let [secrets (-> secrets-file slurp (json/parse-string true))]
          (:intervals secrets))
        (catch Exception e
          (println "Warning: Could not read secrets file:" (.getMessage e))
          nil)))))

(defn get-intervals-api-key
  "Get intervals.icu API key from secrets"
  []
  (some-> (get-intervals-secrets)
          :api_key))

(defn get-intervals-athlete-id
  "Get intervals.icu athlete ID from secrets"
  []
  (some-> (get-intervals-secrets)
          :athlete_id))
