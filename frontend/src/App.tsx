import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Navbar from './components/Navbar';
import Dashboard from './pages/Dashboard';
import Courses from './pages/Courses';
import Rooms from './pages/Rooms';
import WeeklyTimetable from './pages/WeeklyTimetable';
import ExamTimetable from './pages/ExamTimetable';
import Teachers from './pages/Teachers';
import StudentView from './pages/StudentView';

export default function App() {
  return (
    <BrowserRouter>
      <div style={{ minHeight: '100vh', background: '#080f1a', color: '#e2e8f0' }}>
        <Navbar />
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/courses" element={<Courses />} />
          <Route path="/rooms" element={<Rooms />} />
          <Route path="/timetable" element={<WeeklyTimetable />} />
          <Route path="/exams" element={<ExamTimetable />} />
	  <Route path="/teachers" element={<Teachers />} />
	  <Route path="/view" element={<StudentView />} />
        </Routes>
      </div>
    </BrowserRouter>
  );
}
