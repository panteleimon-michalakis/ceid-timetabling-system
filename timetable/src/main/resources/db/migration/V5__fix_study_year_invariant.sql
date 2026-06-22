-- Διόρθωση study_year ώστε να συμφωνεί με το semester (έτος = ceil(εξάμηνο/2)).
-- Στόχος: CEID_22Y101 (semester=3, study_year=1 → 2). Idempotent: σε συνεπή βάση επηρεάζει 0 γραμμές.
UPDATE courses
SET study_year = CEIL(semester / 2.0)
WHERE study_year <> CEIL(semester / 2.0);
