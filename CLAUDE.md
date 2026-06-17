# CLAUDE.md — ceid-timetabling-system

Διπλωματική εργασία ΤΜΗΥΠ (CEID) Παν. Πατρών: σύστημα ωρολογίου προγράμματος
& εξεταστικής. Υποψήφιο για επίσημη χρήση από το τμήμα — προτεραιότητα σε
ποιότητα, ασφάλεια, συντηρησιμότητα.

## Δομή & Stack

- `timetable/` — backend: Java 21 (OpenJDK Temurin), Spring Boot 3.5, JPA,
  Spring Security (JWT), PostgreSQL 16, Timefold Solver 1.11 (Community, Apache 2.0)
- `frontend/` — React 19 + TypeScript + Vite (dark theme `#080f1a`)
- Solver: `timetable/src/main/java/gr/upatras/ceid/timetable/solver/`
  - `CeidConstraintProvider` (εβδομαδιαίο) και `ExamConstraintProvider` (εξεταστική)
  - Κάθε `Lesson` = 1 ώρα (weekly) ή 1 εξέταση σε 3ωρο slot (exam)
  - **Δύο κόσμοι:** JPA entities (storage) vs solver POJOs (compute, flat snapshots)
- Υπάρχουν: **66 tests** (ConstraintVerifier + validation + util), GitHub Actions CI,
  Docker compose, backup scripts (`scripts/`), Swagger UI (`/swagger-ui.html`)
- Test accounts (dev μόνο): admin/admin123, teacher/teacher123, student/student123

## Εντολές (Windows)

