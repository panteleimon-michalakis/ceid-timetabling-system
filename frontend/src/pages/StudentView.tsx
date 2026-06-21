import api from '../api/client';
import { useStudentTimetableData } from '../hooks/useStudentTimetableData';
import StudentTimetableView from '../components/StudentTimetableView';

// Authed προβολή προγράμματος (route /view, μέσα στο AppLayout/Navbar).
// Όλη η λογική render ζει στο StudentTimetableView· εδώ μόνο το data wiring.
export default function StudentView() {
  const d = useStudentTimetableData(api, '/timetables');
  return <StudentTimetableView
    timetables={d.timetables} assignments={d.assignments} loading={d.loading}
    selectedTtId={d.selectedTtId} onSelectTt={d.setSelectedTtId} />;
}
