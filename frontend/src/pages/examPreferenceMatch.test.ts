import { describe, it, expect } from 'vitest';
import { matchesExamPreference } from './examPreferenceMatch';

// TASK 3 — θετική-μόνο ένδειξη «στην προτιμώμενη αίθουσα/ώρα εξέτασης».
// (rooms, hours, actualRoom, actualStartTime) -> boolean.

describe('matchesExamPreference', () => {
  it('room-only preference, actual room in list → match', () => {
    expect(matchesExamPreference('ΑΦΕ, Γ', '', 'ΑΦΕ', '09:00:00')).toBe(true);
    expect(matchesExamPreference('ΑΦΕ, Γ', null, 'Γ', '12:00:00')).toBe(true);
  });

  it('room-only preference, actual room NOT in list → no match', () => {
    expect(matchesExamPreference('ΑΦΕ, Γ', '', 'Δ1', '09:00:00')).toBe(false);
  });

  it('hour-only preference, actual start hour in list → match', () => {
    // "09:00:00" → 9· CSV "9, 12"
    expect(matchesExamPreference('', '9, 12', 'Δ1', '09:00:00')).toBe(true);
    expect(matchesExamPreference(null, '9, 12', 'Δ1', '12:00:00')).toBe(true);
  });

  it('hour-only preference, actual start hour NOT in list → no match', () => {
    expect(matchesExamPreference('', '9, 12', 'Δ1', '15:00:00')).toBe(false);
  });

  it('both room+hour set: match ΜΟΝΟ όταν ικανοποιούνται ΚΑΙ τα δύο', () => {
    expect(matchesExamPreference('ΑΦΕ', '9', 'ΑΦΕ', '09:00:00')).toBe(true);   // both ok
    expect(matchesExamPreference('ΑΦΕ', '9', 'ΑΦΕ', '12:00:00')).toBe(false);  // room ok, hour όχι
    expect(matchesExamPreference('ΑΦΕ', '9', 'Γ',   '09:00:00')).toBe(false);  // hour ok, room όχι
  });

  it('καμία δηλωμένη προτίμηση (κενά/null) → false (δεν δείχνουμε τίποτα)', () => {
    expect(matchesExamPreference('', '', 'ΑΦΕ', '09:00:00')).toBe(false);
    expect(matchesExamPreference(null, null, 'ΑΦΕ', '09:00:00')).toBe(false);
    expect(matchesExamPreference(undefined, undefined, 'ΑΦΕ', '09:00:00')).toBe(false);
  });

  it('parsing: κόμμα/;/κενά + μη-αριθμητικές ώρες αγνοούνται', () => {
    expect(matchesExamPreference(' Γ ; Β ', '', 'Β', '10:00:00')).toBe(true);
    expect(matchesExamPreference('', 'abc, 9', 'Δ1', '09:00:00')).toBe(true);   // "abc" αγνοείται, 9 ταιριάζει
    expect(matchesExamPreference('', 'abc', 'Δ1', '09:00:00')).toBe(false);      // καμία έγκυρη ώρα → καμία προτίμηση
  });

  it('missing actual room/time → no false-positive', () => {
    expect(matchesExamPreference('ΑΦΕ', '', null, '09:00:00')).toBe(false);
    expect(matchesExamPreference('', '9', 'Δ1', null)).toBe(false);
  });
});
