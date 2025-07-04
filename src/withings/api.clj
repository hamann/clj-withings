(ns withings.api
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [withings.config :as config]
            [withings.oauth :as oauth]))

(def withings-api-url "https://wbsapi.withings.net/measure")

(defn get-measurements
  "Get measurements from Withings API"
  [access-token & {:keys [meastype category limit lastupdate]
                   :or {meastype 1 category 1 limit 1}}]
  (let [params (cond-> {"action" "getmeas"
                       "meastype" meastype
                       "category" category
                       "limit" limit}
                 lastupdate (assoc "lastupdate" lastupdate))
        response (http/get withings-api-url
                          {:headers {"Authorization" (str "Bearer " access-token)}
                           :query-params params})]
    (if (= 200 (:status response))
      (let [data (json/parse-string (:body response) true)]
        (if (= 0 (:status data))
          (:body data)
          {:error (str "API error: " (:error data))}))
      {:error (str "HTTP error: " (:status response) " " (:body response))})))

(defn calculate-weight
  "Calculate actual weight from value and unit"
  [value unit]
  (* value (Math/pow 10 unit)))

(defn get-latest-weight
  "Get the latest weight measurement"
  [access-token]
  (let [result (get-measurements access-token :meastype 1 :category 1 :limit 1)]
    (if (:error result)
      result
      (let [measuregrps (:measuregrps result)]
        (if (empty? measuregrps)
          {:error "No weight measurements found"}
          (let [latest-group (first measuregrps)
                measures (:measures latest-group)
                weight-measure (first (filter #(= 1 (:type %)) measures))]
            (if weight-measure
              (let [weight-kg (calculate-weight (:value weight-measure) (:unit weight-measure))
                    date-instant (java.time.Instant/ofEpochSecond (:date latest-group))]
                {:weight (format "%.2f kg" weight-kg)
                 :date (str date-instant)})
              {:error "No weight measurement found in latest group"})))))))

(defn get-weight-with-auth
  "Get weight with automatic token management"
  []
  (let [config (config/read-config)]
    (if config
      (let [token (oauth/get-valid-token config)]
        (if token
          (get-latest-weight (:access-token token))
          {:error "No valid token available. Please run setup."}))
      {:error "No configuration found. Please run setup first."})))