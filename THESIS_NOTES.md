# THESIS_NOTES — ceid-timetabling-system

Σημειώσεις σχεδιασμού & υλοποίησης για το γραπτό. Κάθε task προσαρτά εδώ το 📝
note του. ΔΕΝ είναι instructions — δεν φορτώνεται στο per-session context.

## Φάση 0 — Guardrails

### [cb1a0bc] Flyway baseline
Αποκλειστικός διαχειριστής σχήματος. Baseline σε υπάρχον σύστημα: τρέχον σχήμα ως
V1, baseline-on-migrate (υπάρχουσες βάσεις = marker, κενές/CI = χτίσιμο από V1).
ddl-auto update→validate → μη-συγχρονισμένη αλλαγή entity↔σχήματος = ρητή αποτυχία
στο boot αντί σιωπηλού auto-DDL.

### [c5deef1] CPSolver → Timefold (terminology)
Το UI ανέφερε λάθος engine (CPSolver/UniTime, ποτέ ενσωματωμένο)· ο solver είναι
Timefold.

### [d736b2b/2c3e6c9] Safe-validation guardrails
Multi-record writes → ατομικά (all-or-nothing). Ανάδειξη partial-write bug στο
RoomController.delete· αντιμετώπιση pre-check→409 + transaction χωρίς κατάπνιξη
exception (αποφυγή Spring rollback-only / UnexpectedRollbackException).

## Φάση 1 — Μοντέλο

### [e4a47eb] S1 — soft-delete
Master (rooms/teachers) → active flag. «Σε χρήση» → deactivate (row+FK μένουν)·
αχρησιμοποίητο → hard-delete. Active-aware solver reads. Υλοποιεί «master =
soft-delete, ιστορική αναφορά διατηρείται» (αρχ. #1) — θεμέλιο του snapshot-on-write.

### [beb523c] S2 — CourseTeacher M2M authoritative
Two-worlds + silent-catch: το M2M loop στο buildTeacherKeyMap ήταν dead code
(open-in-view=false + non-transactional solve + LAZY ct.getTeacher() →
LazyInitializationException σιωπηλά swallowed → teacherKeys de-facto μόνο από
teachers_text). Αιτιολόγηση task E. Διόρθωση: join-fetch findAllWithTeacherAndCourse,
M2M authoritative + active filter, teachers_text deprecated fallback.

### [d4b773a] S3a — snapshot-on-write: σχήμα + entity (V3)
Θεμελίωση του invariant #1 (render-from-snapshot): 16 denormalized στήλες στο
timetable_assignments (course code/name/εξάμηνο/έτος/τύπος, teachers_text, room
code/name/χωρητικότητα/τύπος, slot ημέρα/ώρες/τύπος + specific_date & exam_period_label).
Πλήρες snapshot σκόπιμα: χωρίς room capacity/type & course semester/year/type τα
attributes γίνονται stale, και χωρίς date/period τα exam timetables σπάνε μετά από
αλλαγή/διαγραφή live timeslot. Σχεδιαστικές επιλογές: (α) DDL-only/όλες nullable —
κανένα data UPDATE στο migration· το backfill υπαρχόντων αναβάλλεται σε Java
SnapshotBackfillRunner ώστε backfilled & newly-stamped να περνούν από το ΙΔΙΟ
stampSnapshot (single source — η SQL δεν αναπαράγει το Java normalizeTeachersTextForDto).
(β) VARCHAR = length πηγής (μηδέν truncation), teachers_text → TEXT αφού είναι
Java-derived. (γ) enum-name πεδία ως String, ώστε το snapshot να μένει ανεξάρτητο
από μελλοντικές αλλαγές enums. Επαλήθευση: ddl-auto=validate → ο Hibernate ελέγχει
entity↔σχήμα στο boot (αναντιστοιχία = ρητή αποτυχία)· 77/77 πράσινα, Flyway v3 καθαρό.

### [65acc5d] S3b-1 — extract TeacherDisplayText (προετοιμασία single source)
Η αλυσίδα ~15 private μεθόδων normalize διδασκόντων (ελεύθερο teachers_text →
καθαρισμός / dedup-ανά-key / ταξινόμηση accent-insensitive / role-attachment) εξήχθη
αυτούσια από τον TimetableController σε static `util/TeacherDisplayText`, ώστε το
stamp (S3b-2) και το assignmentToDto να μοιράζονται ΕΝΑ implementation — αλλιώς
stamped snapshot vs live render αποκλίνουν (drift). Gate πριν την εξαγωγή: κάθε
μετακινούμενη μέθοδος stateless· οι 2 stateful (buildCourseTeacherKeyMap,
findCommonTeacherNamesSmart) έμειναν στον controller και καλούν το util (static
import). Εμβέλεια single-source = ΜΟΝΟ το snapshot/timetable path· ο CourseController
κρατά προσωρινά δικό του αντίγραφο της ίδιας αλυσίδας μέχρι το BL-7. Behavior-preserving:
77/77 υπάρχοντα πράσινα + characterization test (8 edge cases: ΕΔΙΠ/ΑΑΔΕ placeholders,
co-taught ordering, role-attach, empty/null, ελληνικά με τόνους). Το test εξέθεσε
ΠΡΟΫΠΑΡΧΟΝ quirk: «Α. Ηλία (ΕΔΙΠ)» → «Α. Ηλίαςςς (ΕΔΙΠ)» (overlapping data-fix
replaces × διπλό πέρασμα cleanTeacherDisplayName) — κλειδώθηκε ως-έχει, διόρθωση
ξεχωριστά. Δείχνει τη χρησιμότητα του characterization testing σε behavior-preserving
extract.

### [842151a] S3b-2 — snapshot stamping (AssignmentSnapshotStamper)
Νέο `@Component AssignmentSnapshotStamper.stamp(a)`: γράφει ΚΑΙ τα 16 denormalized
πεδία (Course 6 / Room 4 / Time 6) από τα ΤΡΕΧΟΝΤΑ live entities, καλούμενο ακριβώς
πριν από κάθε save στα 3 controller write-paths — manual placement (298), move (1522,
με re-stamp room/slot στη νέα θέση), auto-schedule (1964). Pure in-memory (μηδέν DB
πρόσβαση)· enum πεδία ως `.name()` String· null entity → αφήνει την ομάδα ως έχει.
Το `teachers_text` περνά μέσω `TeacherDisplayText.normalizeTeachersTextForDto` (single
source με το assignmentToDto — βλ. S3b-1).

ΑΠΟΦΑΣΗ — πηγή teacher names = **Option 1** (normalized `course.teachers_text`), ΟΧΙ
το active M2M που πρότεινε αρχικά το `S3_PROMPT.md`. Συνειδητή, εγκεκριμένη απόκλιση
από το spec: (1) **display fidelity** — το snapshot == ό,τι έδειχνε το UI κατά την
τοποθέτηση· (2) **μηδέν join-per-stamp** μέσα στο ~266-assignment solve loop (το stamp
μπαίνει στο atomic tx του BL-1/S3c)· (3) **καμία cross-screen ordering inconsistency**
(το M2M role-order διέφερε από το αλφαβητικό normalize του UI). Καταγράφεται για καθαρό
spec↔implementation trail.

Tests: unit (16 πεδία + exam date/period + move re-stamp + null-safety) + wiring
`@SpringBootTest` ανά path (place/move/auto-schedule). Το auto-schedule path
απομονώθηκε με pre-fill (γέμισμα ωρών των real courses → ο greedy τα προσπερνά,
τοποθετεί μόνο το test course) → ~3s αντί full-dataset build (663s). 92/92 πράσινα.