Backend (`mvnw.cmd`, `pom.xml`) στο nested path `timetable\timetable\` — όλες οι
`mvnw.cmd` εντολές τρέχουν από εκεί.
- Build: `mvnw.cmd clean compile` · Tests: `mvnw.cmd test` (θέλει PostgreSQL up +
  env `DB_PASSWORD`) · Run: `mvnw.cmd spring-boot:run` (8080)
- Μόνο solver tests: `mvnw.cmd test -Dtest=*ConstraintProviderTest`

Frontend (από `frontend/`):
- `npm install` · `npx tsc --noEmit -p tsconfig.app.json` · `npx eslint src` ·
  `npm run build` · dev: `npm run dev` (5173)

Βάση: db `ceid_timetable`, user `ceid_admin`, password από env `DB_PASSWORD`.
Secrets ΜΟΝΟ σε `.env` (gitignored)/env vars. ΠΟΤΕ σε tracked αρχεία.

## Κανόνες εργασίας (ΑΠΑΡΑΒΑΤΟΙ)

1. **Ρυθμός εργασίας ανά ρίσκο (risk-based cadence):**
   - **HIGH-RISK** (solver, Flyway migrations, entities, snapshot/soft-delete,
     validation/transactions, security): ΕΝΑ task τη φορά → `mvnw.cmd test` ΟΛΑ
     πράσινα (+νέα) → conventional commit → 📝 thesis note → ΣΤΑΜΑΤΑ για ρητό «ΟΚ».
   - **LOW-RISK** (CRUD, UI strings, σχόλια, polish, mechanical refactors):
     checkpoint-batching — ομάδα συναφών αλλαγών → ΜΙΑ φορά verify
     (`npm run build` + `npx tsc --noEmit` ή/και `mvnw.cmd test`) → commit(s) ανά
     λογική μονάδα → ΣΤΑΜΑΤΑ για «ΟΚ».
   Πάντα: manual-approve edits (ποτέ auto-accept), commits ΧΩΡΙΣ Co-Authored-By,
   backup βάσης (`scripts/backup-db.ps1`) πριν από κάθε αλλαγή σχήματος/δεδομένων (#6).
2. Πριν από ΚΑΘΕ αλλαγή σε solver/validation: ConstraintVerifier baseline → αλλαγή →
   ξανά. Κάθε νέο/αλλαγμένο constraint = νέο test.
3. ΜΗΝ αλλάζεις βάρη/λογική υπαρχόντων constraints χωρίς ρητή έγκριση.
4. Ιεραρχία βαρών HARD: conflicts 1, required same-year 5, blocked slots 10.
   SOFT: sameYearSameDay 6, directionGroupA 5, A6 prefs 4, spread 3, teacher/day 2,
   tie-breakers 1. **(Νέοι κανόνες κατευθύνσεων — βλ. §Αρχιτεκτονική, ΧΡΕΙΑΖΟΝΤΑΙ
   έγκριση βαρών.)**
5. **Flyway ΕΝΕΡΓΟ (priority #1).** Από τη στιγμή που μπει: ΚΑΘΕ αλλαγή σχήματος =
   νέο versioned migration (`Vx__desc.sql`), ΠΟΤΕ edit παλιών. Έρχονται πολλές
   αλλαγές μοντέλου — γι' αυτό μπαίνει πρώτο.
6. SQL σε δεδομένα: transaction με ROLLBACK + backup (`scripts/backup-db.ps1`) πριν.
7. ΜΗΝ προσθέτεις προσωπικά δεδομένα (ονόματα/διαθεσιμότητες) σε κώδικα/tracked —
   μόνο στη βάση.
8. Γλώσσα: απαντήσεις ελληνικά, κώδικας/identifiers αγγλικά, UI strings ελληνικά.
9. Σε κάθε αλλαγή που αξίζει για το γραπτό: ετικέτα «📝 thesis note».
10. **Model tiers:** 🟣 Fable = solver/architecture/security · 🔵 Opus = bounded
    features/PDF/κείμενο · 🟢 Sonnet = CRUD/mechanical/polish. Ανέφερε προτεινόμενο tier.

## 🏗️ Αρχιτεκτονική-στόχος (ΕΓΚΕΚΡΙΜΕΝΕΣ αποφάσεις — invariants)

Αυτά κατευθύνουν όλη τη νέα δουλειά. Μην τα παραβιάζεις χωρίς ρητή αλλαγή εδώ.

1. **Snapshot-on-write + soft-delete.** Όταν δημιουργείται/τοποθετείται ανάθεση,
   αντιγράφονται denormalized τα display πεδία (course code+name, teacher names,
   room code, timeslot label) στη γραμμή. Τα προγράμματα render-άρονται από το
   **snapshot** → μένουν ανέπαφα σε μετέπειτα αλλαγές/διαγραφές master δεδομένων.
   Master entities: **soft-delete** (`active=false`), όχι hard delete αν υπάρχει
   ιστορική αναφορά. (Ίδια αρχή με τα solver POJOs.)
2. **`CourseOffering {course, curriculum: ΠΠΣ|ΝΠΣ, semester(nullable)}`** για multi-
   εξάμηνο: ένα μάθημα → πολλά offerings (π.χ. 1ο στο ΠΠΣ, 2ο στο ΝΠΣ). Επιλογής:
   `semester=null` + κατεύθυνση.
3. **`Direction {code: Κ1.., name, color}`** + Course↔Direction **many-to-many**
   (μάθημα σε >1 κατεύθυνση). Ο `sector` ΚΑΤΑΡΓΕΙΤΑΙ. Υποχρεωτικά: κατεύθυνση κενή/
   κλειδωμένη. **Νέοι solver κανόνες (🟣, χρειάζονται έγκριση βαρών):**
   `directionRequiredElectiveConflict` (μη-σύμπτωση υποχρ.-κατ'-επιλ. ίδιας
   κατεύθυνσης — ανάλογο requiredSameYear=5) + `directionGaps` (ελαχιστοποίηση κενών
   εντός κατεύθυνσης — ανάλογο spread=3).
4. **Γενικευμένο `Constraint {scope: WEEKLY|EXAM, targetType: TEACHER|ROOM|COURSE|
   GLOBAL, targetId, day, hour|range, state: PREFERRED|NEUTRAL|BLOCKED}`.** Default =
   PREFERRED. **Νέο NEUTRAL** (πορτοκαλί/γκρι). Επεξεργασία από frontend → DB →
   διάβασμα από solver. Αντικαθιστά/επεκτείνει TeacherConstraint/RoomConstraint με
   `scope`+`state`. `validateAssignment` επιστρέφει **structured reason** (π.χ.
   `TEACHER_BLOCKED`) για κόκκινο error μήνυμα στο UI.

## Πηγή αλήθειας δεδομένων

`docs/official/` = επίσημα έγγραφα τμήματος (ΠΠΣ μαθήματα, Κ1-Κ6 κατευθύνσεις) —
υπερισχύουν του κώδικα. `docs/private/` (gitignored) = φόρμες διδασκόντων με
προσωπικά δεδομένα — ΜΗΝ τα αντιγράφεις σε tracked αρχεία.

## Baseline αναφοράς

Weekly solver στο πλήρες dataset (118 μαθήματα, 14+1 αίθουσες): 266/266 lessons,
hardScore 0, ~30s. Αν μετά από αλλαγή δεν πιάνει 0, κάτι χάλασε. (Ορισμένα co-taught
σε αποθηκευμένα timetables δεν πιάνουν 0 λόγω λαθών δεδομένων, όχι regression — λύνεται
στο task E.)

## 🗺️ Roadmap (νέο, μετά τη συνάντηση) — βλ. `Plano-Frontend-Rework.md` για λεπτομέρεια

**Τρέχουσα εστίαση: λειτουργίες δημιουργίας προγράμματος + έναρξη γραπτού.**
Σειρά κατά εξάρτηση (κάθε φάση ξεκλειδώνει την επόμενη):

- **Φ0 Guardrails:** Flyway (#1) · safe validation · backups/ROLLBACK · Γ-UI-1 rename
  `CPSolver`→Timefold (UI strings + 3 σχόλια).
- **Φ1 Μοντέλο (🟣):** Direction(+2 κανόνες) · CourseOffering · CourseTeacher wire ·
  γενικευμένο Constraint(+neutral) · snapshot+soft-delete · task E (registry→DB).
- **Φ2 CRUD frontend (🟢🔵):** course checkboxes (teacher/sem/dir) · Directions/
  Constraints/TimeSlots pages · soft-delete UI. (Υπάρχουν ήδη Courses/Rooms/Teachers/
  Users → επέκταση.)
- **Φ3 Redesign προγραμμάτων (🔵):** full-width + controls κάτω · warnings table κάτω ·
  κάρτες-κωδικοί + hover/click info · no-scroll 9-21×Δευ-Παρ · φίλτρα έτος/εξάμηνο/
  κατεύθυνση (auto-refresh) · κάρτες εξαμήνου πάνω · suggested-pos κάτω · exam parity.
- **Φ4 Περιορισμοί UX (🔵):** neutral · μαζική επιλογή · error μηνύματα (όλοι, 2 scopes) ·
  (stretch) include-which-constraints.
- **Φ5 PDF overhaul (🔵):** print dialog+checkboxes · 4 κατηγορίες σελίδων (εξάμηνο/
  αίθουσα/καθηγητής/κατεύθυνση) · πλήρη ονόματα+χρώμα · κόκκινο Χ μπλοκαρισμένων.
- **Φ6 Εξεταστική:** ΕΠΙ ΔΙΠΛΩΜΑΤΙ (load-all) · compressed grid.
- **Stretch:** Φ7 public StudentView (=A8) · Φ8 αναπληρώσεις · Φ9 A7 import.
- **Ongoing:** Δ-phase γραπτό · ConstraintVerifier νέων κανόνων · incremental C refactor
  (TimetableController ~2998γρ → ValidationService/PlacementService/ExamSlotService).

## Resolved (ιστορικό — συνοπτικά)

- ✅ A: NewEarino «6 errors / 0 hard» — ΑΛΗΘΙΝΕΣ co-taught συγκρούσεις λόγω **λαθών
  δεδομένων** (λάθος registry, phantom tut/lab ώρες, λάθος attribution), ΟΧΙ δομική
  αδυναμία· διόρθωση ανήκει στο task E. Fixed δευτερεύον double-count bug (+test).
- ✅ B: A6 tests. ✅ D: A12 αργίες/εξαιρέσεις (`util/GreekHolidays` + `excludedDates`).

## Εργαλεία/βιβλιοθήκες (για κεφ. 2 γραπτού)

Backend: Spring Boot 3.5 (Apache 2.0), Hibernate/JPA, PostgreSQL 16 + JDBC,
**Timefold Solver 1.11 Community** (Apache 2.0, constraint streams), Lombok (MIT),
jjwt (Apache 2.0), springdoc-openapi/Swagger (Apache 2.0), Maven, JUnit +
ConstraintVerifier. **ΝΕΟ: Flyway** (Community, Apache 2.0).
Frontend: React 19/TS/Vite (MIT/Apache), react-router-dom, axios (MIT), ESLint,
native HTML5 Drag&Drop, `window.print()`+print CSS, custom iCal export.
**PDF overhaul — ΑΠΟΦΑΣΗ ΕΚΚΡΕΜΕΙ:** print-CSS+dedicated print view (0 deps, default)
ή pdfmake/@react-pdf/renderer (MIT) αν θέλουμε «Download .pdf» αρχείο.
Tooling: Docker/compose, GitHub Actions, Git, Claude Code (AI pair-programmer).

## 📋 BACKLOG — Παρατηρημένα προς υλοποίηση (deferred)

**[BL-1] Solver persistence atomicity (B2 από Φ0/0.2) — HIGH PRIORITY**
Σημείο: `SolverService.solve()` → `saveSolution()` (private, self-invocation).
Πρόβλημα: `deleteAll(old)` + loop `save(new)` + `save(score)` ΔΕΝ είναι ατομικά·
exception στο persist → παλιές αναθέσεις σβησμένες + μερικές νέες.
Γιατί αναβλήθηκε: σκέτο `@Transactional` δεν ενεργοποιείται (private
self-invocation)· δεν τυλίγουμε τα ~30s solve σε tx· το early SOLVING save
θέλει χωριστό commit για το UI.
Λύση: extract της persistence σε ξεχωριστό transactional bean/method
(boundary ΜΟΝΟ γύρω από τα writes, ΟΧΙ γύρω από το solve).
Tier: 🔴 Fable. Τοποθέτηση: πιθανότατα ΜΕΣΑ στη Φ1 (το snapshot-on-write
αναδιαμορφώνει ούτως ή άλλως το solver persistence).

**[BL-2] CourseController.delete error-handling**
500 σε FK violation αντί για καθαρό 4xx (409/400). Atomicity ΟΚ (single
write)· μόνο error-handling/UX. Tier: 🟢 Sonnet (polish).

**[BL-3] Frontend ESLint cleanup**
9× `@typescript-eslint/no-explicit-any` (ExamTimetable.tsx, Courses.tsx,
TimetableSelector.tsx, MoveAssignmentModal.tsx) + 1×
`react-refresh/only-export-components`. ΔΕΝ μπλοκάρουν το CI (warnings), αλλά
σήμα ποιότητας για το γραπτό. Tier: 🟢 Sonnet. Πότε: πριν την παράδοση.

**[BL-4] CI actions → Node 24**
`actions/checkout@v4` + `setup-node@v4` + `setup-java@v4` σε deprecated Node 20·
ο runner ήδη σπρώχνει Node 24. Λύση: bump σε `@v5`.
Tier: 🟢 Sonnet/manual. Commit: `ci: bump actions to Node 24`.

**[BL-5] Inactive teacher που ακόμα διδάσκει (από S1)**
Το soft-delete (S1) απενεργοποιεί καθηγητή που ΕΧΕΙ CourseTeacher links (η
κανονική περίπτωση deactivate), αλλά ο solver εξακολουθεί να αντλεί `teacherKeys`
από αυτά τα links → ένας inactive teacher συνεχίζει να επιβάλλει teacher-conflict/
availability constraints. Σκόπιμα εκτός scope S1. Λύση: είτε φιλτράρισμα inactive
στο `buildTeacherKeyMap`/availability reads, είτε «reassign-before-deactivate» UX
(ο admin ανακατανέμει τα μαθήματα πριν απενεργοποιήσει). Τοποθέτηση: S4 (γενικευμένο
Constraint) ή νωρίτερα αν χρειαστεί. Tier: 🟣/🔵.
