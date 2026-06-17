-- =====================================================================
-- V2__add_active_soft_delete.sql  —  soft-delete support (S1)
--
-- Προσθέτει στήλη active στα master entities που έχουν user-facing delete
-- path (rooms, teachers). Πολιτική: deactivate πάντα επιτρεπτό· hard-delete
-- μόνο αν η εγγραφή δεν χρησιμοποιήθηκε ποτέ. Έτσι μια «σε χρήση» αίθουσα/
-- διδάσκων απενεργοποιείται με ασφάλεια — η γραμμή & τα FK μένουν, τα παλιά
-- προγράμματα παραμένουν ανέπαφα.
--
-- Additive & μη-καταστροφικό: backfill των υπαρχόντων μέσω DEFAULT true.
-- Το time_slots ΔΕΝ αποκτά active εδώ (δεν υπάρχει delete endpoint ακόμα —
-- αναβάλλεται για τη Φ2 όταν προστεθεί η σελίδα/διαγραφή χρονοθυρίδων).
-- =====================================================================

ALTER TABLE public.rooms    ADD COLUMN active boolean NOT NULL DEFAULT true;
ALTER TABLE public.teachers ADD COLUMN active boolean NOT NULL DEFAULT true;
