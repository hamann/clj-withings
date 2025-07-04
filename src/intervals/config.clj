(ns intervals.config
  (:require [withings.config :as withings-config]))

(defn get-intervals-secrets
  "Get all intervals.icu secrets"
  []
  (some-> (withings-config/decrypt-secrets withings-config/secrets-file)
          :intervals))

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
