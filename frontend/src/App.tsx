import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';
import Navbar from './components/Navbar';
import Dashboard from './pages/Dashboard';
import Courses from './pages/Courses';
import Rooms from './pages/Rooms';
import WeeklyTimetable from './pages/WeeklyTimetable';
import ExamTimetable from './pages/ExamTimetable';
import Teachers from './pages/Teachers';
import StudentView from './pages/StudentView';
import PublicStudentView from './pages/PublicStudentView';
import Users from './pages/Users';

// ─── PrivateRoute — redirect στο /login αν δεν είσαι authenticated ────────────
function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />;
}

// ─── RoleRoute — redirect στο / αν δεν έχεις τον σωστό ρόλο ─────────────────
// Χρησιμοποιείται για να εμποδίσει απευθείας πλοήγηση μέσω URL
// (π.χ. ένας STUDENT δεν μπορεί να πάει στο /teachers πληκτρολογώντας το URL).
function RoleRoute({
  children,
  roles,
}: {
  children: React.ReactNode;
  roles: Array<'ADMIN' | 'TEACHER' | 'STUDENT'>;
}) {
  const { user, isAuthenticated } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (!user || !roles.includes(user.role)) return <Navigate to="/dashboard" replace />;
  return <>{children}</>;
}

// ─── Layout με Navbar + Routes ────────────────────────────────────────────────
function AppLayout() {
  return (
    <>
      <Navbar />
      <Routes>
        {/* Προσβάσιμο από όλους τους συνδεδεμένους */}
        <Route path="/"          element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/courses"   element={<Courses />} />
        <Route path="/view"    element={<StudentView />} />

        {/* Μόνο ADMIN + TEACHER */}
        <Route path="/timetable" element={
          <RoleRoute roles={['ADMIN', 'TEACHER']}>
            <WeeklyTimetable />
          </RoleRoute>
        } />
        <Route path="/exams" element={
          <RoleRoute roles={['ADMIN', 'TEACHER']}>
            <ExamTimetable />
          </RoleRoute>
        } />
        <Route path="/rooms" element={
          <RoleRoute roles={['ADMIN', 'TEACHER']}>
            <Rooms />
          </RoleRoute>
        } />
        <Route path="/teachers" element={
          <RoleRoute roles={['ADMIN', 'TEACHER']}>
            <Teachers />
          </RoleRoute>
        } />

        {/* Μόνο ADMIN */}
        <Route path="/users" element={
          <RoleRoute roles={['ADMIN']}>
            <Users />
          </RoleRoute>
        } />

        {/* Fallback: άγνωστο path → αρχική */}
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </>
  );
}

// ─── Routing με auth check ────────────────────────────────────────────────────
function AppRoutes() {
  const { isAuthenticated } = useAuth();
  return (
    <Routes>
      {/* Login — αν ήδη συνδεδεμένος → αρχική */}
      <Route path="/login" element={
        isAuthenticated ? <Navigate to="/dashboard" replace /> : <LoginPage />
      } />
      {/* Δημόσια account-less προβολή — ΕΚΤΟΣ PrivateRoute/Navbar, χωρίς token */}
      <Route path="/public" element={<PublicStudentView />} />
      {/* Όλα τα άλλα → protected */}
      <Route path="/*" element={
        <PrivateRoute>
          <AppLayout />
        </PrivateRoute>
      } />
    </Routes>
  );
}

// ─── Root ─────────────────────────────────────────────────────────────────────
export default function App() {
  return (
    <div style={{ minHeight: '100vh', background: '#080f1a', color: '#e2e8f0' }}>
      <BrowserRouter>
        <AuthProvider>
          <AppRoutes />
        </AuthProvider>
      </BrowserRouter>
    </div>
  );
}
