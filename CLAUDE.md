# CLAUDE.md — ceid-timetabling-system

Διπλωματική εργασία ΤΜΗΥΠ (CEID) Παν. Πατρών: σύστημα ωρολογίου προγράμματος
& εξεταστικής. Υποψήφιο για επίσημη χρήση από το τμήμα — προτεραιότητα σε
ποιότητα, ασφάλεια, συντηρησιμότητα.

## Δομή & Stack

- `timetable/` — backend: Java 21, Spring Boot 3.5, JPA, Spring Security (JWT),
  PostgreSQL 16, Timefold Solver 1.11
- `frontend/` — React 19 + TypeScript + Vite (dark theme `#080f1a`)
- Solver: `timetable/src/main/java/gr/upatras/ceid/timetable/solver/`
  - `CeidConstraintProvider` (εβδομαδιαίο, 17 constraints) και
    `ExamConstraintProvider` (εξεταστική, 14 constraints)
  - Κάθε `Lesson` = 1 ώρα (weekly) ή 1 εξέταση σε 3ωρο slot (exam)
- Υπάρχουν: 47 ConstraintVerifier tests, GitHub Actions CI, Docker compose,
  backup scripts (`scripts/`), Swagger UI (`/swagger-ui.html`)
- Test accounts (dev μόνο): admin/admin123, teacher/teacher123, student/student123

## Εντολές (Windows)

