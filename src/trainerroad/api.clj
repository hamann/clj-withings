(ns trainerroad.api
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string]
            [trainerroad.config :as config]))

(def trainerroad-api-url "https://api.trainerroad.com/api")

(defn authenticate-and-get-member-id [username password]
  "Authenticate with TrainerRoad and get member ID"
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

(defn get-authenticated-session [credentials]
  "Get an authenticated session for API calls"
  (let [{:keys [username password]} credentials
        auth-str (str username ":" password)
        encoded-auth (.encodeToString (java.util.Base64/getEncoder) (.getBytes auth-str "UTF-8"))]
    {"Accept" "application/json"
     "Authorization" (str "Basic " encoded-auth)
     "Content-Type" "application/json"}))

(defn update-athlete-weight [username password weight-kg]
  "Update athlete weight in TrainerRoad profile"
  (try
    (let [credentials {:username username :password password}
          headers (get-authenticated-session credentials)

          ;; First, ensure we have member-id
          auth-result (authenticate-and-get-member-id username password)]

      (if-not (:success auth-result)
        {:success false :error (str "Failed to authenticate: " (:error auth-result))}

        (let [member-id (:member-id auth-result)

              ;; Try common weight update endpoints
              endpoints-to-try [{:url (str trainerroad-api-url "/members/" member-id "/profile")
                                 :method :put
                                 :data {:weight weight-kg}}
                                {:url (str trainerroad-api-url "/members/" member-id "/weight")
                                 :method :post
                                 :data {:weight weight-kg}}
                                {:url (str trainerroad-api-url "/profile")
                                 :method :put
                                 :data {:weight weight-kg}}
                                {:url (str trainerroad-api-url "/athletes/" member-id "/measurements")
                                 :method :post
                                 :data {:weight weight-kg}}
                                {:url (str trainerroad-api-url "/members/" member-id)
                                 :method :put
                                 :data {:weight weight-kg}}]

              ;; Function to try a single endpoint
              try-endpoint (fn [{:keys [url method data]}]
                             (try
                               (let [response (case method
                                                :put (http/put url {:headers headers
                                                                    :body (json/generate-string data)
                                                                    :throw false})
                                                :post (http/post url {:headers headers
                                                                      :body (json/generate-string data)
                                                                      :throw false}))]
                                 (if (<= 200 (:status response) 299)
                                   {:success true
                                    :message (str "Weight updated successfully using " method " " url)
                                    :response-status (:status response)}
                                   {:success false
                                    :error (str method " " url " returned " (:status response))}))
                               (catch Exception e
                                 {:success false
                                  :error (str method " " url " threw " (.getMessage e))})))

              ;; Try endpoints until one succeeds
              results (map try-endpoint endpoints-to-try)
              success-result (first (filter :success results))]

          (if success-result
            success-result
            {:success false
             :error (str "No working endpoint found. Errors: "
                         (clojure.string/join "; " (map :error results)))}))))

    (catch Exception e
      {:success false :error (str "Weight update error: " (.getMessage e))})))

(defn update-weight-with-auth [weight-kg]
  "Update weight using stored credentials from SOPS"
  (if-let [credentials (config/get-trainerroad-credentials)]
    (let [{:keys [username password]} credentials]
      (if (and username password)
        (update-athlete-weight username password weight-kg)
        {:success false :error "Missing username or password in TrainerRoad credentials"}))
    {:success false :error "No TrainerRoad credentials found. Please add trainerroad section to secrets.yaml"}))
