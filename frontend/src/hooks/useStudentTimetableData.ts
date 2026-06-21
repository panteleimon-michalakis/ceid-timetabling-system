import { useEffect, useRef, useState, useCallback } from 'react';
import type { AxiosInstance } from 'axios';
import type { Timetable, Assignment } from '../components/studentTimetableTypes';

interface Options { pollMs?: number }

// Data + timetable-selection layer για το StudentTimetableView.
// Παραμετροποιημένο με axios client + base path ώστε ο authed (api, '/timetables')
// και ο public (publicApi, '/public/timetables') wrapper να μοιράζονται μία πηγή render.
export function useStudentTimetableData(
  client: AxiosInstance,
  basePath: string,            // '/timetables' ή '/public/timetables'
  options: Options = {}
) {
  const [timetables, setTimetables] = useState<Timetable[]>([]);
  const [selectedTtId, setSelectedTtId] = useState<number | null>(null);
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [loading, setLoading] = useState(true);
  const selectedRef = useRef<number | null>(null);
  // Latest-ref mirror: επιτρέπει στα fetchList/refresh callbacks να διαβάζουν την
  // τρέχουσα επιλογή χωρίς να εξαρτώνται από το selectedTtId — έτσι το polling
  // interval μένει σταθερό και δεν ξαναστήνεται σε κάθε αλλαγή προγράμματος.
  // eslint-disable-next-line react-hooks/refs
  selectedRef.current = selectedTtId;

  const fetchList = useCallback(async () => {
    const r = await client.get<Timetable[]>(basePath);
    const all = r.data;
    setTimetables(all);
    // default-selection ΜΟΝΟ αν δεν έχει ήδη επιλογή ή αν χάθηκε από τη λίστα
    const cur = selectedRef.current;
    const stillThere = cur != null && all.some(t => t.id === cur);
    if (!stillThere) {
      const sem = all.filter(t => t.timetableType === 'SEMESTER');
      if (sem.length > 0) setSelectedTtId(sem[0].id);
      else if (all.length > 0) setSelectedTtId(all[0].id);
      else setSelectedTtId(null);
    }
  }, [client, basePath]);

  const fetchAssignments = useCallback(async (id: number) => {
    const r = await client.get<Assignment[]>(`${basePath}/${id}/assignments`);
    // Μαθήματα «σε συνεννόηση» (visibleInTimetable=false) δεν εμφανίζονται.
    setAssignments(r.data.filter(a => a.course?.visibleInTimetable !== false));
  }, [client, basePath]);

  const refresh = useCallback(async () => {
    await fetchList();
    const cur = selectedRef.current;
    if (cur != null) await fetchAssignments(cur);
  }, [fetchList, fetchAssignments]);

  useEffect(() => { fetchList(); /* mount */ }, [fetchList]);

  useEffect(() => {
    if (selectedTtId == null) return;
    setLoading(true);
    fetchAssignments(selectedTtId).finally(() => setLoading(false));
  }, [selectedTtId, fetchAssignments]);

  // polling (μόνο όταν δοθεί pollMs): pause σε hidden tab, immediate refresh στο visible
  useEffect(() => {
    const ms = options.pollMs;
    if (!ms) return;
    const tick = () => { if (!document.hidden) refresh(); };
    const id = window.setInterval(tick, ms);
    const onVis = () => { if (!document.hidden) refresh(); };
    document.addEventListener('visibilitychange', onVis);
    return () => { window.clearInterval(id); document.removeEventListener('visibilitychange', onVis); };
  }, [options.pollMs, refresh]);

  return { timetables, assignments, loading, selectedTtId, setSelectedTtId, refresh };
}
