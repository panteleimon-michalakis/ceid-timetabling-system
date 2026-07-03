import { describe, it, expect, afterEach, beforeEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import TimetableSelector from './TimetableSelector';
import { AuthProvider } from '../context/AuthContext';
import type { Timetable } from '../types';

// E-3: DELETE /api/timetables/{id} απαιτεί ρόλο ADMIN (SecurityConfig), αλλά το κουμπί
// "Διαγραφή" εμφανιζόταν αδιακρίτως και σε TEACHER, που έπαιρνε πάντα 403 (role-gate by
// design, όχι bug στο backend). Fix: το κουμπί κρύβεται εντελώς για μη-ADMIN.
const sampleTimetable: Timetable = {
  id: 1,
  name: 'Δείγμα Πρόγραμμα',
  academicYear: '2025-26',
  timetableType: 'SEMESTER',
  semesterType: 'FALL',
  status: 'DRAFT',
};

function renderAs(role: 'ADMIN' | 'TEACHER') {
  localStorage.setItem('jwt', 'fake.jwt.token');
  localStorage.setItem('user', JSON.stringify({ username: role.toLowerCase(), fullName: role, role }));

  return render(
    <AuthProvider>
      <TimetableSelector
        timetables={[sampleTimetable]}
        selectedTimetableId={null}
        onSelect={() => {}}
        onCreated={() => {}}
        onDeleted={() => {}}
        disabled={false}
      />
    </AuthProvider>,
  );
}

describe('E-3: TimetableSelector delete button role visibility', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
    cleanup();
  });

  it('shows the delete button for ADMIN', () => {
    renderAs('ADMIN');
    expect(screen.getByTitle('Διαγραφή προγράμματος')).toBeTruthy();
  });

  it('hides the delete button for TEACHER (always fails with 403 by design)', () => {
    renderAs('TEACHER');
    expect(screen.queryByTitle('Διαγραφή προγράμματος')).toBeNull();
  });
});
