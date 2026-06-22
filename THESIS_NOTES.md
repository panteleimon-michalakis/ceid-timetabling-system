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

### [5b7945e] S3e — SnapshotBackfillRunner (backfill υπαρχόντων) — S3 ΟΛΟΚΛΗΡΩΜΕΝΟ
Ο `SnapshotBackfillRunner` (`ApplicationRunner`, `@Order(100)` μετά τους seeders)
στο startup γεμίζει το snapshot σε αναθέσεις γραμμένες ΠΡΙΝ το snapshot-on-write,
μέσω του **ΙΔΙΟΥ** `AssignmentSnapshotStamper` — υλοποιεί την απόφαση του S3a
(«backfill σε Java, όχι στο DDL migration»): single source ⇒ backfilled ==
newly-stamped == render, χωρίς η SQL να αναπαράγει το `normalizeTeachersTextForDto`.

Σχεδιαστικές επιλογές: (α) **null-guard στο query**, όχι `findAll`+filter:
`findBySnapshotCourseCodeIsNull` (το `snapshot_course_code` είναι το sentinel —
null ⇔ η course-ομάδα ποτέ stamped). **ΚΡΙΣΙΜΟ:** rows με υπάρχον snapshot ΔΕΝ
επιλέγονται → δεν ξανα-stamp-άρονται· διαφορετικά το frozen snapshot θα γινόταν
refresh στις ΤΡΕΧΟΥΣΕΣ live τιμές, σπάζοντας το freeze (invariant #1). (β)
**Idempotent / re-runnable**: 2η εκτέλεση βρίσκει 0 null → no-op. Σε καθαρή/CI βάση
(καμία ανάθεση) = no-op ⇒ καμία εγγραφή, καμία επίδραση στο V1→V2→V3 Flyway/
ddl-validate του startup. (γ) Non-transactional `run()`: το EAGER course/room/timeSlot
φορτώνεται με το query (detached αλλά populated), ο stamper διαβάζει scalars χωρίς
LazyInit, `saveAll` κάνει merge — μία batch-tx μέσω της transactionality του repository.
(δ) `@Order(100)`: μετά τους seeders (ο `DataSeeder` δεν φτιάχνει αναθέσεις, οπότε
δεν υπάρχει σειριακή εξάρτηση).

Interference (το ρίσκο του ApplicationRunner): ο runner τρέχει σε ΚΑΘΕ context-load.
Αλλά πυροδοτείται στο **load** — πριν τα `@BeforeEach`/test bodies φτιάξουν τα δικά
τους (committed) null-snapshot rows· δεν ξανα-τρέχει per-test (context caching). Άρα
δεν αγγίζει rows που τα tests κρατούν σκόπιμα null (TransactionalRollbackτest auto-
assignments, WiringTest prefill). Επιβεβαιωμένο: full suite **99/99**, με ΜΟΝΟ μία
`backfilled 1` γραμμή (το direct `run()` του test) — τα υπόλοιπα startup loads no-op
αφού το dev DB είχε ήδη γεμίσει.

Πραγματικό backfill: το πρώτο context-load γέμισε **3086** αναθέσεις του dev DB
(backup `pre-S3e` πριν, κανόνας #6). Tests: null→backfilled από live· υπάρχον
(αποκλίνον) snapshot→**frozen** (όχι re-stamp)· 2η run→no-op.

**S3 (snapshot-on-write) ΟΛΟΚΛΗΡΩΜΕΝΟ** (S3a→S3e): schema/entity → single source →
4 write-paths (incl. atomic solver persistence/BL-1) → render snapshot-first →
backfill. Invariant #1 (render-from-snapshot) ενεργό end-to-end.

### [ffbf699] S4a — characterization tests (pin current behavior) πριν τον weight refactor
Πρώτο βήμα του S4 (data-driven constraint weights): **regression guard ΠΡΙΝ** αγγίξουμε
τον μηχανισμό βαρών. 6 νέα ConstraintVerifier tests (2/κανόνα — positive + boundary/
negative) για τα 3 constraints που ήταν **χωρίς κάλυψη**: `sameCourseConflict` (HARD,
weekly), `avoidOverloadedDay` (SOFT, weekly), `directionGroupADifferentDays` (SOFT,
exam). Verifier 46→52, full suite 99→105, **+61/−0 production lines** (tests-only,
κανένα υπάρχον test ή production γραμμή δεν άλλαξε).

ΜΕΘΟΔΟΛΟΓΙΑ — characterization testing: «κλειδώνουμε» την ΑΚΡΙΒΗ τρέχουσα ποινή κάθε
κανόνα ΠΡΙΝ τον S4b (externalization βαρών). Επειδή στο S4b τα defaults θα είναι ίδια με
τα σημερινά literals, ο refactor γίνεται **provably behavior-preserving**: κάθε υπάρχον
verifier test ΠΡΕΠΕΙ να μείνει πράσινο αυτούσιο — λειτουργεί ως tripwire που πιάνει κάθε
ακούσια μεταβολή ποινής. Τα 3 «τυφλά» constraints αποκτούν baseline πρώτα, ώστε να μην
μπει ο S4b με κανόνες που κανένα test δεν φρουρεί. Τεχνική: characterization / golden-
master tests ως ασφαλές δίχτυ refactor σε υπάρχοντα κώδικα (Feathers, *Working
Effectively with Legacy Code*).

PINNED VALUES (τρέχουσα συμπεριφορά, όχι «ιδανική»):
- `sameCourseConflict`: `ONE_HARD` ×1 → `penalizesBy(1)`/ζεύγος (join courseId+timeSlot)·
  boundary: ίδιο course σε άλλα slots → 0.
- `avoidOverloadedDay`: `ONE_SOFT`, weigher = `count−4` για >4 required lectures ανά
  (έτος, μέρα)· positive 5 → 1, boundary ακριβώς 4 → 0.
- `directionGroupADifferentDays`: `ONE_SOFT` ×5/ζεύγος όταν δύο εξετάσεις ίδιας Ομάδας Α
  πέφτουν ίδια ημ/νία· boundary: ίδια μαθήματα άλλες μέρες → 0.

SEMANTICS NUANCE (για το γραπτό): το `penalizesBy(n)` του ConstraintVerifier μετρά το
ΣΥΝΟΛΙΚΟ match-weight (Σ των weigher outputs), **ΟΧΙ** το base ConstraintWeight. Γι' αυτό
στον κώδικα συνυπάρχουν δύο στυλ: «Στυλ-1» (βάρος στο base score, π.χ. `ofHard(5)`,
weigher=1) ελέγχεται με `penalizesBy(1)`/match, ενώ «Στυλ-2» (βάρος στον weigher, π.χ.
`(a,b)->5`, base=`ONE_*`) με `penalizesBy(5)`. Κρίσιμο για τον S4b: αν ο refactor
μετακινήσει βάρος base↔weigher (π.χ. ofSoft(5)+weigher 1 αντί ONE_SOFT+weigher 5), τα
νούμερα των tests αλλάζουν **σιωπηλά** ενώ η συνολική ποινή μένει ίδια — άρα η
externalization πρέπει να διατηρεί τη θέση του βάρους, αλλιώς το «behavior-preserving»
είναι φαινομενικό.

S5 COUPLING (risk προς θύμηση): τα direction tests κουμπώνουν στο **hardcoded**
`DirectionRegistry.GROUP_A` (Κ1: CEID_NE5057, CEID_NE4168 — από τα ίδια fixtures του
υπάρχοντος weekly directionGroupA test). Όταν ο S5 μεταφέρει τη membership μαθήματος→
κατεύθυνσης σε `Direction` entity + `course_directions` (M2M, αντικαθιστά το registry —
task E), τα fixtures αυτά θα χρειαστούν τη membership **διαθέσιμη χωρίς DB** (seed/fixture
μέσα στον ConstraintVerifier), αλλιώς θα σπάσουν ή θα γίνουν no-op.

Verification: solver-only `*ConstraintProviderTest` 52/52, full suite **105/105**
πράσινο, `BUILD SUCCESS`.

### [06e2df9 + 938c74f + ba32eba] S4b — data-driven constraint weights (externalize → persist → overlay)
Τα **31 βάρη** των solver constraints (17 weekly + 14 exam) έγιναν editable/data-driven
**χωρίς καμία αλλαγή συμπεριφοράς** (seed = ακριβώς τα σημερινά literals).

ΕΠΙΛΟΓΗ ΜΗΧΑΝΙΣΜΟΥ — **Option B** (custom weight holder `SolverWeights` + overlay-on-
defaults) αντί native `@ConstraintConfiguration`. Αιτιολογία: (α) ελάχιστο test churn —
defaults=literals → τα 52 verifier μένουν αυτούσια· (β) ομοιογένεια με τα υπάρχοντα
availability registries (`TeacherAvailabilityConstraints`/`RoomAvailabilityConstraints`,
ίδιο overlay-on-defaults μοτίβο)· (γ) position-preserving· (δ) το **Timefold 1.11 ΔΕΝ έχει
`ConstraintWeightOverrides`** — επιβεβαιωμένο από το jar του core (μόνο
`@ConstraintConfiguration`/`@ConstraintWeight` υπάρχουν). Documented upgrade path:
`@ConstraintConfiguration` ή `ConstraintWeightOverrides` μετά από version bump — η μετάβαση
είναι μηχανική (το βάρος ήδη εξωτερικευμένο σε keys). Το per-constraint score breakdown για
demo/αξιολόγηση παραμένει διαθέσιμο μέσω `ScoreManager`/`ConstraintMatchTotal`, ανεξάρτητο
του μηχανισμού βαρών.

ΑΡΧΙΤΕΚΤΟΝΙΚΗ (3 βήματα):
- **S4b-1 (06e2df9) — externalize:** `SolverWeights` ως single source· 31 keys, **defaults ==
  τα προηγούμενα literals**. Position-preserving rewire των 31 `penalize()`: «Στυλ-1» (βάρος
  στο base score) → `hard()`/`soft()`· «Στυλ-2» (βάρος στον weigher) → base `ONE_*` +
  `w()*<φόρμουλα>`. **Δεν μετακινήθηκε βάρος base↔weigher** (βλ. S4a §SEMANTICS NUANCE) → τα
  νούμερα των verifier tests αμετάβλητα. Φρουρός: τα characterization tests του S4a + τα 52
  verifier αυτούσια. `resetToDefaults()` στα 2 `@AfterEach` (forward-safety για το overlay).
- **S4b-2a (938c74f) — persist:** `SolverWeights` → πλήρες **catalog** (record `Def`:
  key/scope/level/defaultWeight/ελληνικό label/περιγραφή· τα defaults παράγονται ΑΠΟ το
  catalog). Entity `ConstraintWeightConfig` + **V4** (additive `CREATE TABLE`, UNIQUE
  `constraint_key`, `jsonb params`, `timestamptz` created/updated) + idempotent **insert-if-
  absent** seeder (`ConstraintWeightSeeder`, `@Order(50)`, SLF4J — όχι `System.out`, μάθαμε
  από το S3e mojibake). Ο solver **ΑΚΟΜΑ στα defaults** — `loadConstraintsFromDb` άθικτο,
  μηδενική αλλαγή solve (ίδιο idiom με V3/SnapshotBackfillRunner: DDL-only migration, seed σε
  Java). Νέο έδαφος για τον κώδικα: πρώτη χρήση jsonb + Hibernate `@CreationTimestamp/
  @UpdateTimestamp` — το `ddl-auto=validate` πέρασε καθαρά (Flyway v4 + entity ↔ σχήμα).
- **S4b-2b (ba32eba) — overlay:** `applyConstraintWeightOverrides()` (package-private,
  testable — precedent S2/S3d) στην **ΑΡΧΗ** του `loadConstraintsFromDb()`. **ΚΡΙΣΙΜΟ:** το
  method έχει `if (dbConstraints.isEmpty()) return;` early-return — άρα η κλήση ΠΡΕΠΕΙ να μπει
  πριν, αλλιώς θα παρακαμπτόταν όταν δεν υπάρχουν teacher constraints. `resetToDefaults()`
  ΠΡΩΤΑ → idempotent per-solve (αλλαγή & επαναφορά βάρους στη ΒΔ αντικατοπτρίζεται σωστά).
  Εδώ ο solver αρχίζει να διαβάζει τα persisted βάρη.

POLICY (κλειδωμένη — recon §D): `score_level` **read-only** (διαβάζεται μόνο για το διαχωρισμό
HARD-floor vs SOFT-disable)· **HARD-floor**: disabled ή weight<1 → fallback στο code default —
ΠΟΤΕ σιωπηλή απώλεια hard κανόνα· **SOFT-disable**: disabled → βάρος 0. Safety: orphan/unknown
key (stale row μετά από κατάργηση κανόνα) → `log.warn` + skip, ΔΕΝ σπάει το solve.

SINGLE SOURCE OF TRUTH: το catalog (`SolverWeights`) τροφοδοτεί **και** τα call sites των
providers **και** τον seeder (DB) **και** το overlay (read-back) → κανένα drift
literal↔seed↔provider, μία πηγή ανά βάρος. Acceptance: 31 catalog keys ≡ 31 provider keys
(grep + test).

VERIFICATION: 52 verifier **αυτούσια** (defaults=literals, δεν χτυπούν DB· @AfterEach reset)·
`ConstraintWeightSeederTest` (clean→**31** με catalog defaults / idempotent / δεν επαναφέρει
admin edit)· `ConstraintWeightOverlayTest` **6 σενάρια** (no-op defaults / SOFT change+disable→0
/ HARD floor disable+zero→default / HARD raise)· **ΡΗΤΟ baseline** (throwaway full FALL solve με
seeded defaults) → **266/266 @ hard 0** μετά το overlay = ίδιο με το documented baseline. Full
suite **114/114**.

ΕΓΚΡΙΣΗ ΕΠΙΒΛΕΠΟΝΤΑ: S4b = **μηχανισμός, ΜΗΔΕΝΙΚΗ αλλαγή τιμών** (baseline αμετάβλητο by
construction). Έγκριση βαρών χρειάζεται μόνο (α) όταν ο admin επεξεργαστεί τιμή μέσω UI, (β) για
τα 2 νέα βάρη του Direction (S5). Ο πλήρης **πίνακας 31 βαρών** στο catalog = official baseline
προς έγκριση (κανόνας #4, ιεραρχία βαρών).

FORWARD / DEBT: το static `WEIGHTS` είναι ασφαλές **όσο τα solves είναι σειριακά** (ένα
`SolverFactory` ανά solve)· όταν έρθει ο async solver (**A10**), το `WEIGHTS` + τα 3 availability
registries πρέπει να **de-static-οποιηθούν ΜΑΖΙ** (κοινό global-state debt). **BL-10:** dead-code
— 2 constraint methods στο `TeacherAvailabilityConstraints` (διπλότυπα των wired κανόνων του
`CeidConstraintProvider`, εντοπίστηκαν στο S4b-1) → cleanup σε B-φάση.

## Φάση 7 (stretch) — Public StudentView (=A8)

### [ab64f86] PV-1 — backend account-less public read endpoints (Φάση 1/2)

ΣΤΟΧΟΣ: account-less, read-only δημόσια πρόσβαση **ΜΟΝΟ σε PUBLISHED** προγράμματα,
ώστε ένας φοιτητής να βλέπει/εκτυπώνει το δημοσιευμένο πρόγραμμα **χωρίς login**. Backend
μόνο (Φ1)· το frontend public route + auto-update (polling) έρχεται στη Φ2.

ΣΧΕΔΙΑΣΗ — ΞΕΧΩΡΙΣΤΟ NAMESPACE, ΟΧΙ ΧΑΛΑΡΩΜΑ ΥΠΑΡΧΟΝΤΩΝ: νέος
`PublicTimetableController` σε `/api/public/timetables`, **χωρίς να αγγιχτεί κανένα από τα
authenticated endpoints** του `TimetableController`. Στο `SecurityConfig` μία μόνο γραμμή —
`.requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()` — τοποθετημένη **ΑΚΡΙΒΩΣ
ΠΡΙΝ** τον γενικό `GET /api/** → authenticated()` (η σειρά των matchers είναι σημαντική:
ο πιο ειδικός πρώτος). **GET-only public**: κάθε non-GET στο `/api/public/**` πέφτει στο
`anyRequest().authenticated()` → η δημόσια επιφάνεια είναι αυστηρά read-only by construction.
Ο `JwtAuthFilter` ήδη περνά no-token requests χωρίς auth context (ίδιο μοτίβο με
`/api/health`, `/api/auth/**`), οπότε permitAll = πραγματικά account-less.

ΔΥΟ ΕΠΙΠΕΔΑ ΠΡΟΣΤΑΣΙΑΣ ΔΙΑΡΡΟΗΣ:
- **(α) Minimal DTO, ΟΧΙ raw entity.** Νέο record `PublicTimetableDto`
  (id/name/academicYear/timetableType/semesterType/publishedAt). Το raw `Timetable` entity
  θα διέρρεε `createdBy`, `notes`, `solverScore/Conflicts/TimeSeconds`, `excludedDates`,
  `status` — όλα κρύβονται. (Σημ.: τα authenticated endpoints εξακολουθούν να γυρνούν raw
  entity· εδώ, στο public, σκόπιμα minimal projection.)
- **(β) PUBLISHED gating με 404 (όχι 403) για μη-δημόσια.** Λίστα: `findByStatus(PUBLISHED,
  …)` → **ΠΟΤΕ** μη-PUBLISHED. Assignments: `findById` + `status == PUBLISHED`, αλλιώς
  **404** (και για DRAFT). 404 αντί 403 ώστε να μη διαρρέει **ούτε η ύπαρξη** μη-δημόσιου
  προγράμματος (un-enumerable). **ΚΡΙΣΙΜΟ — υπάρχον κενό που έκλεισε:** το authenticated
  `GET /api/timetables/{id}/assignments` ΔΕΝ ελέγχει status (role-filtering μόνο στη λίστα,
  βλ. recon)· το public endpoint **επιβάλλει ρητά** το PUBLISHED check, αλλιώς το permitAll
  θα είχε εκθέσει DRAFT αναθέσεις.

ΕΠΑΝΑΧΡΗΣΗ ΧΩΡΙΣ ΑΝΤΙΓΡΑΦΗ: ο `PublicTimetableController` κάνει inject τον
`TimetableController` (ίδιο `controller` package → νόμιμη κλήση του **package-private**
`assignmentToDto`) ⇒ οι δημόσιες αναθέσεις render-άρονται με **την ίδια snapshot-first
λογική** (invariant #1) με την authenticated προβολή· καμία απόκλιση render, μηδενική
διπλο-υλοποίηση. Καμία κυκλική εξάρτηση (TimetableController δεν εξαρτάται από το public).

ΜΗΔΕΝΙΚΟ ΣΧΗΜΑ / ΜΗΔΕΝΙΚΟ RISK ΣΤΟΝ SOLVER: το `status` enum (μαζί με `PUBLISHED`) υπήρχε
ήδη → **καμία migration, κανένα backup**. Δεν αγγίχτηκαν solver/constraints/entities — το
baseline (266/266 @ hard 0) δεν εμπλέκεται.

VERIFICATION: νέο `PublicTimetableControllerTest` (`@SpringBootTest @AutoConfigureMockMvc`,
**MockMvc χωρίς Authorization header** — πρώτο MockMvc test του repo, ασκεί το πραγματικό
security filter chain). 3 σενάρια: (1) `GET /api/public/timetables` no-token → 200 & περιέχει
το published id **όχι** το draft (membership check, robust σε dev-DB δεδομένα)· (2)
draft `…/assignments` no-token → **404**· (3) published `…/assignments` no-token → 200 &
μη-κενή λίστα. Marker-based seed/cleanup (`TEST_PUBVIEW_`), ΧΩΡΙΣ `@Transactional` (τα seeds
commit ώστε να τα δει το request μέσα από το filter chain). Full suite **122/122** πράσινο,
κανένα υπάρχον authed test δεν έσπασε.

ΑΠΟΦΑΣΗ ΤΟΠΟΘΕΤΗΣΗΣ DTO: μπήκε στο `controller` package (όχι νέο `controller/dto`) — δεν
υπάρχει dedicated dto package· τα DTOs ζουν στο controller layer (ως `Map<String,Object>`).
Co-located με τον public controller, χωρίς one-file package.

FORWARD (Φ2): public frontend route **έξω** από `PrivateRoute`/`AuthProvider` + ξεχωριστό
axios χωρίς τον `401 → redirect /login` interceptor (αλλιώς redirect-loop σε no-token call)
+ polling auto-update. Ανοιχτό spec-ερώτημα: ΔΕΝ υπάρχει single «current» flag — πολλά
PUBLISHED μπορούν να συνυπάρχουν (η λίστα ταξινομείται `publishedAt DESC`· ο client επιλέγει).

### [862402e · 1b0dfbf · 986ae90] PV-2 — frontend public route + single-source render + polling auto-update (Φάση 2/2)

ΣΤΟΧΟΣ: account-less δημόσια προβολή στο `/public` — ο φοιτητής βλέπει/εκτυπώνει/εξάγει iCal
το δημοσιευμένο πρόγραμμα **χωρίς login**, και η σελίδα **auto-ανανεώνεται** όταν ο admin αλλάζει
ένα PUBLISHED πρόγραμμα. Frontend-only: **καμία** αλλαγή backend, **καμία** migration/backup, ο
solver δεν εμπλέκεται (baseline 266/266 @ hard 0 ανέγγιχτο).

SINGLE-SOURCE RENDER (extract, μηδενική διπλο-υλοποίηση): το μονολιθικό `StudentView` (~665 γρ.)
σπάστηκε σε (α) **presentational** `StudentTimetableView` (όλο το JSX + ui-state: `yearFilter`/
`mode`/`selectedCourses` + localStorage persistence + όλα τα derived `useMemo`) και (β) **data-hook**
`useStudentTimetableData(client, basePath, {pollMs?})` παραμετροποιημένο με **axios client + base
path**. Authed `/view` (`api`, `/timetables`) & public `/public` (`publicApi`, `/public/timetables`)
γίνονται thin wrappers που μοιράζονται **μία πηγή render** — ακριβής **frontend-αντιστοιχία** του
backend reuse όπου ο `PublicTimetableController` καλεί το package-private `assignmentToDto` του
`TimetableController` (PV-1): μία λογική render, δύο επιφάνειες (authed/public). Shared types
(`Timetable`/`Assignment`) σε `studentTimetableTypes.ts` → καθαρό DAG (hook→types, component→types,
wrappers→hook+component, χωρίς cycle).

ACCOUNT-LESS ΧΩΡΙΣ REDIRECT-LOOP: νέο `publicApi` = καθαρό `axios.create` **χωρίς κανένα
interceptor**. Κρίσιμο: ο authed `api` έχει response interceptor `401 → localStorage.clear +
window.location='/login'`· αν τον χρησιμοποιούσε η public σελίδα, ένα leftover/άκυρο token ή ένα
401 θα πετούσε τον επισκέπτη στο login. Επιπλέον η route `/public` μπαίνει ως **sibling του
`/login`, ΠΡΙΝ** το `/*` catch-all → **εκτός** `PrivateRoute` (κανένα redirect σε no-token) και
**εκτός** `AppLayout`/`Navbar` (ο `Navbar` καλεί `useAuth()` — δεν τον θέλουμε public). Παραμένει
μέσα στο `AuthProvider` (αβλαβές: χωρίς token `user=null`, ο redirect ζει μόνο στο `PrivateRoute`).

POLLING AUTO-UPDATE (smooth, χωρίς SSE): `setInterval(20s)` ενεργό **μόνο** όταν δοθεί `pollMs`
(άρα authed `/view` δεν κάνει poll, μόνο public). **Pause-on-hidden** (`if (!document.hidden)`) +
**immediate refresh on visible** (`visibilitychange`) → δεν χτυπά το backend σε αόρατο tab, αλλά
δείχνει φρέσκα δεδομένα μόλις επιστρέψει ο χρήστης. Το interval μένει **σταθερό** μέσω
**latest-ref** (`selectedRef.current = selectedTtId`): τα `fetchList`/`refresh` callbacks διαβάζουν
την τρέχουσα επιλογή χωρίς να εξαρτώνται από το `selectedTtId`, ώστε το `setInterval` να μην
ξαναστήνεται σε κάθε αλλαγή προγράμματος. Το `refresh()` (poll path) **δεν** αγγίζει loading/clear
→ καμία αναλαμπή στο auto-update· το default-selection ξανατρέχει **μόνο** αν χαθεί η τρέχουσα
επιλογή από τη λίστα → το polling δεν μηδενίζει την επιλογή του χρήστη.

PARITY (no-behavior-change του 2a, οριστικοποιημένο στο fix 986ae90): το selection-effect
αποκαθιστά (α) **clear-on-switch** (`setAssignments([])` πριν το fetch) ώστε το authed `/view` να
είναι πραγματικά πανομοιότυπο με πριν, και (β) **empty-state**: με μηδέν προγράμματα η επιλογή μένει
`null` → καθαρίζει + σταματά το loading (όχι «Φόρτωση…» επ' άπειρον). Το clear ζει στο effect (όχι
στο `refresh()`) ώστε ο διαχωρισμός «switch=clear / poll=smooth» να είναι ρητός.

VERIFICATION: `tsc --noEmit` + `npm run build` καθαρά μετά από κάθε step. ESLint: τα 6 νέα αρχεία
καθαρά· το `StudentTimetableView` φέρει **αυτούσια** τα 4 pre-existing lint errors του παλιού
`StudentView` (επιβεβαιωμένο με stash-baseline) → **ESLint debt αμετάβλητο** (`any` count 1→1,
relocated). Μία στοχευμένη `eslint-disable react-hooks/refs` στο intentional latest-ref pattern
(load-bearing για το σταθερό interval), με σχόλιο αιτιολόγησης. Οι runtime έλεγχοι (visual parity
`/view`, account-less `/public`, 20s auto-update) είναι browser-side, για επιβεβαίωση στο review.

DELIVERY: 3 commits — `862402e` extract (StudentTimetableView + hook), `1b0dfbf` public view
(publicApi + PublicStudentView + route), `986ae90` fix (clear-on-switch + empty-state). ΕΚΤΟΣ
SCOPE (επόμενα μικρο-tasks): αφαίρεση ρόλου/seeding `STUDENT`· SSE (το polling αρκεί)· server-side
απόκρυψη `visibleInTimetable=false` (γίνεται client-side, όπως ήδη).

## Feature #2 — Non-blocking manual editing

### [f98416e] NB-1 — backend: advisory placement (3 structural HARD vs υπόλοιπα non-blocking, βήμα 1/2)

ΣΤΟΧΟΣ: ο καθηγητής/admin τοποθετεί/μετακινεί χειροκίνητα **πάντα** (ποτέ reject) για
παραβιάσεις **scheduling-constraints** και βλέπει advisory ανατροφοδότηση, αντί να μπλοκάρεται.
Backend-only (βήμα 1/2)· το frontend (αφαίρεση client-side pre-blocks + εμφάνιση warnings ως
notice) έρχεται στο βήμα 2/2.

ΑΠΟΦΑΣΗ — 3 STRUCTURAL HARD vs ΥΠΟΛΟΙΠΑ ADVISORY: από τους 16 ελέγχους του
`validateAssignment`, **μένουν HARD blocks (4xx)** μόνο οι **3 structural/data-integrity** που
αλλιώς παράγουν corrupt/un-renderable δεδομένα: **#1** ελλιπή δεδομένα (null
timetable/course/room/timeSlot/type), **#3** type↔timetable συμβατότητα (EXAM type μόνο σε
εξεταστικό πρόγραμμα & αντίστροφα), **#4** exam-slot structural rules (slotType EXAM, specificDate
set, εντός start/end). Όλοι οι υπόλοιποι (**#2, #5–#16**: room double-book, teacher conflict,
year-room κανόνες CEID, όρια ωρών, ≥6 ώρες θεωρίας/ημέρα, lunch break κ.λπ.) → **advisory**: η
τοποθέτηση γίνεται και επιστρέφεται non-blocking `warnings` μέσα στο **200** response.

ΥΛΟΠΟΙΗΣΗ — COPY-NOT-MOVE (συνειδητή μικρο-διπλασίαση): νέα `private validateStructural(...)`
που τρέχει ΜΟΝΟ τα #1/#3/#4 (τα 3 check blocks **VERBATIM copies** — ίδια conditionals + ίδια
ελληνικά `badRequest` μηνύματα). Το `validateAssignment` **ΜΕΝΕΙ ΑΝΕΠΑΦΟ** (κρατά και τους 16):
το χρησιμοποιεί **advisory** το `getPlacementOptions` (allowed/blocked overlay), οπότε αλλαγή του
θα έσπαγε εκείνο το μονοπάτι. DRY consolidation σημειωμένη ως deferred (σχόλιο στον κώδικα).

