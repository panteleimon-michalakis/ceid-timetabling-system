-- =====================================================================
-- V3__add_assignment_snapshot.sql  —  snapshot-on-write (S3a)
--
-- Προσθέτει denormalized "snapshot" στήλες στις αναθέσεις. Κατά την
-- εγγραφή/τοποθέτηση (επόμενο task S3b) αντιγράφονται τα display πεδία των
-- live Course/Room/TimeSlot πάνω στη γραμμή. Τα προγράμματα render-άρονται
-- από αυτά → μένουν ανέπαφα σε μετέπειτα αλλαγές/soft-delete των master
-- δεδομένων (Architecture invariant #1).
--
-- Πλήρες snapshot ώστε να μη γίνονται stale: κωδικός/όνομα/εξάμηνο/έτος/τύπος
-- μαθήματος, διδάσκοντες, κωδικός/όνομα/χωρητικότητα/τύπος αίθουσας, ημέρα/
-- ώρες/τύπος slot + ημερομηνία & ετικέτα περιόδου εξεταστικής (αλλιώς τα exam
-- timetables χάνουν date/period μετά από αλλαγή/διαγραφή live timeslot).
--
-- Additive & μη-καταστροφικό: ΟΛΕΣ nullable, DDL-only (ΚΑΝΕΝΑ data UPDATE).
-- Το backfill των υπαρχόντων γίνεται ξεχωριστά μέσω Java SnapshotBackfillRunner
-- (single source = το ίδιο stampSnapshot), όχι εδώ. Τα VARCHAR ισούνται με το
-- length της πηγής (κανένα truncation)· το teachers_text → TEXT αφού είναι
-- Java-derived (normalizeTeachersTextForDto).
-- =====================================================================

ALTER TABLE public.timetable_assignments
    -- Course
    ADD COLUMN snapshot_course_code       varchar(30),
    ADD COLUMN snapshot_course_name       varchar(200),
    ADD COLUMN snapshot_semester          integer,
    ADD COLUMN snapshot_study_year        integer,
    ADD COLUMN snapshot_course_type       varchar(30),
    ADD COLUMN snapshot_teachers_text     text,
    -- Room
    ADD COLUMN snapshot_room_code         varchar(20),
    ADD COLUMN snapshot_room_name         varchar(100),
    ADD COLUMN snapshot_room_capacity     integer,
    ADD COLUMN snapshot_room_type         varchar(30),
    -- TimeSlot
    ADD COLUMN snapshot_day_of_week       varchar(15),
    ADD COLUMN snapshot_start_time        time,
    ADD COLUMN snapshot_end_time          time,
    ADD COLUMN snapshot_slot_type         varchar(20),
    ADD COLUMN snapshot_specific_date     date,
    ADD COLUMN snapshot_exam_period_label varchar(50);
