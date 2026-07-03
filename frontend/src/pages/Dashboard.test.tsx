import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Dashboard from './Dashboard';
import { AuthProvider } from '../context/AuthContext';

// BL-13: Dashboard.tsx έφερνε progress/validation stats ΜΟΝΟ για SEMESTER
// timetables (φίλτρο `t.timetableType === 'SEMESTER'` στο loadAll) και δεν
// περνούσε καν το `stat` prop στην κάρτα EXAM. Άρα το hasErrors έμενε πάντα
// false για εξεταστικές περιόδους → το "δημοσίευση με σφάλματα" confirmation
// gate δεν εμφανιζόταν ΠΟΤΕ, ακόμα κι όταν υπήρχαν ενεργά hard violations.
vi.mock('../api/client', () => ({
  default: {
    get: vi.fn((url: string) => {
      if (url === '/health') {
        return Promise.resolve({ data: { status: 'UP', application: 'ceid-timetable', rooms: 5, courses: 10 } });
      }
      if (url === '/timetables') {
        return Promise.resolve({
          data: [{
            id: 1,
            name: 'Εξεταστική Χειμερινού 2025-26',
            academicYear: '2025-26',
            timetableType: 'EXAM',
            semesterType: 'FALL',
            status: 'SOLVED',
          }],
        });
      }
      if (url === '/teachers') {
        return Promise.resolve({ data: [] });
      }
      if (url === '/timetables/1/progress') {
        return Promise.resolve({ data: { percentage: 60, placedHours: 6, totalRequiredHours: 10 } });
      }
      if (url === '/timetables/1/validation') {
        return Promise.resolve({ data: { errorCount: 3, warningCount: 1, valid: false, errors: [], warnings: [] } });
      }
      return Promise.resolve({ data: {} });
    }),
    post: vi.fn(() => Promise.resolve({ data: {} })),
    put: vi.fn(() => Promise.resolve({ data: {} })),
    delete: vi.fn(() => Promise.resolve({ data: {} })),
    defaults: { headers: { common: {} as Record<string, string> } },
  },
}));

function renderDashboardAsAdmin() {
  localStorage.setItem('jwt', 'fake.jwt.token');
  localStorage.setItem('user', JSON.stringify({ username: 'admin', fullName: 'Admin', role: 'ADMIN' }));

  return render(
    <AuthProvider>
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe('BL-13: EXAM timetable publish shows the same error confirmation as SEMESTER', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
    cleanup();
    vi.restoreAllMocks();
  });

  it('asks for confirmation before publishing an EXAM timetable with active hard errors', async () => {
    renderDashboardAsAdmin();

    const publishButton = await screen.findByTitle(
      /σφάλματα — θα ζητηθεί επιβεβαίωση/,
      {},
      { timeout: 3000 },
    );

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    fireEvent.click(publishButton);

    expect(confirmSpy).toHaveBeenCalledWith(expect.stringContaining('3 σφάλματα'));
  });
});