ADVISORY BRIDGE: στους δύο write-paths, μετά το HARD `validateStructural` gate, καλείται το
`validateAssignment` (πρώτη παραβίαση, early-return) ΠΡΙΝ το save και το string εξάγεται με τον
υπάρχοντα `getValidationErrorText(ResponseEntity)` → `warnings` (`List<String>`). Στο `moveAssignment`
το advisory τρέχει με `ignoredAssignmentId = self` (να μη συγκρούεται με την τρέχουσα θέση του),
υπολογισμένο ΠΡΙΝ το mutate/save. Το `warnings` μπαίνει στο φρέσκο mutable `LinkedHashMap` του
`assignmentToDto` (key `warnings`). **Contract:** 0-ή-1 εγγραφή σε αυτό το βήμα — array για
forward-compat· το Feature #3 θα το γεμίσει με πλήρη κατηγοριοποιημένη λίστα (ScoreAnalysis).
Μικρο-σημείωση: το request param των δύο handlers λέγεται ήδη `body` → το response map ονομάστηκε
`responseBody` (αποφυγή shadowing — η literal πρόταση του spec θα έκανε collision).

ΑΝΕΠΑΦΑ paths: `validateAssignment` (ως έχει), `getPlacementOptions`, `autoSchedule` (έχει δικό του
non-blocking μηχανισμό `getBlockingReasonsInMemoryFast` → best-effort skip, ποτέ reject), `solve`,
`removeAssignment`· persistence/snapshot stamping αμετάβλητα. **Καμία migration / schema change /
backup** (καθαρά λογική controller). Κανένας solver/constraint κώδικας → baseline 266/266 @ hard 0
διατηρείται by construction (οι ConstraintVerifier tests 29+23 πράσινοι).

