(ns trainerroad.config
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [withings.config :as withings-config]))

(def config-dir withings-config/config-dir)

(def trainerroad-config-file
  (str config-dir "/trainerroad-config.json"))

(defn ensure-config-dir []
  (.mkdirs (io/file config-dir)))

(defn get-trainerroad-secrets
  "Get TrainerRoad configuration from encrypted secrets"
  []
  (some-> (withings-config/decrypt-secrets withings-config/secrets-file)
          :trainerroad))

(defn get-trainerroad-username
  "Get TrainerRoad username from secrets"
  []
  (some-> (get-trainerroad-secrets)
          :username))

(defn get-trainerroad-password
  "Get TrainerRoad password from secrets"
  []
  (some-> (get-trainerroad-secrets)
          :password))

(defn read-trainerroad-config []
  (when (.exists (io/file trainerroad-config-file))
    (json/parse-string (slurp trainerroad-config-file) true)))

(defn write-trainerroad-config [config]
  (ensure-config-dir)
  (spit trainerroad-config-file (json/generate-string config {:pretty true})))

(defn save-credentials [username password member-id]
  (let [config {:username username
                :password password
                :member-id member-id
                :saved-at (System/currentTimeMillis)}]
    (write-trainerroad-config config)))

(defn get-trainerroad-credentials
  "Get TrainerRoad credentials from SOPS secrets or saved config"
  []
  (let [config (read-trainerroad-config)
        username (get-trainerroad-username)
        password (get-trainerroad-password)]
    (if config
      config
      (when (and username password)
        {:username username
         :password password}))))
