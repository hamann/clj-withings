(ns weight.parser-test
  (:require [clojure.test :refer [deftest is testing]]
            [weight.parser :as parser]))

;; Test data
(def valid-bb-weight-outputs
  ["{:weight 75.11 kg, :date 2025-07-01T10:13:53Z}"
   "{:weight 68.5 kg, :date 2025-12-25T15:30:00Z}"
   "{:weight 100.0 kg, :date 2025-01-01T00:00:00Z}"])

(def invalid-bb-weight-outputs
  ["{:weight invalid kg, :date 2025-07-01T10:13:53Z}"
   "{:not-weight 75.11 kg, :date 2025-07-01T10:13:53Z}"
   "{:weight 75.11, :date 2025-07-01T10:13:53Z}"
   "not a map at all"])

(def valid-manual-inputs
  [["75.1 kg" {:weight-kg 75.1 :date (parser/today-date-string)}]
   ["165.5 lbs" {:weight-kg (parser/lbs->kg 165.5) :date (parser/today-date-string)}]
   ["75.1 kg 2025-01-01" {:weight-kg 75.1 :date "2025-01-01"}]
   ["200 lbs 2025-12-31" {:weight-kg (parser/lbs->kg 200) :date "2025-12-31"}]])

(def invalid-manual-inputs
  ["75.1" ; missing unit
   "75.1 kg 2025-01-01 extra" ; too many parts
   "invalid kg" ; invalid weight number
   "75.1 invalid" ; invalid unit
   "75.1 kg invalid-date" ; invalid date format
   "" ; empty string
   "   " ; whitespace only
   ])

;; Test weight conversion
(deftest test-lbs-to-kg-conversion
  (testing "pounds to kilograms conversion"
    (is (= 45.3592 (parser/lbs->kg 100)))
    (is (= 22.6796 (parser/lbs->kg 50)))
    (is (= 68.0388 (parser/lbs->kg 150)))))

(deftest test-convert-to-kg
  (testing "weight conversion with different units"
    (is (= 75.0 (parser/convert-to-kg 75.0 "kg")))
    (is (= (parser/lbs->kg 165) (parser/convert-to-kg 165 "lbs")))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid unit"
                          (parser/convert-to-kg 75.0 "pounds")))))

;; Test date parsing
(deftest test-parse-iso-date-to-local-date
  (testing "ISO date parsing to local date"
    (is (= "2025-07-01" (parser/parse-iso-date-to-local-date "2025-07-01T10:13:53Z")))
    (is (= "2025-12-25" (parser/parse-iso-date-to-local-date "2025-12-25T15:30:00Z")))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Failed to parse date"
                          (parser/parse-iso-date-to-local-date "invalid-date")))))

