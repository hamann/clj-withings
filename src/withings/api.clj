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
    (if-not (= 200 (:status response))
      {:error (str "HTTP error: " (:status response) " " (:body response))}
      (let [data (json/parse-string (:body response) true)]
        (if (= 0 (:status data))
          (:body data)
          {:error (str "API error: " (:error data))})))))

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
                weight-measure (->> (:measures latest-group)
                                    (filter #(= 1 (:type %)))
                                    first)]
            (if-not weight-measure
              {:error "No weight measurement found in latest group"}
              {:weight (format "%.2f kg" (calculate-weight (:value weight-measure) (:unit weight-measure)))
               :date (str (java.time.Instant/ofEpochSecond (:date latest-group)))})))))))

(defn get-weight-with-auth
  "Get weight with automatic token management
   Used in bb.edn: weight task"
  []
  (let [config (config/read-config)]
    (if-not config
      {:error "No configuration found. Please run setup first."}
      (let [token (oauth/get-valid-token config)]
        (if-not token
          {:error "No valid token available. Please run setup."}
          (get-latest-weight (:access-token token)))))))