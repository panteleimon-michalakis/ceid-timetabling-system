import { describe, it, expect } from 'vitest';
import { buildSlotHintMap } from './placementHints';
import type { PlacementOption, PlacementOptionsResponse } from '../types';

// Feature #3 — 3-state drag-drop hints: το state ανά slot πρέπει να ταιριάζει με
// το τι θα κάνει το drop (πράσινο=καθαρό, amber=advisory warning, κόκκινο=δομικό block).

function option(day: string, time: string, allowed: boolean, warning: string | null): PlacementOption {
  return {
    allowed,
    score: 0,
    warning,
    status: allowed ? (warning ? 'WARNING' : 'ALLOWED') : 'BLOCKED',
    reasons: [],
    room: { id: 1, code: 'R1', name: 'Room 1' } as PlacementOption['room'],
    timeSlot: { dayOfWeek: day, startTime: time } as PlacementOption['timeSlot'],
  };
}

function resp(options: PlacementOption[]): PlacementOptionsResponse {
  return {
    timetableId: 1,
    course: {} as PlacementOptionsResponse['course'],
    assignmentType: 'LECTURE',
    totalOptions: options.length,
    allowedOptions: options.filter((o) => o.allowed).length,
    blockedOptions: options.filter((o) => !o.allowed).length,
    options,
  };
}

function onlyValue(map: Map<string, string>): string | undefined {
  return [...map.values()][0];
}

describe('buildSlotHintMap — three-state drag-drop hints (Feature #3)', () => {
  it('allowed + no warning → πράσινο (allowed)', () => {
    const map = buildSlotHintMap(resp([option('MONDAY', '09:00', true, null)]));
    expect(onlyValue(map)).toBe('allowed');
  });

  it('allowed + warning → amber (warning)', () => {
    const map = buildSlotHintMap(resp([option('MONDAY', '09:00', true, 'Room double-book')]));
    expect(onlyValue(map)).toBe('warning');
  });

  it('!allowed → κόκκινο (blocked)', () => {
    const map = buildSlotHintMap(resp([option('MONDAY', '09:00', false, null)]));
    expect(onlyValue(map)).toBe('blocked');
  });

  it('priority ανά slot: μια καθαρή αίθουσα υπερισχύει του warning', () => {
    const map = buildSlotHintMap(resp([
      option('MONDAY', '09:00', true, 'conflict'),
      option('MONDAY', '09:00', true, null),
    ]));
    expect(map.size).toBe(1);
    expect(onlyValue(map)).toBe('allowed');
  });

  it('priority ανά slot: warning υπερισχύει του blocked', () => {
    const map = buildSlotHintMap(resp([
      option('MONDAY', '09:00', false, null),
      option('MONDAY', '09:00', true, 'conflict'),
    ]));
    expect(onlyValue(map)).toBe('warning');
  });

  it('null response → κενό map', () => {
    expect(buildSlotHintMap(null).size).toBe(0);
  });
});
