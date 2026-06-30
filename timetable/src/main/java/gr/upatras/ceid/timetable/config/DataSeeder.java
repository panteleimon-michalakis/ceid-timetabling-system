package gr.upatras.ceid.timetable.config;

import gr.upatras.ceid.timetable.entity.*;
import gr.upatras.ceid.timetable.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.LocalDateTime;

/**
 * Idempotent DataSeeder — ασφαλές να τρέξει πολλές φορές.
 * Κάνει upsert (insert αν δεν υπάρχει, skip αν υπάρχει) για κάθε entity.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final RoomRepository roomRepo;
    private final CourseRepository courseRepo;
    private final TimeSlotRepository timeSlotRepo;
    private final UserRepository userRepo;

    public DataSeeder(RoomRepository roomRepo, CourseRepository courseRepo,
                      TimeSlotRepository timeSlotRepo, UserRepository userRepo) {
        this.roomRepo = roomRepo;
        this.courseRepo = courseRepo;
        this.timeSlotRepo = timeSlotRepo;
        this.userRepo = userRepo;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> DataSeeder running (idempotent)...");
        seedRooms();
        seedTimeSlots();
        seedUsers();
        seedRequiredCourses();
        seedWinterElectives();
        seedSpringElectives();
        seedGPCourses();
        System.out.println(">>> Seeding complete!");
        System.out.println("    Rooms: " + roomRepo.count());
        System.out.println("    Courses: " + courseRepo.count());
        System.out.println("    TimeSlots: " + timeSlotRepo.count());
        System.out.println("    Users: " + userRepo.count());
    }

    // -----------------------------------------------------------------------
    // Rooms — upsert by code
    // -----------------------------------------------------------------------
    private void seedRooms() {
        upsertRoom("Αμφιθέατρο Γ", "Γ", 244, Room.RoomType.AMPHITHEATER, true, false, true, true, "Κύρια αίθουσα 1ου έτους + υποχρεωτικά");
        upsertRoom("Αμφιθέατρο Β", "Β", 238, Room.RoomType.AMPHITHEATER, true, false, true, true, "Υποχρεωτικά μαθήματα");
        upsertRoom("Αίθουσα Δ1", "Δ1", 110, Room.RoomType.CLASSROOM, true, false, true, true, "Μαθήματα επιλογής");
        upsertRoom("Αίθουσα Δ2", "Δ2", 110, Room.RoomType.CLASSROOM, true, false, true, true, "Μαθήματα επιλογής");
        upsertRoom("Αίθουσα Ε1", "Ε1", 64, Room.RoomType.CLASSROOM, true, false, true, true, "Μαθήματα επιλογής μικρά τμήματα");
        upsertRoom("Αίθουσα Ε2", "Ε2", 64, Room.RoomType.CLASSROOM, true, false, true, true, "Μαθήματα επιλογής μικρά τμήματα");
        upsertRoom("Υπολογιστικό Κέντρο", "ΥΚ", 50, Room.RoomType.LAB, true, true, false, true, "Εργαστήριο Η/Υ");
        upsertRoom("Εργαστήριο ΗΛ3", "ΗΛ3", 50, Room.RoomType.LAB, true, true, false, true, "Εργαστηριακή αίθουσα από aSc Timetables");
        upsertRoom("Εργαστήριο ΗΛ4", "ΗΛ4", 50, Room.RoomType.LAB, true, true, false, true, "Εργαστηριακή αίθουσα από aSc Timetables");
        upsertRoom("Εργαστήριο ΗΛ5", "ΗΛ5", 50, Room.RoomType.LAB, true, true, false, true, "Εργαστηριακή αίθουσα από aSc Timetables");
        upsertRoom("Εργαστήριο ΗΛ7", "ΗΛ7", 50, Room.RoomType.LAB, true, true, false, true, "Εργαστηριακή αίθουσα από aSc Timetables");
        upsertRoom("Αίθουσα Η/Υ - Τ&ΤΠ", "ΤΤΠ", 50, Room.RoomType.LAB, true, true, false, true, "Εργαστηριακή αίθουσα Η/Υ");
        upsertRoom("Αίθουσα Συνεδριάσεων", "ΑΣ", 30, Room.RoomType.MEETING_ROOM, true, false, true, false, "Κυρίως για συνεδρίες, εξεταστική κατ' εξαίρεση");
        upsertRoom("Αίθουσα Φυσικής-Εξεταστικής", "ΑΦΕ", 200, Room.RoomType.EXAM_HALL, false, false, true, false, "Μόνο εξεταστική");
    }

    private void upsertRoom(String name, String code, int capacity, Room.RoomType type,
                             boolean projector, boolean computers,
                             boolean forExams, boolean forSemester, String notes) {
        if (roomRepo.findByCode(code).isPresent()) return; // skip αν υπάρχει
        roomRepo.save(Room.builder()
                .name(name).code(code).capacity(capacity).roomType(type)
                .hasProjector(projector).hasComputers(computers)
                .availableForExams(forExams).availableForSemester(forSemester)
                .notes(notes).build());
    }

    // -----------------------------------------------------------------------
    // TimeSlots — upsert by slotType + dayOfWeek + startTime
    // -----------------------------------------------------------------------
    private void seedTimeSlots() {
        DayOfWeek[] days = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY};
        for (DayOfWeek day : days) {
            for (int hour = 9; hour <= 20; hour++) {
                LocalTime start = LocalTime.of(hour, 0);
                boolean exists = timeSlotRepo
                        .findBySlotTypeAndDayOfWeekAndStartTime(TimeSlot.SlotType.SEMESTER, day, start)
                        .isPresent();
                if (!exists) {
                    timeSlotRepo.save(TimeSlot.builder()
                            .dayOfWeek(day)
                            .startTime(start)
                            .endTime(LocalTime.of(hour + 1, 0))
                            .slotType(TimeSlot.SlotType.SEMESTER)
                            .build());
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Users — upsert by username
    // -----------------------------------------------------------------------
    private void seedUsers() {
        upsertUser("admin", "Διαχειριστής CEID", "admin@ceid.upatras.gr", User.Role.ADMIN, null);
        upsertUser("tsichlas", "Κωνσταντίνος Τσίχλας", "tsichlas@ceid.upatras.gr", User.Role.TEACHER, "ΕΘ");
        upsertUser("megalooikonomou", "Βασίλειος Μεγαλοοικονόμου", "vasilis@ceid.upatras.gr", User.Role.TEACHER, "ΛΥ");
        upsertUser("makris", "Χρήστος Μακρής", "makri@ceid.upatras.gr", User.Role.TEACHER, "ΛΥ");
        upsertUser("vergos", "Χαρίδημος Βέργος", "vergos@ceid.upatras.gr", User.Role.TEACHER, "ΥΑ");
    }

    private void upsertUser(String username, String fullName, String email, User.Role role, String sector) {
        if (userRepo.findByUsername(username).isPresent()) return;
        userRepo.save(User.builder()
                .username(username).fullName(fullName).email(email)
                .role(role).sector(sector).active(true)
                .createdAt(LocalDateTime.now()).build());
    }

    // -----------------------------------------------------------------------
    // Courses — upsert by code
    // -----------------------------------------------------------------------
    private void seedRequiredCourses() {
        req("CEID_22Y101","Διακριτά Μαθηματικά",3,2,3,2,0,7,"ΕΘ","FALL",244,"Κ. Τσίχλας, Ευ. Παπαϊωάννου");
        req("CEID_22Y102","Γραμμική Άλγεβρα",1,1,3,2,1,7,"ΕΘ","FALL",244,"Ε. Γαλλόπουλος, Ε. Στεφανόπουλος");
        req("CEID_22Y103","Εισαγωγή στον Προγραμματισμό",1,1,3,2,2,9,"ΛΥ","FALL",244,"Χ. Μακρής, Σ. Σιούτας, Α. Ηλίας (ΕΔΙΠ)");
        req("CEID_22Y104","Βασικές Αρχές Οργάνωσης και Λειτουργίας Υπολογιστικών Συστημάτων",1,1,3,1,0,7,"ΥΑ","FALL",244,"Χ. Βέργος");

        req("CEID_22Y105","Γενικά Μαθηματικά Ι",2,1,3,2,1,7,"ΕΘ","SPRING",244,"Α. Ανδρικόπουλος");
        req("CEID_23Y106","Αντικειμενοστρεφής Προγραμματισμός",2,1,3,2,2,9,"ΛΥ","SPRING",244,"Π. Χατζηδούκας, Α. Κομνηνός, Εντεταλμένος Διδάσκων");
        req("CEID_23Y107","Λογική Σχεδίαση",2,1,3,1,0,7,"ΥΑ","SPRING",244,"Γ. Ζερβάκης, Εντεταλμένος Διδάσκων");
        req("CEID_22Y108","Ηλεκτρικά Κυκλώματα",2,1,3,1,2,7,"ΥΑ","SPRING",238,"Χρ. Χρηστίδης, Εντεταλμένος Διδάσκων");

        req("CEID_23Y204","Πιθανότητες και Αρχές Στατιστικής",3,2,3,2,0,6,"ΕΘ","FALL",238,"Σ. Νικολετσέας");
        req("CEID_23Y205","Εισαγωγή στους Αλγόριθμους",3,2,3,1,2,6,"ΕΘ","FALL",244,"Χρ. Ζαρολιάγκης, Στ. Αθανασόπουλος (ΕΔΙΠ), Ι. Βασιλόπουλος (ΕΔΙΠ)");
        req("CEID_23Y202","Θεωρία Γραφημάτων και Εφαρμογές",3,2,3,1,1,6,"ΕΘ","FALL",238,"Σ. Κοσμαδάκης");
        req("CEID_23Y201","Γενικά Μαθηματικά ΙΙ",3,2,3,2,1,6,"ΕΘ","FALL",238,"Α. Ανδρικόπουλος");
        req("CEID_23Y203","Αρχιτεκτονική Υπολογιστών",3,2,3,1,0,4,"ΥΑ","FALL",244,"Γ. Παπαδημητρίου");
        req("CEID_23Y211","Εργαστήριο Λογικού Σχεδιασμού",3,2,0,0,3,2,"ΥΑ","FALL",50,"Μ.-Ε. Δούναβη (ΕΔΙΠ), ή/και Εντεταλμένος Διδάσκων");

        req("CEID_23Y207","Θεωρία Σημάτων και Συστημάτων",4,2,3,2,0,6,"ΥΑ","SPRING",238,"Εμμ. Ψαράκης");
        req("CEID_23Y208","Αναλογικά και Ψηφιακά Ηλεκτρονικά",4,2,3,1,0,4,"ΥΑ","SPRING",238,"Ε. Δερματάς");
        req("CEID_23Y206","Αρχές Γλωσσών Προγραμματισμού και Μεταφραστών",4,2,3,1,2,6,"ΛΥ","SPRING",244,"Ι. Γαροφαλάκης, Σ. Σιούτας, Π. Χατζηδούκας, Ι. Βασιλόπουλος (ΕΔΙΠ)");
        req("CEID_23Y209","Αριθμητική Ανάλυση και Περιβάλλοντα Υλοποίησης",4,2,3,1,2,6,"ΛΥ/ΕΘ","SPRING",244,"Εντεταλμένος Διδάσκων");
        req("CEID_23Y210","Δομές Δεδομένων",4,2,3,1,2,6,"ΛΥ","SPRING",244,"Χ. Μακρής, Σ. Σιούτας");
        req("CEID_23Y212","Εργαστήριο Αρχιτεκτονικής Υπολογιστών",4,2,0,0,3,2,"ΥΑ","SPRING",50,"Γ. Παπαδημητρίου, Β. Παπαϊωάννου (ΕΔΙΠ), Εντεταλμένος Διδάσκων");

        req("CEID_24Y361","Συστήματα Μικροϋπολογιστών",5,3,3,1,0,5,"ΥΑ","FALL",244,"Ν. Σκλάβος");
        req("CEID_24Y381","Ψηφιακή Επεξεργασία Σημάτων",5,3,3,1,2,5,"ΥΑ","FALL",238,"Εμμ. Ψαράκης, Δ. Κοσμόπουλος");
        req("CEID_24Y303","Εργαστήριο Αναλογικών & Ψηφιακών Ηλεκτρονικών",5,3,0,0,3,2,"ΥΑ","FALL",50,"Γ.Π. Οικονόμου (ΕΔΙΠ)");
        req("CEID_24Y351","Τεχνητή Νοημοσύνη",5,3,3,2,1,6,"ΛΥ","FALL",244,"Β. Μεγαλοοικονόμου, Δ. Κοσμόπουλος, Δ. Κουτσομητρόπουλος (ΕΔΙΠ), Εντεταλμένος Διδάσκων");
        req("CEID_24Y334","Βάσεις Δεδομένων",5,3,2,1,3,6,"ΛΥ","FALL",244,"Β. Μεγαλοοικονόμου, Ε. Βογιατζάκη (ΕΔΙΠ), Ι. Βασιλόπουλος (ΕΔΙΠ)");
        req("CEID_24Y330","Λειτουργικά Συστήματα",5,3,3,1,2,6,"ΛΥ","FALL",244,"Χρ. Μακρής, Σ. Σιούτας, Π. Χατζηδούκας, Α. Ηλίας (ΕΔΙΠ)");

        req("CEID_24Y387","Δίκτυα Υπολογιστών",6,3,3,1,2,6,"ΥΑ","SPRING",244,"Κ. Βλάχος");
        req("CEID_24Y302","Βασικές Έννοιες Συστημάτων Επικοινωνίας",6,3,3,1,1,6,"ΛΥ","SPRING",238,"Κ. Μπερμπερίδης");
        req("CEID_24Y332","Τεχνολογία Λογισμικού",6,3,2,2,2,6,"ΛΥ","SPRING",244,"Μ. Ξένος, Α. Ηλίας (ΕΔΙΠ), Ι. Βασιλόπουλος (ΕΔΙΠ)");
        req("CEID_24Y338","Προγραμματισμός και Συστήματα στον Παγκόσμιο Ιστό",6,3,2,2,2,6,"ΛΥ","SPRING",244,"Ι. Γαροφαλάκης, Α. Κομνηνός, Ε. Βογιατζάκη (ΕΔΙΠ), Εντεταλμένος Διδάσκων");
        req("CEID_24Y301","Θεωρία Υπολογισμού και Πολυπλοκότητα",6,3,3,3,0,6,"ΕΘ","SPRING",244,"Χρ. Κακλαμάνης, Ευ. Παπαϊωάννου");

        req("CEID_25Y401","Προγραμματισμός Συστημάτων",7,4,2,1,2,5,"ΥΑ/ΛΥ","FALL",238,"Γ. Παπαδημητρίου, Π. Χατζηδούκας, Β. Παπαϊωάννου (ΕΔΙΠ)");

        System.out.println("    -> Required courses seeded");
    }

    private void seedWinterElectives() {
        elec("CEID_NE4117","Κατανεμημένα Συστήματα",7,4,2,1,2,5,"ΕΘ","FALL",80,"Σ. Κοντογιάννης");
        elec("CEID_NE4157","Δίκτυα Δημόσιας Χρήσης και Διασύνδεση Δικτύων",7,4,2,2,1,5,"ΕΘ","FALL",60,"Ευ. Παπαϊωάννου, Εντεταλμένος Διδάσκων");
        elec("CEID_NE5057","Αλγόριθμοι και Βελτιστοποίηση",7,4,2,2,1,5,"ΕΘ","FALL",60,"Χρ. Ζαρολιάγκης, Σ. Κοντογιάννης");
        elec("CEID_NE5017","Πιθανοτικές Τεχνικές και Τυχαιοκρατικοί Αλγόριθμοι",7,4,2,2,1,5,"ΕΘ","FALL",40,"Σ. Νικολετσέας");
        elec("CEID_NE5127","Αλγόριθμοι Επικοινωνιών",7,4,2,2,1,5,"ΕΘ","FALL",40,"Χρ. Κακλαμάνης, Ευ. Παπαϊωάννου, Εντεταλμένος Διδάσκων ή ΑΑΔΕ");
        elec("CEID_NE5237","Στατιστικές Μέθοδοι Μηχανικής Μάθησης",7,4,2,1,2,5,"ΕΘ","FALL",80,"Δ. Κοσμόπουλος, Σ. Νικολετσέας");
        elec("CEID_NE5038","Σημασιολογία στην Επιστήμη των Υπολογιστών",7,4,2,3,0,5,"ΕΘ","FALL",30,"Σ. Κοσμαδάκης, Χρ. Κακλαμάνης");
        elec("CEID_NE565","Ανάπτυξη Βιντεοπαιγνιδιών",7,4,2,1,2,5,"ΕΘ","FALL",80,"Κ. Τσίχλας");
        elec("CEID_NE4128","Παράλληλοι Αλγόριθμοι",7,4,2,2,1,5,"ΕΘ","FALL",40,"Χρ. Κακλαμάνης, Ευ. Παπαϊωάννου");
        elec("CEID_NE1411","Τεχνολογίες και Αλγόριθμοι Αποκεντρωμένων Δεδομένων",7,4,2,1,2,5,"ΛΥ","FALL",50,"Σ. Σιούτας, Α. Κομνηνός, Ι. Βασιλόπουλος (ΕΔΙΠ)");
        elec("CEID_NE4338","Πολυδιάστατες Δομές Δεδομένων",7,4,2,1,2,5,"ΛΥ","FALL",40,"Σ. Σιούτας, Κ. Τσίχλας");
        elec("CEID_NE4547","Τεχνικές Εκτίμησης Υπολογιστικών Συστημάτων και Δικτύων",7,4,2,2,1,5,"ΛΥ","FALL",40,"Ι. Γαροφαλάκης");
        elec("CEID_NE5367","Προηγμένα Πληροφοριακά Συστήματα",7,4,2,1,2,5,"ΛΥ","FALL",50,"Μ. Ξένος, Α. Ηλίας (ΕΔΙΠ)");
        elec("CEID_NE5407","Λογισμικό & Προγραμματισμός Συστημάτων Υψηλής Επίδοσης",7,4,2,1,2,5,"ΛΥ","FALL",40,"Π. Χατζηδούκας");
        elec("CEID_NE5597","Ανάκτηση Πληροφορίας",7,4,2,1,2,5,"ΛΥ","FALL",50,"Χ. Μακρής");
        elec("CEID_NE584","e-Επιχειρείν",7,4,2,1,2,5,"ΛΥ","FALL",40,"Ι. Γαροφαλάκης, Μ. Ρήγκου, Ε. Βογιατζάκη (ΕΔΙΠ)");
        elec("CEID_NE5577","Ποιότητα Λογισμικού",7,4,2,1,2,5,"ΛΥ","FALL",80,"Μ. Ξένος");
        elec("CEID_25EX598","Αλληλεπίδραση Ανθρώπου-Υπολογιστή",7,4,2,1,2,5,"ΛΥ","FALL",80,"Μ. Ξένος, Α. Κομνηνός, Ε. Βογιατζάκη (ΕΔΙΠ)");
        elec("CEID_NE575","Υλοποιήσεις και Εφαρμογές Ασφάλειας Δικτύων",7,4,2,0,3,5,"ΥΑ","FALL",40,"Δ. Σερπάνος, Κ. Βλάχος");
        elec("CEID_NE4648","Εισαγωγή σε VLSI",7,4,3,0,2,5,"ΥΑ","FALL",30,"Εντεταλμένος Διδάσκων (ΑΑΔΕ)");
        elec("CEID_NE574","Οπτικά Δίκτυα Επικοινωνιών",7,4,2,1,2,5,"ΥΑ","FALL",30,"Κ. Βλάχος");
        elec("CEID_NE5678","Σχεδιασμός Συστημάτων Ειδικού Σκοπού",7,4,2,1,0,5,"ΥΑ","FALL",30,"Χ. Βέργος");
        elec("CEID_NE471","Όραση Υπολογιστών & Γραφικά",7,4,2,1,2,5,"ΥΑ","FALL",60,"Εμμ. Ψαράκης");
        elec("CEID_NE4847","Στατιστική Επεξεργασία Σήματος και Θέματα Μηχανικής Μάθησης",7,4,2,1,2,5,"ΥΑ","FALL",60,"Εντεταλμένος Διδάσκων (ΑΑΔΕ)");
        elec("CEID_NE590","Κυβερνοασφάλεια",7,4,2,1,2,5,"ΥΑ","FALL",60,"Ν. Σκλάβος");
        elec("CEID_NE592","Βασικές Αρχές Δικτύων Κινητών Επικοινωνιών",7,4,3,0,2,5,"ΥΑ","FALL",40,"Χρ. Βερυκούκης");
        elec("CEID_NE594","Αρθρωτά Κβαντικά Συστήματα",7,4,4,1,0,5,"ΥΑ","FALL",20,"Χ. Χρηστίδης");
        elec("CEID_NE489","Ευφυείς Τεχνολογίες Ασύρματων και Κινητών Επικοινωνιών",7,4,2,1,2,5,"ΥΑ","FALL",40,"Κ. Μπερμπερίδης");
        elec("CEID_NE579","Εφαρμογές της Ψηφιακής Επεξεργασίας Σημάτων",7,4,2,1,2,5,"ΥΑ","FALL",30,"Εμμ. Ψαράκης");
        ext("CEID_NE9DE","Εισαγωγή στη Διοίκηση και Οργάνωση Επιχειρήσεων για Μηχανικούς και Επιστήμονες",7,4,5,"FALL",30,"Εντεταλμένος Διδάσκων");
        ext("CEID_E9OE","Εισαγωγή στα Οικονομικά",7,4,5,"FALL",35,"Ν. Χατζησταμούλου");
        ext("CEID_NSM02","Ασφάλεια Υπολογιστών και Δικτύων",7,4,5,"FALL",60,"Δ. Σερπάνος, Κ. Βλάχος");
        ext("CEID_NSM06","Ηλεκτροακουστική",7,4,5,"FALL",20,"Ι. Μουρτζόπουλος, Π. Χατζηαντωνίου, Ε.ΔΙ.Π.");
        ext("CEID_NSM05","Διαδραστικές Τεχνολογίες",7,4,5,"FALL",30,"Ν. Αβούρης, Χ. Σιντόρης, Κ. Μουστάκας");
        System.out.println("    -> Winter electives seeded");
    }

    private void seedSpringElectives() {
        elec("CEID_NE4017","Μαθηματική Λογική και Εφαρμογές της",8,4,3,2,0,5,"ΕΘ","SPRING",30,"Σ. Κοσμαδάκης");
        elec("CEID_NE4168","Κρυπτογραφία",8,4,2,2,1,5,"ΕΘ","SPRING",60,"Χρ. Κακλαμάνης, Ευ. Παπαϊωάννου, Εντεταλμένος Διδάσκων ή ΑΑΔΕ");
        elec("CEID_NE5218","Υπολογιστική Νοημοσύνη",8,4,2,1,2,5,"ΕΘ","SPRING",50,"Χρ. Ζαρολιάγκης, Δ. Κουτσομητρόπουλος (ΕΔΙΠ)");
        elec("CEID_NE5078","Τεχνολογίες Υλοποίησης Αλγορίθμων",8,4,2,1,2,5,"ΕΘ","SPRING",30,"Χρ. Ζαρολιάγκης");
        elec("CEID_NE5168","Ευρυζωνικές Τεχνολογίες",8,4,2,2,1,5,"ΕΘ","SPRING",40,"Ευ. Παπαϊωάννου, Χρ. Κακλαμάνης");
        elec("CEID_NE520","Αλγόριθμοι και Εφαρμογές ΤΝ για το Διαδίκτυο των Αντικειμένων",8,4,2,1,2,5,"ΕΘ","SPRING",40,"Σ. Νικολετσέας");
        elec("CEID_NE509","Αλγοριθμική Θεωρία Παιγνίων",8,4,2,2,1,5,"ΕΘ","SPRING",30,"Σ. Κοντογιάννης");
        elec("CEID_NE5288","Ειδικά Θέματα Υπολογιστικής Λογικής",8,4,2,2,1,5,"ΕΘ","SPRING",20,"Σ. Κοσμαδάκης, Χρ. Κακλαμάνης");
        elec("CEID_24EE594","Αλγοριθμικές Τεχνικές Επιστήμης Δεδομένων",8,4,2,2,1,5,"ΕΘ","SPRING",50,"Χρ. Ζαρολιάγκης, Σ. Κοντογιάννης");
        elec("CEID_25EE605","Όρια Υπολογισμού και Αλγοριθμικές Στρατηγικές Επίλυσης Προβλημάτων",8,4,2,2,1,5,"ΕΘ","SPRING",40,"Κ. Τσίχλας");
        elec("CEID_25EE606","Τοπολογική Ανάλυση Δεδομένων",8,4,2,2,1,5,"ΕΘ","SPRING",20,"Α. Ανδρικόπουλος");
        elec("CEID_NE4348","Συστήματα Διαχείρισης Μεγάλων Δεδομένων",8,4,2,1,2,5,"ΛΥ","SPRING",60,"Β. Μεγαλοοικονόμου, Εντεταλμένος Διδάσκων");
        elec("CEID_NE562","Εξόρυξη Δεδομένων και Μηχανική Μάθηση",8,4,2,1,2,5,"ΛΥ","SPRING",80,"Χ. Μακρής, Β. Μεγαλοοικονόμου");
        elec("CEID_NE5358","Εισαγωγή στα Πληροφοριακά Συστήματα",8,4,2,1,2,5,"ΛΥ","SPRING",40,"Μ. Ξένος, Ι. Βασιλόπουλος (ΕΔΙΠ), Α. Ηλίας (ΕΔΙΠ)");
        elec("CEID_NE548","Εισαγωγή στη Βιοπληροφορική",8,4,2,1,2,5,"ΛΥ","SPRING",30,"Χ. Μακρής, Β. Μεγαλοοικονόμου");
        elec("CEID_NE5908","Κοινωνικές και Νομικές Πλευρές της Τεχνολογίας",8,4,2,1,2,5,"ΛΥ","SPRING",40,"Ι. Γαροφαλάκης");
        elec("CEID_NE576","Διάχυτος Υπολογισμός",8,4,2,1,2,5,"ΛΥ","SPRING",30,"Α. Κομνηνός");
        elec("CEID_24EE595","Διαχείριση Έργων Λογισμικού και Ανάπτυξη με Ευέλικτες Μεθόδους",8,4,2,1,2,5,"ΛΥ","SPRING",40,"Μ. Ξένος, Α. Ηλίας (ΕΔΙΠ)");
        elec("CEID_24EE596","Μέθοδοι Μητρώων και Υπολογιστικά Εργαλεία στην Επιστήμη Δεδομένων",8,4,2,2,1,5,"ΛΥ","SPRING",20,"Ε. Γαλλόπουλος");
        elec("CEID_25EE607","Παράλληλη Επεξεργασία",8,4,2,1,2,5,"ΛΥ","SPRING",60,"Π. Χατζηδούκας, Ε. Δερματάς");
        elec("CEID_NE593","Επεξεργασία Σημάτων, Θεωρία Γραφημάτων και Μάθηση",8,4,2,1,2,5,"ΥΑ","SPRING",30,"Σ. Κοσμαδάκης, Δ. Κοσμόπουλος, Εμμ. Ψαράκης");
        elec("CEID_NE4617","Προχωρημένα Θέματα Αρχιτεκτονικής Υπολογιστών",8,4,2,1,2,5,"ΥΑ","SPRING",30,"Γ. Παπαδημητρίου");
        elec("CEID_NE4658","Σχεδίαση Συστημάτων με Χρήση Υπολογιστών (CAD)",8,4,2,0,4,5,"ΥΑ","SPRING",20,"Χ. Βέργος");
        elec("CEID_NE4828","Επεξεργασία και Ανάλυση Εικόνας",8,4,2,1,2,5,"ΥΑ","SPRING",40,"Κ. Μπερμπερίδης");
        elec("CEID_NE5668","Ειδικά Θέματα Σχεδίασης Ψηφιακών Συστημάτων",8,4,2,1,2,5,"ΥΑ","SPRING",20,"Εντεταλμένος Διδάσκων");
        elec("CEID_NE5647","Σχεδιασμός Συστημάτων VLSI",8,4,3,0,2,5,"ΥΑ","SPRING",20,"Γ. Ζερβάκης");
        elec("CEID_NE577","Αρχιτεκτονικές Δικτύων Επόμενης Γενιάς, Τεχνολογίες και Εφαρμογές",8,4,3,0,2,5,"ΥΑ","SPRING",30,"Χρ. Βερυκούκης");
        elec("CEID_NE588","Ενσωματωμένα Συστήματα",8,4,2,0,3,5,"ΥΑ","SPRING",40,"Ν. Σκλάβος");
        elec("CEID_NE591","Ασφάλεια σε Υλικό",8,4,3,0,2,5,"ΥΑ","SPRING",30,"Ν. Σκλάβος");
        elec("CEID_25EE608","Αρχές Ψηφιακού Ελέγχου",8,4,2,1,2,5,"ΥΑ","SPRING",30,"Ε. Δερματάς");
        elec("CEID_25EE609","Επεξεργασία και Ανάλυση Video",8,4,3,0,2,5,"ΥΑ","SPRING",30,"Δ. Κοσμόπουλος");
        ext("CEID_NSM04","Ρομποτική",8,4,5,"SPRING",40,"Γ. Νικολακόπουλος, Π. Κουστουμπαρδής (ΕΔΙΠ), Ε. Δερματάς");
        ext("CEID_NSM07","Οπτικές Επικοινωνίες",8,4,5,"SPRING",20,"Ι. Τόμκος");
        System.out.println("    -> Spring electives seeded");
    }

    private void seedGPCourses() {
        gp("CEID_GP75","Νεότερη Ηθική Φιλοσοφία",7,4,3,"FALL",20,"Α. Μιχαλάκης");
        gp("CEID_GP37","Φιλοσοφία του Νου",7,4,3,"FALL",20,"Κ. Παγωνδιώτης");
        gp("CEID_GP80","Περιγραφική Ανάλυση της Νέας Ελληνικής",7,4,3,"FALL",10,"Χρ. Βλάχος");
        gp("CEID_GP70","Η Βυζαντινή Φιλολογία στον 21ο Αιώνα",7,4,3,"FALL",10,"Ε.-Σ. Κιαπίδου");
        gp("CEID_GP71","Εισαγωγή στη Νεοελληνική Φιλολογία",7,4,3,"FALL",10,"Ι. Παπαθεοδώρου");
        gp("CEID_GP72","Αρχαία Ελληνική Ιστορία",7,4,3,"FALL",10,"Α. Σύρκου");
        gp("CEID_GP81","Η Ποίηση και η Ποιητική του Καβάφη",7,4,3,"FALL",10,"Α. Κωστίου");
        gp("CEID_GP00","Αγγλικά ΙΙ",8,4,3,"SPRING",30,"Ε. Κωνσταντινοπούλου");
        gp("CEID_GP68","Αισθητική",8,4,3,"SPRING",20,"Εντεταλμένος Διδάσκων");
        gp("CEID_GP22","Ιστορία της Τέχνης στη Νεότερη Εποχή",8,4,3,"SPRING",25,"Αικ. Παπανδρεοπούλου");
        gp("CEID_GP36","Σύγχρονη Πολιτική Φιλοσοφία",8,4,3,"SPRING",20,"Μ. Σκομβούλη");
        gp("CEID_GP34","Νεότερη Γνωσιοθεωρία-Μεταφυσική Ι",8,4,3,"SPRING",20,"Γ. Σαγκριώτης");
        gp("CEID_GP76","Ελληνιστική και Ρωμαϊκή Ιστορία",8,4,3,"SPRING",10,"Α. Σύρκου");
        gp("CEID_GP77","Η Δημοκρατία στην Αρχαία Ελλάδα",8,4,3,"SPRING",10,"Αθ. Παπαχρυσοστόμου");
        gp("CEID_GP78","Η Λαϊκή Παράδοση στην Αρχαιότητα",8,4,3,"SPRING",10,"Α. Ποταμίτη");
        gp("CEID_GP79","Κοινωνιογλωσσολογία",8,4,3,"SPRING",10,"Αρ. Αρχάκης");
        gp("CEID_GP82","Εισαγωγή στη Γλωσσολογία ΙΙ",8,4,3,"SPRING",10,"Γ. Ξυδόπουλος");
        gp("CEID_DE2","Εισαγωγή στο Μάρκετινγκ",8,4,3,"SPRING",30,"Εντεταλμένος Διδάσκων");
        gp("CEID_DE7","Δημόσια Οικονομική",8,4,3,"SPRING",30,"Γ. Οικονομάκης");
        System.out.println("    -> GP courses seeded");
    }

    // -----------------------------------------------------------------------
    // Helper methods — upsert by code (skip αν υπάρχει)
    // -----------------------------------------------------------------------
    private void req(String code, String name, int sem, int year, int lec, int tut, int lab,
                     int ects, String sector, String semType, int students, String teachers) {
        if (courseRepo.findByCode(code).isPresent()) return;
        courseRepo.save(Course.builder().code(code).name(name).semester(sem).studyYear(year)
                .courseType(Course.CourseType.REQUIRED).lectureHours(lec).tutorialHours(tut).labHours(lab)
                .ects(ects).sector(sector).semesterType(Course.SemesterType.valueOf(semType))
                .expectedStudents(students).teachersText(teachers)
                .needsProjector(true).needsLab(lab > 0).active(true).visibleInTimetable(true).build());
    }

    private void elec(String code, String name, int sem, int year, int lec, int tut, int lab,
                      int ects, String sector, String semType, int students, String teachers) {
        if (courseRepo.findByCode(code).isPresent()) return;
        courseRepo.save(Course.builder().code(code).name(name).semester(sem).studyYear(year)
                .courseType(Course.CourseType.REQUIRED_ELECTIVE).lectureHours(lec).tutorialHours(tut).labHours(lab)
                .ects(ects).sector(sector).semesterType(Course.SemesterType.valueOf(semType))
                .expectedStudents(students).teachersText(teachers)
                .needsProjector(true).needsLab(lab > 0).active(true).visibleInTimetable(true).build());
    }

    private void ext(String code, String name, int sem, int year, int ects, String semType,
                     int students, String teachers) {
        if (courseRepo.findByCode(code).isPresent()) return;
        courseRepo.save(Course.builder().code(code).name(name).semester(sem).studyYear(year)
                .courseType(Course.CourseType.EXTERNAL).lectureHours(3).tutorialHours(0).labHours(0)
                .ects(ects).sector("EXT").semesterType(Course.SemesterType.valueOf(semType))
                .expectedStudents(students).teachersText(teachers)
                .needsProjector(true).needsLab(false).active(true).visibleInTimetable(true).build());
    }

    private void gp(String code, String name, int sem, int year, int ects, String semType,
                    int students, String teachers) {
        if (courseRepo.findByCode(code).isPresent()) return;
        courseRepo.save(Course.builder().code(code).name(name).semester(sem).studyYear(year)
                .courseType(Course.CourseType.GENERAL_EDUCATION).lectureHours(3).tutorialHours(0).labHours(0)
                .ects(ects).sector("ΓΠ").semesterType(Course.SemesterType.valueOf(semType))
                .expectedStudents(students).teachersText(teachers)
                .needsProjector(true).needsLab(false).active(true).visibleInTimetable(true).build());
    }
}