Το backend (`mvnw.cmd`, `pom.xml`) βρίσκεται στο nested path `timetable\timetable\`
σε σχέση με το repo root — όλες οι `mvnw.cmd` εντολές τρέχουν από εκεί.

Backend (από `timetable/`):
- Build: `mvnw.cmd clean compile` · Tests: `mvnw.cmd test` (θέλει PostgreSQL up
  + env var `DB_PASSWORD`) · Run: `mvnw.cmd spring-boot:run` (port 8080)
- Μόνο solver tests: `mvnw.cmd test -Dtest=*ConstraintProviderTest`

Frontend (από `frontend/`):
- `npm install` · `npx tsc --noEmit -p tsconfig.app.json` · `npx eslint src`
- `npm run build` · dev server: `npm run dev` (port 5173)

Βάση: db `ceid_timetable`, user `ceid_admin`, password από env `DB_PASSWORD`.
Secrets: ΜΟΝΟ σε `.env` (gitignored) / env vars. ΠΟΤΕ σε tracked αρχεία.

## Κανόνες εργασίας (ΑΠΑΡΑΒΑΤΟΙ)

1. ΕΝΑ task τη φορά. Ροή: υλοποίηση → `mvnw.cmd test` ΟΛΑ πράσινα (και τα 47
   υπάρχοντα) → conventional commit (feat/fix/test/refactor/chore με scope) →
   σύντομη αναφορά τι άλλαξε.
2. Πριν από ΚΑΘΕ αλλαγή σε solver/validation: τρέξε πρώτα τα ConstraintVerifier
   tests για baseline. Μετά την αλλαγή: ξανά. Κάθε νέο/αλλαγμένο constraint
   συνοδεύεται από νέο test.
3. ΜΗΝ αλλάζεις βάρη/λογική υπαρχόντων constraints χωρίς ρητή έγκριση —
   υλοποιούν επίσημους κανόνες του τμήματος.
4. Ιεραρχία βαρών (διατήρησέ τη): HARD: βασικά conflicts 1, required same-year 5,
   blocked slots 10. SOFT exam: sameYearSameDay 6, directionGroupA 5,
   A6 preferences 4, spread 3, teacher/day 2, tie-breakers 1.
5. Σχήμα DB: `ddl-auto=update` προς το παρόν — όταν μπει Flyway, ΚΑΘΕ αλλαγή
   σχήματος = νέο migration, ποτέ edit παλιών.
6. SQL σε δεδομένα: transaction με ROLLBACK δυνατότητα + πρόταση backup
   (`scripts/backup-db.ps1`) πριν.
7. ΜΗΝ προσθέτεις προσωπικά δεδομένα (ονόματα/διαθεσιμότητες καθηγητών) σε
   κώδικα ή tracked αρχεία — μόνο στη βάση. Εκκρεμεί μεταφορά του
   TeacherAvailabilityRegistry στη DB + καθάρισμα git history (task #4).
8. Γλώσσα: απαντήσεις ελληνικά, κώδικας/identifiers αγγλικά, UI strings ελληνικά.
9. Σε κάθε αλλαγή που αξίζει για το γραπτό: ετικέτα «📝 thesis note» στο τέλος
   της αναφοράς (design decisions, μετρικές πριν/μετά, αρχιτεκτονικά σκεπτικά).

## Πηγή αλήθειας δεδομένων

`docs/official/` = επίσημα έγγραφα τμήματος (ΠΠΣ κατάλογος μαθημάτων, Κ1-Κ6
κατευθύνσεις). Για διασταυρώσεις ονομάτων/κωδικών: αυτά υπερισχύουν του κώδικα.
`docs/private/` (gitignored, μόνο τοπικά) = φόρμες διδασκόντων με προσωπικά
δεδομένα. ΜΗΝ αντιγράψεις περιεχόμενό τους σε tracked αρχεία.

## Baseline αναφοράς

Weekly solver στο πλήρες dataset (118 μαθήματα, 14+1 αίθουσες): 266/266 lessons,
hardScore 0, ~30s. Αν μετά από αλλαγή δεν πιάνει hardScore 0, κάτι χάλασε.
Σημ.: ορισμένα αποθηκευμένα timetables (π.χ. NewEarino) ΔΕΝ πιάνουν hardScore 0
λόγω δομικά μη-ικανοποιήσιμων co-taught μαθημάτων — δεν είναι regression,
βλ. task A.

## Ουρά tasks (με σειρά)

A) ~~Διάγνωση «6 validation errors με 0 hard» (NewEarino)~~ — ΔΙΑΓΝΩΣΤΗΚΕ
   (2026-06-13). Η αρχική υπόθεση (απόκλιση normalizers) ΔΕΝ ίσχυε: τα 6 errors
   ήταν ΑΛΗΘΙΝΕΣ συγκρούσεις διδάσκοντα (μαθήματα 31/68/71 «Θεωρία Υπολογισμού /
   Κρυπτογραφία / Ευρυζωνικές», co-taught από Κακλαμάνη+Παπαϊωάννου, ταυτόσημα
   ονόματα + κοινό teacher.id). Η «0 hard» δεν αναπαράγεται: re-solve δίνει
   hardScore -6 (SOLVED_WITH_HARD_CONFLICTS) — solver & validation ΣΥΜΦΩΝΟΥΝ.
   Ρίζα: δομική υπερ-ανάθεση (≈16 co-taught ώρες που πρέπει όλες να μην
   επικαλύπτονται) υπό τον περιορισμό διαθεσιμότητας του Κακλαμάνη (από τον
   hardcoded TeacherAvailabilityRegistry — `teacher_constraints` κενός, βλ.
   task #4). ΕΓΙΝΕ fix ενός δευτερεύοντος bug: το validation διπλομετρούσε
   SAME_COURSE_SAME_SLOT και ως TEACHER_CONFLICT (commit fix(validation), νέο
   TimetableControllerValidationTest).
   ΑΝΑΛΥΣΗ ΕΦΙΚΤΟΤΗΤΑΣ (2026-06-14, ΑΡΧΙΚΗ — ΑΝΑΤΡΑΠΗΚΕ): είχε εκτιμηθεί ότι τα
   co-taught είναι ΔΟΜΙΚΑ ΑΔΥΝΑΤΑ (κοινό παράθυρο μόλις ~10 slots/εβδ, 16 co-taught
   ώρες που κληρονομούν ΚΑΙ ΟΙ ΔΥΟ τα teacher keys → pigeonhole ≥6 αναπόφευκτες
   συγκρούσεις = το hardScore -6). Αυτό ΔΕΝ ισχύει.
   ΑΝΑΘΕΩΡΗΣΗ (2026-06-14, μετά από διερεύνηση των φορμών διδασκόντων σε
   docs/private): με ΣΩΣΤΑ δεδομένα τα co-taught ΔΕΝ είναι infeasible — το «10
   slots» της αρχικής ανάλυσης βασιζόταν σε ΛΑΘΟΣ δεδομένα του hardcoded
   TeacherAvailabilityRegistry. Ευρήματα: (1) το παράθυρο διαθεσιμότητας του ενός
   διδάσκοντα στον registry είναι υπερβολικά περιοριστικό — οι φόρμες δείχνουν
   διαθεσιμότητα και μετά τις 17:00 για τα co-taught, άρα το κοινό παράθυρο είναι
   ουσιαστικά μεγαλύτερο από 10 slots· (2) τα μαθήματα 68/71 έχουν phantom
   tut/lab ώρες στη DB που ΔΕΝ υπάρχουν στις φόρμες (πλασματικός φόρτος που
   φουσκώνει το «16 ώρες»)· (3) πιθανό λάθος attribution διδάσκοντα στο 68.
   ΣΥΜΠΕΡΑΣΜΑ: το hardScore -6 οφείλεται σε ΛΑΘΗ ΔΕΔΟΜΕΝΩΝ (λάθος registry, phantom
   ώρες, λάθος attribution), ΟΧΙ σε πραγματική δομική αδυναμία. Δεν χρειάζεται
   αλλαγή solver/constraints. Όλη η διόρθωση ανήκει στο task #4/E (διασταύρωση &
   μεταφορά δεδομένων διαθεσιμότητας/μαθημάτων στη DB)· εκεί τα co-taught θα πρέπει
   να γίνουν εφικτά (hardScore 0) με σωστά δεδομένα. Σημ.: τα προσωπικά δεδομένα
   των φορμών μένουν ΜΟΝΟ τοπικά (docs/private, gitignored) — δεν αντιγράφονται εδώ.
   ΕΚΚΡΕΜΕΙ (χωριστά): προαιρετικά η ενοποίηση normalizers σε TeacherNameUtil ως
   καθαρό DRY refactor (ΔΕΝ είναι bug fix).
B) ~~Tests για A6 constraints~~ — ΕΓΙΝΕ (47/47, βλ. ExamConstraintProviderTest).
C) Refactor: TimetableController (~2900 γραμμές) → ValidationService,
   PlacementService, ExamSlotService. Καμία αλλαγή συμπεριφοράς — τα tests κριτής.
   Κράτα μετρικές πριν/μετά (γραμμές/κλάση) για 📝 thesis.
D) A12: εξαιρούμενες ημερομηνίες/αργίες στη δημιουργία exam slots (UI πεδίο +
   προσυμπληρωμένες ελληνικές αργίες — έχουν βρεθεί εξετάσεις σε
   Πρωτοχρονιά/Θεοφάνια).
E) Διασταύρωση δεδομένων: ονόματα/κωδικοί μαθημάτων & καθηγητών του DataSeeder
   και της DB vs `docs/official/` — ΠΡΩΤΑ αναφορά αποκλίσεων, μετά διορθώσεις
   κατόπιν έγκρισης.

Ευρύτερο πλάνο: βλ. TASKS.md (αν υπάρχει στη ρίζα) — φάσεις A (features),
B (υποδομή — εκκρεμούν Flyway, Docker validation), C (UI polish + WCAG βλ.
WCAG_AUDIT.md), D (γραπτό).