(deftest test-today-date-string
  (testing "today's date string format"
    (let [today (parser/today-date-string)]
      (is (string? today))
      (is (re-matches #"\d{4}-\d{2}-\d{2}" today)))))

;; Test BB weight output parsing
(deftest test-parse-bb-weight-output
  (testing "valid bb weight outputs"
    (let [result (parser/parse-bb-weight-output "{:weight 75.11 kg, :date 2025-07-01T10:13:53Z}")]
      (is (not (:error result)))
      (is (= 75.11 (:weight-kg result)))
      (is (= "2025-07-01" (:date result))))

    (let [result (parser/parse-bb-weight-output "{:weight 68.5 kg, :date 2025-12-25T15:30:00Z}")]
      (is (not (:error result)))
      (is (= 68.5 (:weight-kg result)))
      (is (= "2025-12-25" (:date result)))))

  (testing "invalid bb weight outputs"
    (doseq [invalid-input invalid-bb-weight-outputs]
      (let [result (parser/parse-bb-weight-output invalid-input)]
        (is (:error result) (str "Should have error for input: " invalid-input))))))

;; Test manual weight input parsing
(deftest test-parse-manual-weight-input
  (testing "valid manual inputs"
    (doseq [[input expected] valid-manual-inputs]
      (let [result (parser/parse-manual-weight-input input)]
        (is (not (:error result)) (str "Should not have error for input: " input))
        (is (= (:weight-kg expected) (:weight-kg result)) (str "Weight mismatch for input: " input))
        (is (= (:date expected) (:date result)) (str "Date mismatch for input: " input)))))

  (testing "invalid manual inputs"
    (doseq [invalid-input invalid-manual-inputs]
      (let [result (parser/parse-manual-weight-input invalid-input)]
        (is (:error result) (str "Should have error for input: " invalid-input))))))

;; Test main parsing dispatcher
;; Test main parsing dispatcher
(deftest test-parse-weight-input
  (testing "bb weight format detection and parsing"
    (let [bb-input "{:weight 75.11 kg, :date 2025-07-01T10:13:53Z}"
          result (parser/parse-weight-input bb-input)]
      (is (not (:error result)))
      (is (= 75.11 (:weight-kg result)))
      (is (= "2025-07-01" (:date result)))))

  (testing "bb weight format with token refresh messages"
    (let [mixed-input "Token expired, refreshing...\nConfiguration saved to /Users/hamann/.withings-config.json\n{:weight 72.5 kg, :date 2025-07-04T08:15:30Z}"
          result (parser/parse-weight-input mixed-input)]
      (is (not (:error result)))
      (is (= 72.5 (:weight-kg result)))
      (is (= "2025-07-04" (:date result)))))

  (testing "manual input format detection and parsing"
    (let [manual-input "75.1 kg"
          result (parser/parse-weight-input manual-input)]
      (is (not (:error result)))
      (is (= 75.1 (:weight-kg result)))
      (is (= (parser/today-date-string) (:date result)))))

  (testing "empty input handling"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"No input provided"
                          (parser/parse-weight-input "")))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"No input provided"
                          (parser/parse-weight-input "   ")))))

;; Test validation
(deftest test-valid-parsed-weight
  (testing "valid parsed weight data"
    (is (parser/valid-parsed-weight? {:weight-kg 75.0}))
    (is (parser/valid-parsed-weight? {:weight-kg 75.0 :date "2025-01-01"})))

  (testing "invalid parsed weight data"
    (is (not (parser/valid-parsed-weight? {:error "some error"})))
    (is (not (parser/valid-parsed-weight? {:weight-kg -75.0}))) ; negative weight
    (is (not (parser/valid-parsed-weight? {:weight-kg "75.0"}))) ; string weight
    (is (not (parser/valid-parsed-weight? {:weight-kg 75.0 :date "invalid-date"})))))

;; Test service-specific processors
(deftest test-for-intervals
  (testing "valid data for intervals"
    (let [parsed-data {:weight-kg 75.0 :date "2025-01-01"}
          result (parser/for-intervals parsed-data)]
      (is (not (:error result)))
      (is (= 75.0 (:weight-kg result)))
      (is (= "2025-01-01" (:date result)))))

  (testing "missing date for intervals"
    (let [parsed-data {:weight-kg 75.0}
          result (parser/for-intervals parsed-data)]
      (is (:error result))
      (is (re-find #"Date is required" (:error result)))))

  (testing "error passthrough for intervals"
    (let [parsed-data {:error "parsing failed"}
          result (parser/for-intervals parsed-data)]
      (is (= "parsing failed" (:error result))))))

(deftest test-for-strava
  (testing "valid data for strava"
    (let [parsed-data {:weight-kg 75.0 :date "2025-01-01"}
          result (parser/for-strava parsed-data)]
      (is (not (:error result)))
      (is (= 75.0 (:weight-kg result)))
      (is (nil? (:date result))))) ; date should not be included

  (testing "data without date for strava"
    (let [parsed-data {:weight-kg 75.0}
          result (parser/for-strava parsed-data)]
      (is (not (:error result)))
      (is (= 75.0 (:weight-kg result)))))

  (testing "error passthrough for strava"
    (let [parsed-data {:error "parsing failed"}
          result (parser/for-strava parsed-data)]
      (is (= "parsing failed" (:error result))))))

;; Test usage message generators
(deftest test-usage-messages
  (testing "intervals usage message"
    (let [usage (parser/usage-message-intervals)]
      (is (vector? usage))
      (is (every? string? usage))
      (is (some #(re-find #"intervals" %) usage))))

  (testing "strava usage message"
    (let [usage (parser/usage-message-strava)]
      (is (vector? usage))
      (is (every? string? usage))
      (is (some #(re-find #"strava" %) usage)))))

;; Integration tests with realistic scenarios
(deftest test-realistic-scenarios
  (testing "bb weight piped to intervals"
    (let [bb-output "{:weight 72.5 kg, :date 2025-07-04T08:15:30Z}"
          parsed (parser/parse-weight-input bb-output)
          intervals-data (parser/for-intervals parsed)]
      (is (not (:error intervals-data)))
      (is (= 72.5 (:weight-kg intervals-data)))
      (is (= "2025-07-04" (:date intervals-data)))))

  (testing "bb weight piped to strava"
    (let [bb-output "{:weight 72.5 kg, :date 2025-07-04T08:15:30Z}"
          parsed (parser/parse-weight-input bb-output)
          strava-data (parser/for-strava parsed)]
      (is (not (:error strava-data)))
      (is (= 72.5 (:weight-kg strava-data)))
      (is (nil? (:date strava-data)))))

  (testing "manual kg input to intervals"
    (let [manual-input "75.1 kg"
          parsed (parser/parse-weight-input manual-input)
          intervals-data (parser/for-intervals parsed)]
      (is (not (:error intervals-data)))
      (is (= 75.1 (:weight-kg intervals-data)))
      (is (= (parser/today-date-string) (:date intervals-data)))))

  (testing "manual lbs input to strava"
    (let [manual-input "165.5 lbs"
          parsed (parser/parse-weight-input manual-input)
          strava-data (parser/for-strava parsed)]
      (is (not (:error strava-data)))
      (is (= (parser/lbs->kg 165.5) (:weight-kg strava-data)))
      (is (nil? (:date strava-data)))))

  (testing "manual input with date to intervals"
    (let [manual-input "75.1 kg 2025-01-01"
          parsed (parser/parse-weight-input manual-input)
          intervals-data (parser/for-intervals parsed)]
      (is (not (:error intervals-data)))
      (is (= 75.1 (:weight-kg intervals-data)))
      (is (= "2025-01-01" (:date intervals-data))))))

;; Edge case tests
(deftest test-edge-cases
  (testing "very small and large weights"
    (let [small-weight "0.1 kg"
          large-weight "500 kg"]
      (is (not (:error (parser/parse-weight-input small-weight))))
      (is (not (:error (parser/parse-weight-input large-weight))))))

  (testing "precision handling"
    (let [precise-weight "75.123456 kg"
          result (parser/parse-weight-input precise-weight)]
      (is (not (:error result)))
      (is (= 75.123456 (:weight-kg result)))))

  (testing "whitespace handling"
    (let [spaced-input "  75.1   kg  "
          result (parser/parse-weight-input spaced-input)]
      (is (not (:error result)))
      (is (= 75.1 (:weight-kg result))))))