VERIFICATION: νέο `NonBlockingPlacementTest` (`@SpringBootTest` + MockMvc, marker `TEST_NB_`,
ΧΩΡΙΣ `@Transactional`, JWT ADMIN μέσω `POST /api/auth/login`), 4 σενάρια: (1) advisory add
(room double-book) → **200** + μη-κενά warnings + persisted· (2) structural add (EXAM σε SEMESTER,
#3) → **400** + NOT persisted· (3) advisory move (σε κατειλημμένο slot) → **200** + warnings + νέα
θέση αποθηκευμένη· (4) clean add → **200** + κενά warnings + persisted. Full suite **121/121**
(117 baseline + 4), κανένα υπάρχον test δεν έσπασε.

FORWARD (βήμα 2/2 — frontend): αφαίρεση client-side pre-blocks (`WeeklyTimetable.handleDrop` silent
abort γρ. 458, `submitManualAssignment` hour-guards γρ. 670–681)· εμφάνιση `warnings` ως
non-blocking notice αντί error banner· relabel drag hint «Μη επιτρεπτή τοποθέτηση» → advisory
wording (διατήρηση χρωμάτων). DEBT: `validateStructural` ⊂ `validateAssignment` DRY consolidation.

## Data integrity — study_year invariant

### [7009ef3] DF-1 — διόρθωση CEID_22Y101 study_year (seeder + Flyway V5)

ΑΣΥΝΕΠΕΙΑ: το μάθημα **CEID_22Y101 «Διακριτά Μαθηματικά»** είχε `semester=3` (σωστό, 3ο
εξάμηνο) αλλά `study_year=1` (λάθος· σωστό = **2**, αφού έτος = `ceil(εξάμηνο/2)`). **Μοναδικό**
ασυνεπές μάθημα στη βάση (`study_year <> CEIL(semester/2.0)` → 1 row, semester 3). Διπλή πηγή:
(α) ο `config/DataSeeder.java:126` τροφοδοτούσε `sem=1, year=1`, και (β) η live dev βάση είχε
`semester=3` (διορθωμένο post-seed μέσω CRUD) αλλά το `study_year` έμεινε στο seeded 1 — ποτέ δεν
ανέβηκε σε 2 (ο upsert-skip `req(...)` δεν ξαναγράφει υπάρχουσα εγγραφή).

ΣΥΝΕΠΕΙΑ (γιατί έγινε ορατό): ο advisory έλεγχος year-room του `validateAssignment`
(`TimetableController` ~γρ.1790, `course.getStudyYear() == 1 && !"Γ".equals(room.getCode())`) κλειδώνει
στο `study_year`, ΟΧΙ στο `semester` → θεωρούσε το 3ου-εξαμήνου μάθημα 1ου έτους και έβγαζε λάθος
warning «μαθήματα 1ου έτους μόνο στο Αμφιθέατρο Γ».

ΔΙΟΡΘΩΣΗ — δύο εστίες (καθαρά data, καμία αλλαγή λογικής/βαρών): (α) **seed source**
`DataSeeder.java:126` `sem 1→3, year 1→2` (τα υπόλοιπα lec=3/tut=2/lab=0/ects=7/«ΕΘ»/«FALL»/244
αμετάβλητα· «FALL» σωστό για περιττό εξάμηνο), ώστε καθαρό re-seed να μην ξαναγεννά το λάθος· (β)
**Flyway `V5__fix_study_year_invariant.sql`** — idempotent, γενικό: `UPDATE courses SET study_year =
CEIL(semester/2.0) WHERE study_year <> CEIL(semester/2.0)`. Επιβάλλει τον αναλλοίωτο `study_year =
ceil(semester/2)` σε migration time· σε συνεπή βάση επηρεάζει 0 γραμμές (idempotent), οπότε διορθώνει
και το συγκεκριμένο row και οποιαδήποτε μελλοντική ασυνέπεια.

SOLVER-RELEVANCE & VERIFY: το `study_year` δεν είναι μόνο display — τροφοδοτεί **year-constraints**
του solver (required-same-year conflict, sameYearSameDay spread, year-room κανόνες), γι' αυτό η
αλλαγή θεωρείται HIGH-RISK και απαιτεί re-verify του baseline. Full suite **121/121** πράσινο (οι
ConstraintVerifier tests χρησιμοποιούν synthetic data → ανεπηρέαστοι· το context boot εφάρμοσε το V5
στη live βάση — Flyway `Current version: 5`, success=t). Post-fix SQL: CEID_22Y101 → `semester=3,
study_year=2`· inconsistent = **0**. Backup πριν την αλλαγή: `backups/ceid_timetable_2026-06-22_0359.dump`.
Runtime re-verify (από τον Pantelis, browser): (1) το λάθος warning «1ου έτους» δεν εμφανίζεται πλέον
στο CEID_22Y101· (2) auto-schedule ολόκληρου του εβδομαδιαίου dataset → **266/266 @ hardScore 0**
(το μάθημα ξαναομαδοποιείται ως 2ο έτος, πρέπει να μένει feasible).

FUTURE WORK: το `study_year` θα ήταν ασφαλέστερο ως **derived invariant** του `semester`
(computed `ceil(semester/2)` αντί stored field) — θα απέκλειε εξ ορισμού αυτή την κατηγορία bug
(stored-but-stale). Σήμερα είναι αποθηκευμένο πεδίο σε πολλά σημεία (entity, seeder, solver POJOs,
snapshot), οπότε η μετατροπή είναι ξεχωριστό refactor — σημειωμένο, εκτός scope εδώ.
