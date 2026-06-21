import publicApi from '../api/publicClient';
import { useStudentTimetableData } from '../hooks/useStudentTimetableData';
import StudentTimetableView from '../components/StudentTimetableView';

// Δημόσια, account-less προβολή προγράμματος (route /public, ΕΚΤΟΣ PrivateRoute/Navbar).
// Διαβάζει μόνο PUBLISHED προγράμματα μέσω των account-less endpoints και
// auto-ανανεώνεται με polling κάθε 20s (pause σε hidden tab).
export default function PublicStudentView() {
  const d = useStudentTimetableData(publicApi, '/public/timetables', { pollMs: 20000 });
  return <StudentTimetableView
    timetables={d.timetables} assignments={d.assignments} loading={d.loading}
    selectedTtId={d.selectedTtId} onSelectTt={d.setSelectedTtId} />;
}
