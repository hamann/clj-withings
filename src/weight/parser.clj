(ns weight.parser
  "Namespace for parsing weight input from various sources"
  (:require [clojure.string :as str]))

;; Constants
(def lbs-to-kg-ratio 0.453592)
(def bb-weight-pattern #":weight ([0-9.]+) kg")
(def bb-date-pattern #":date ([0-9T:-]+Z)")
(def manual-weight-parts-pattern #"\s+")

;; Weight conversion
(defn lbs->kg
  "Convert pounds to kilograms"
  [lbs]
  (* lbs lbs-to-kg-ratio))

(defn convert-to-kg
  "Convert weight to kg based on unit"
  [weight unit]
  (case unit
    "kg" weight
    "lbs" (lbs->kg weight)
    (throw (ex-info "Invalid unit" {:unit unit :valid-units ["kg" "lbs"]}))))

;; Date parsing
(defn parse-iso-date-to-local-date
  "Parse ISO date string to local date format YYYY-MM-DD"
  [iso-date-str]
  (try
    (let [instant (java.time.Instant/parse iso-date-str)
          local-date (.toLocalDate (.atZone instant (java.time.ZoneId/systemDefault)))]
      (.format local-date (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd")))
    (catch Exception e
      (throw (ex-info "Failed to parse date" {:date iso-date-str :error (.getMessage e)})))))

(defn today-date-string
  "Get today's date as YYYY-MM-DD string"
  []
  (.format (java.time.LocalDate/now)
           (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd")))

(defn valid-date-string?
  "Check if date string is in valid YYYY-MM-DD format"
  [date-str]
  (try
    (java.time.LocalDate/parse date-str)
    true
    (catch Exception _
      false)))

;; Core parsing functions
(defn parse-bb-weight-output
  "Parse weight data from 'bb weight' command output.
   Expected format: {:weight 75.11 kg, :date 2025-07-01T10:13:53Z}
   
   Returns: {:weight-kg Double, :date String} or {:error String}"
  [input]
  (try
    (let [weight-match (re-find bb-weight-pattern input)
          date-match (re-find bb-date-pattern input)]
      (if weight-match
        (let [weight-kg (Double/parseDouble (second weight-match))
              date-str (when date-match
                         (parse-iso-date-to-local-date (second date-match)))]
          {:weight-kg weight-kg
           :date date-str})
        {:error "Could not parse weight from bb output format"}))
    (catch Exception e
      {:error (str "Failed to parse bb weight output: " (.getMessage e))})))

(defn parse-manual-weight-input
  "Parse manual weight input in format 'WEIGHT UNIT [DATE]'.
   Examples: '75.1 kg', '165.5 lbs', '75.1 kg 2025-01-01'
   
   Returns: {:weight-kg Double, :date String} or {:error String}"
  [input]
  (try
    (let [parts (str/split (str/trim input) manual-weight-parts-pattern)]
      (cond
        (< (count parts) 2)
        {:error "Input must contain at least weight and unit (e.g., '75.1 kg')"}

        (> (count parts) 3)
        {:error "Too many parts in input. Expected format: 'WEIGHT UNIT [DATE]'"}

        :else
        (let [weight-str (first parts)
              unit (second parts)
              date-str (if (= (count parts) 3)
                         (nth parts 2)
                         (today-date-string))]
          (when-not (#{"kg" "lbs"} unit)
            (throw (ex-info "Invalid unit" {:unit unit :valid-units ["kg" "lbs"]})))

          (when (and (= (count parts) 3) (not (valid-date-string? date-str)))
            (throw (ex-info "Invalid date format" {:date date-str :expected "YYYY-MM-DD"})))

          (let [weight-val (Double/parseDouble weight-str)
                weight-kg (convert-to-kg weight-val unit)]
            {:weight-kg weight-kg
             :date date-str}))))
    (catch NumberFormatException e
      {:error (str "Invalid weight value: " (first (str/split input #"\s+")))})
    (catch Exception e
      {:error (.getMessage e)})))

;; Main parsing dispatcher
(defn parse-weight-input
  "Parse weight input from stdin, handling both bb weight output and manual input.
   Used in bb.edn: push-to-intervals and push-to-strava tasks
   
   Returns: {:weight-kg Double, :date String} or {:error String}"
  [input]
  (when (str/blank? input)
    (throw (ex-info "No input provided" {})))

  (let [trimmed-input (str/trim input)]
    ;; Check if any line contains bb weight format
    (if-let [weight-line (some #(when (str/starts-with? % "{:weight") %)
                               (str/split-lines trimmed-input))]
      (parse-bb-weight-output weight-line)
      (parse-manual-weight-input trimmed-input))))

;; Validation functions
(defn valid-parsed-weight?
  "Check if parsed weight data is valid"
  [parsed-data]
  (and (not (:error parsed-data))
       (number? (:weight-kg parsed-data))
       (pos? (:weight-kg parsed-data))
       (or (nil? (:date parsed-data))
           (valid-date-string? (:date parsed-data)))))

;; Result processors for different services
(defn for-intervals
  "Process parsed weight data for intervals.icu (requires date)
   Used in bb.edn: push-to-intervals task"
  [parsed-data]
  (if (:error parsed-data)
    parsed-data
    (if (:date parsed-data)
      {:weight-kg (:weight-kg parsed-data)
       :date (:date parsed-data)}
      {:error "Date is required for intervals.icu but was not provided"})))

(defn for-strava
  "Process parsed weight data for Strava (weight only)
   Used in bb.edn: push-to-strava task"
  [parsed-data]
  (if (:error parsed-data)
    parsed-data
    {:weight-kg (:weight-kg parsed-data)}))

(defn for-trainerroad
  "Process parsed weight data for TrainerRoad (weight only)
   Used in bb.edn: push-to-trainerroad task"
  [parsed-data]
  (if (:error parsed-data)
    parsed-data
    {:weight-kg (:weight-kg parsed-data)}))

;; Error message generators
(defn usage-message-intervals
  "Generate usage message for intervals push command
   Used in bb.edn: push-to-intervals task (error handling)"
  []
  ["Usage: echo 'WEIGHT UNIT [DATE]' | bb push-to-intervals"
   "       bb weight | bb push-to-intervals"
   "Example: echo '75.1 kg' | bb push-to-intervals"
   "Example: echo '165.5 lbs 2025-01-01' | bb push-to-intervals"
   "Example: bb weight | bb push-to-intervals"])

(defn usage-message-strava
  "Generate usage message for strava push command
   Used in bb.edn: push-to-strava task (error handling)"
  []
  ["Usage: echo 'WEIGHT UNIT' | bb push-to-strava"
   "       bb weight | bb push-to-strava"
   "Example: echo '75.1 kg' | bb push-to-strava"
   "Example: echo '165.5 lbs' | bb push-to-strava"
   "Example: bb weight | bb push-to-strava"])

(defn usage-message-trainerroad
  "Generate usage message for trainerroad push command
   Used in bb.edn: push-to-trainerroad task (error handling)"
  []
  ["Usage: echo 'WEIGHT UNIT' | bb push-to-trainerroad"
   "       bb weight | bb push-to-trainerroad"
   "Example: echo '75.1 kg' | bb push-to-trainerroad"
   "Example: echo '165.5 lbs' | bb push-to-trainerroad"
   "Example: bb weight | bb push-to-trainerroad"])
