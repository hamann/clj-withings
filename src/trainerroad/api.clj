(ns trainerroad.api
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string]
            [trainerroad.config :as config]))

(def trainerroad-api-url "https://api.trainerroad.com/api")

(defn authenticate-and-get-member-id
  "Authenticate with TrainerRoad and get member ID"
  [username password]
  (try
    (let [auth-str (str username ":" password)
          encoded-auth (.encodeToString (java.util.Base64/getEncoder) (.getBytes auth-str "UTF-8"))
          response (http/get (str trainerroad-api-url "/members")
                             {:headers {"Accept" "application/json"
                                        "Authorization" (str "Basic " encoded-auth)}
                              :throw false})]
      (if (= 200 (:status response))
        (let [body (json/parse-string (:body response) true)
              member-id (:MemberId body)]
          (config/save-credentials username password member-id)
          {:success true :member-id member-id})
        {:success false :error (str "Authentication failed: " (:status response))}))
    (catch Exception e
      {:success false :error (str "Authentication error: " (.getMessage e))})))

(defn get-authenticated-session
  "Get an authenticated session for API calls"
  [credentials]
  (let [{:keys [username password]} credentials
        auth-str (str username ":" password)
        encoded-auth (.encodeToString (java.util.Base64/getEncoder) (.getBytes auth-str "UTF-8"))]
    {"Accept" "application/json"
     "Authorization" (str "Basic " encoded-auth)
     "Content-Type" "application/json"}))

(defn update-athlete-weight
  "Update athlete weight in TrainerRoad profile"
  [username password weight-kg]
  (try
    (let [credentials {:username username :password password}
          headers (get-authenticated-session credentials)

          ;; First, ensure we have member-id
          auth-result (authenticate-and-get-member-id username password)]

      (if-not (:success auth-result)
        {:success false :error (str "Failed to authenticate: " (:error auth-result))}

        (try
          ;; Get current member profile
          (let [profile-response (http/get (str trainerroad-api-url "/members")
                                           {:headers headers :throw false})]
            (if (<= 200 (:status profile-response) 299)
              ;; Update the weight in the profile and send it back
              (let [current-profile (json/parse-string (:body profile-response) true)
                    updated-profile (assoc current-profile :WeightKG weight-kg)
                    update-response (http/put (str trainerroad-api-url "/members")
                                              {:headers headers
                                               :body (json/generate-string updated-profile)
                                               :throw false})]
                (if (<= 200 (:status update-response) 299)
                  {:success true
                   :message (str "Weight updated successfully to " weight-kg " kg")
                   :previous-weight (:WeightKG current-profile)
                   :new-weight weight-kg
                   :response-status (:status update-response)}
                  {:success false
                   :error (str "Failed to update weight: HTTP " (:status update-response))
                   :response-body (:body update-response)}))
              {:success false
               :error (str "Failed to fetch current profile: HTTP " (:status profile-response))
               :response-body (:body profile-response)}))
          (catch Exception e
            {:success false
             :error (str "Weight update error: " (.getMessage e))}))))

    (catch Exception e
      {:success false :error (str "Weight update error: " (.getMessage e))})))

(defn update-weight-with-auth
  "Update weight using stored credentials from SOPS"
  [weight-kg]
  (if-let [credentials (config/get-trainerroad-credentials)]
    (let [{:keys [username password]} credentials]
      (if (and username password)
        (update-athlete-weight username password weight-kg)
        {:success false :error "Missing username or password in TrainerRoad credentials"}))
    {:success false :error "No TrainerRoad credentials found. Please add trainerroad section to secrets.yaml"}))
