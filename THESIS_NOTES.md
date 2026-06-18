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

### [7e585a7] S3c — ατομική solver persistence (SolutionPersistenceService, BL-1)
Το BL-1 (Φ0/0.2): η εγγραφή της λύσης (`deleteAll` παλιών auto-αναθέσεων + loop
`save` νέων + `setStatus(SOLVED)`) έτρεχε σε **auto-commit** (non-transactional
solve, OSIV=false) → exception στη μέση = παλιές αναθέσεις σβησμένες + μερικές νέες
(μερική, μη-αναιρέσιμη εγγραφή). Σκέτη προσθήκη `@Transactional` στο παλιό
`saveSolution` ΔΕΝ έλυνε το πρόβλημα: ήταν **private + self-invoked** από το
`solve()` (ίδιο bean) → το Spring proxy παρακάμπτεται **σιωπηλά**, καμία tx.

Λύση (move-only): εξαγωγή της persistence αυτούσιας σε **ξεχωριστό injected
`@Transactional SolutionPersistenceService.persist`**. Ως entry-point σε άλλο bean
το proxy είναι ενεργό → όλα τα writes σε **ΕΝΑ** transaction (all-or-nothing,
default rollback σε RuntimeException). Το ~30s `solver.solve(...)` μένει στον
`SolverService`, **ΕΚΤΟΣ** tx (δεν κρατά DB connection όσο τρέχει)· το early
`SOLVING` status save παραμένει ξεχωριστό πριν το solve (το UI το θέλει committed
για την κατάσταση «ΕΠΕΞΕΡΓΑΣΙΑ»).

Διευκρίνιση «save score» (διατύπωση BL-1): το πεδίο `Timetable.solverScore` είναι
**dormant** (δεν γράφεται πουθενά)· το μόνο timetable write είναι το
`setStatus(SOLVED)`. Move-only ⇒ **καμία** προσθήκη `setSolverScore`.

Snapshot-on-write: ο stamper μπήκε ΚΑΙ στον 4ο (solver) write-path, ακριβώς πριν
το `save`, συμμετρικά με place/move/auto-schedule (S3b-2). Τρέχει μέσα στο atomic
tx με `findById`-φορτωμένα entities (scalar reads, χωρίς lazy join).

Verification: νέο `TransactionalRollbackTest` ×2 — (α) σκάσιμο στο 2ο `save` →
πλήρες rollback (η προϋπάρχουσα ανάθεση επανέρχεται, 0 μερικές νέες, status ≠
SOLVED· το ΑΚΡΙΒΕΣ σενάριο BL-1)· (β) επιτυχές persist → snapshot stamped. Καλούν
απευθείας το `persist` με χειρο-φτιαγμένη `CeidTimetable` (όχι ακριβό 30s solve),
όπως το A2 καλεί το `generateExamSlotsForTimetable`. Throwaway full-solve (πλήρες
FALL dataset, μετά διαγραφή) → 266/266 @ hard 0 (baseline αμετάβλητο). Full suite
94/94, χωρίς circular dependency στο boot.

Dead code που έμεινε σκόπιμα (εκτός scope, move-only): το ιδιωτικό `hardScoreName`
και το αχρησιμοποίητο local `hardViolations` — ποτέ δεν καλούνται/διαβάζονται·
καθαρισμός ξεχωριστά.

### [3d9a5c7] S3d — render-from-snapshot (assignmentToDto snapshot-first overlay)
Πρώτο **ΟΡΑΤΟ** αποτέλεσμα του invariant #1: τα προγράμματα render-άρονται από το
snapshot, ώστε να δείχνουν ό,τι αποτυπώθηκε κατά την τοποθέτηση — ανέπαφα σε
μετέπειτα αλλαγή/soft-delete των master. Το `assignmentToDto` χτίζει πρώτα τα live
sub-DTOs και ΜΕΤΑ επικαλύπτει snapshot-first τα 16 display πεδία.

Σχεδιαστικές επιλογές: (α) **overlay, όχι αντικατάσταση** — τα κοινά
`courseToDto/roomToDto/timeSlotToDto` μένουν live-only (τα χρησιμοποιεί και το
placement-options, που ΘΕΛΕΙ τρέχουσες τιμές)· η snapshot λογική μπαίνει σε 3
wrapper helpers ΜΟΝΟ στο assignment-render path. (β) Κανόνας ανά πεδίο: **snapshot
αν != null, αλλιώς live, αλλιώς null** — SNAPSHOT-first (όχι live-first): όταν
υπάρχει frozen τιμή ΚΑΙ το live άλλαξε, κερδίζει το snapshot. (γ) **ids/FKs μένουν
live** (assignment + course/room/timeSlot id)· μόνο τα 16 display πεδία περνούν
snapshot-first — αν χάθηκε το master FK, το id μπορεί να είναι null (αναμενόμενο).
(δ) **Null-safe:** αν το live entity είναι null (διαγραμμένο master), χτίζεται map
από το snapshot με `id=null` χωρίς NPE· επειδή τα FK είναι `nullable=false` +
soft-delete (η γραμμή μένει), στην πράξη το live ΣΠΑΝΙΑ είναι null — το branch είναι
αμυντικό, το ρεαλιστικό «deleted» = soft-delete (active=false, snapshot-first ισχύει
έτσι κι αλλιώς). (ε) Τα temporal snapshot πεδία (start/end/specificDate, αποθηκευμένα
ως `LocalTime`/`LocalDate`) γίνονται `toString()` ώστε να ταιριάζουν με το String
format του live `timeSlotToDto` (αλλιώς το ίδιο πεδίο θα εμφανιζόταν με δύο μορφές).
(στ) Το `snapshotTeachersText` περνά ΑΥΤΟΥΣΙΟ (ήδη normalized κατά το stamp, S3b-1) —
όχι re-normalize.

Το `assignmentToDto` έγινε **package-private** για στοχευμένο test (ίδιο pattern με
το `buildTeacherKeyMap` του S2), ώστε να ελέγχεται απευθείας το null-live branch που
το `nullable=false` FK δεν επιτρέπει να persist-αριστεί.

Tests (`SnapshotRenderTest`, μέσω `getAssignments`): (1) live course renamed →
δείχνεται το snapshot name (όχι το live)· (2) live room soft-deleted (active=false)
+ renamed → snapshot· (3) null live masters (direct call) → display από snapshot,
`id=null`, χωρίς NPE· (4) χωρίς αλλαγή → snapshot==live (μη-regression). Κανένα
υπάρχον test δεν κάνει assert το DTO output (νέα data: snapshot==live). Full suite
98/98.
