-- =====================================================================
-- V7__add_courses_soft_delete.sql — #4 soft-delete μαθημάτων
--
-- Το hard delete έσπαγε σε FK (course_teachers + timetable_assignments) και ο
-- backend επέστρεφε ψεύτικη επιτυχία. Με soft-delete το μάθημα φεύγει από τον
-- ζωντανό κατάλογο αλλά η γραμμή μένει → υπάρχοντα προγράμματα το κρατούν
-- ακέραιο (S3 snapshot + #5 frozen scope). Additive, μη-καταστροφικό:
-- όλα τα υπάρχοντα μαθήματα παραμένουν deleted=false.
-- =====================================================================

ALTER TABLE public.courses
    ADD COLUMN deleted boolean NOT NULL DEFAULT false;
