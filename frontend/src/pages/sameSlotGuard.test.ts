import { describe, it, expect } from 'vitest';
import { isDropOnOriginSlot } from './sameSlotGuard';

const normalize = (v?: string | null) => (v ?? '').slice(0, 5);

describe('isDropOnOriginSlot (Task 1 no-op regression)', () => {
  it('weekly: ίδια μέρα+ώρα → true (no-op)', () => {
    const origin = { dayOfWeek: 'MONDAY', startTime: '09:00:00' };
    expect(isDropOnOriginSlot(origin, 'MONDAY', '09:00', normalize)).toBe(true);
  });

  it('weekly: διαφορετική ώρα → false (κανονικό move)', () => {
    const origin = { dayOfWeek: 'MONDAY', startTime: '09:00:00' };
    expect(isDropOnOriginSlot(origin, 'MONDAY', '10:00', normalize)).toBe(false);
  });

  it('weekly: διαφορετική μέρα → false (κανονικό move)', () => {
    const origin = { dayOfWeek: 'MONDAY', startTime: '09:00:00' };
    expect(isDropOnOriginSlot(origin, 'TUESDAY', '09:00', normalize)).toBe(false);
  });

  it('exam: ίδια ημερομηνία+ώρα → true (no-op)', () => {
    const origin = { specificDate: '2026-09-02', startTime: '09:00:00' };
    expect(isDropOnOriginSlot(origin, '2026-09-02', '09:00', normalize)).toBe(true);
  });

  it('exam: διαφορετική ημερομηνία → false (κανονικό move)', () => {
    const origin = { specificDate: '2026-09-02', startTime: '09:00:00' };
    expect(isDropOnOriginSlot(origin, '2026-09-03', '09:00', normalize)).toBe(false);
  });

  it('χωρίς origin (draggingAssignment null) → false', () => {
    expect(isDropOnOriginSlot(null, 'MONDAY', '09:00', normalize)).toBe(false);
  });
});
