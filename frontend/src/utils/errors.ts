import { isAxiosError } from 'axios';

/**
 * Εξάγει ένα ανθρωπίνως αναγνώσιμο μήνυμα σφάλματος από ένα άγνωστο (unknown) σφάλμα.
 *
 * Διατηρεί την ίδια σειρά προτεραιότητας που χρησιμοποιούσαν τα κατά τόπους
 * `catch (err: any)` blocks:  data.error → data.message → error.message → fallback.
 * Με αυτόν τον τρόπο αντικαθιστά με ασφάλεια όλα τα `any` στα catch χωρίς αλλαγή συμπεριφοράς.
 *
 * @param error    Το σφάλμα από ένα catch block (τύπου unknown).
 * @param fallback Προεπιλεγμένο μήνυμα όταν δεν βρεθεί κάτι πιο συγκεκριμένο.
 */
export function getErrorMessage(
  error: unknown,
  fallback = 'Προέκυψε άγνωστο σφάλμα.',
): string {
  if (isAxiosError(error)) {
    const data = error.response?.data as
      | { error?: string; message?: string }
      | undefined;
    return data?.error || data?.message || error.message || fallback;
  }
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}
