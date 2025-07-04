(ns strava.api
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [strava.config :as config]))

(def strava-api-url "https://www.strava.com/api/v3")

(defn update-athlete-weight
  "Update athlete weight in Strava"
  [access-token weight-kg]
  (let [url (str strava-api-url "/athlete")
        response (http/put url
                           {:headers {"Authorization" (str "Bearer " access-token)
                                      "Content-Type" "application/x-www-form-urlencoded"}
                            :form-params {:weight weight-kg}})]
    (if (= 200 (:status response))
      {:message "Weight updated successfully in Strava"}
      {:error (str "HTTP error: " (:status response) " " (:body response))})))

(defn update-weight-with-auth
  "Update weight using configured OAuth token"
  [weight-kg]
  (if-let [token (config/get-valid-strava-token)]
    (update-athlete-weight (:access_token token) weight-kg)
    {:error "No valid Strava access token available. Run 'bb setup-strava' first."}))
