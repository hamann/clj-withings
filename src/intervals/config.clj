(ns intervals.config
  (:require [withings.config :as withings-config]))

(defn get-intervals-api-key
  "Get intervals.icu API key from secrets"
  []
  (when-let [secrets (withings-config/decrypt-secrets withings-config/secrets-file)]
    (get-in secrets [:intervals :api_key])))

(defn get-intervals-secrets
  "Get all intervals.icu secrets"
  []
  (when-let [secrets (withings-config/decrypt-secrets withings-config/secrets-file)]
    (:intervals secrets)))

(defn get-intervals-athlete-id
  "Get intervals.icu athlete ID from secrets"
  []
  (when-let [secrets (withings-config/decrypt-secrets withings-config/secrets-file)]
    (get-in secrets [:intervals :athlete_id])))