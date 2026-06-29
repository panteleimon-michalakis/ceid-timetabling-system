import { useState } from 'react';
import type { CSSProperties } from 'react';
import { timetableService } from '../api/services';
import type { Timetable } from '../types';

type CreateTimetablePayload = Parameters<typeof timetableService.create>[0];

interface TimetableSelectorProps {
  timetables: Timetable[];
  selectedTimetableId: number | null;
  onSelect: (id: number) => void;
  onCreated: (newTimetable: Timetable) => void;
  onDeleted: (id: number) => void;
  disabled: boolean;
  progress?: { percentage?: number; placedHours?: number; totalRequiredHours?: number } | null;
  timetableType?: 'SEMESTER' | 'EXAM';
}

export default function TimetableSelector({
  timetables,
  selectedTimetableId,
  onSelect,
  onCreated,
  onDeleted,
  disabled,
  progress,
  timetableType = 'SEMESTER',
}: TimetableSelectorProps) {
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [newName,         setNewName]         = useState('');
  const [newSemesterType, setNewSemesterType] = useState('FALL');
  const [startDate,       setStartDate]       = useState('');
  const [endDate,         setEndDate]         = useState('');
  const [excludedDates,   setExcludedDates]   = useState<string[]>([]);
  const [excludedInput,   setExcludedInput]   = useState('');
  const [saving,  setSaving]  = useState(false);
  const [error,   setError]   = useState<string | null>(null);

  function addExcludedDate() {
    const d = excludedInput;
    if (!d) return;
    setExcludedDates(prev => prev.includes(d) ? prev : [...prev, d].sort());
    setExcludedInput('');
  }

  function removeExcludedDate(d: string) {
    setExcludedDates(prev => prev.filter(x => x !== d));
  }

  const currentAcademicYear = (() => {
    const now = new Date();
    const y = now.getFullYear();
    return now.getMonth() >= 8 ? `${y}-${String(y+1).slice(2)}` : `${y-1}-${String(y).slice(2)}`;
  })();

  async function createTimetable() {
    if (!newName.trim()) { setError('Δώσε όνομα για το νέο πρόγραμμα.'); return; }
    if (timetableType === 'EXAM' && (!startDate || !endDate)) {
      setError('Δώσε ημερομηνίες έναρξης και λήξης για εξεταστική.'); return;
    }
    if (timetableType === 'EXAM' && endDate < startDate) {
      setError('Η ημερομηνία λήξης πρέπει να είναι μετά την έναρξη.'); return;
    }
    setSaving(true);
    setError(null);
    try {
      const payload: CreateTimetablePayload = {
        name: newName.trim(),
        academicYear: currentAcademicYear,
        timetableType,
        semesterType: newSemesterType,
      };
      if (timetableType === 'EXAM') {
        payload.startDate = startDate;
        payload.endDate   = endDate;
        payload.excludedDates = excludedDates.join(','); // CSV — οι αργίες εξαιρούνται αυτόματα από το backend
      }
      const res = await timetableService.create(payload);
      onCreated(res.data);
      setShowCreateForm(false);
      setNewName('');
      setStartDate('');
      setEndDate('');
      setExcludedDates([]);
      setExcludedInput('');
    } catch {
      setError('Σφάλμα κατά τη δημιουργία.');
    } finally {
      setSaving(false);
    }
  }

  async function deleteTimetable(id: number, name: string) {
    if (!confirm(`Διαγραφή του προγράμματος "${name}";\n\nΘα διαγραφούν και όλες οι αναθέσεις μαθημάτων.`)) return;
    setSaving(true);
    try {
      await timetableService.delete(id);
      onDeleted(id);
    } catch {
      setError('Σφάλμα κατά τη διαγραφή.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h2 style={{ fontSize: '1.1rem', margin: 0 }}>Προγράμματα</h2>
        <button
          onClick={() => { setShowCreateForm(v => !v); setError(null); }}
          disabled={disabled || saving}
          style={primaryBtnStyle}
        >
          + Νέο Πρόγραμμα
        </button>
      </div>

      {error && (
        <div style={{ color: '#f87171', fontSize: '0.85rem', marginBottom: '0.75rem' }}>{error}</div>
      )}

      {/* Create form */}
      {showCreateForm && (
        <div style={{ background: '#111827', border: '1px solid #1e293b', borderRadius: '10px', padding: '1rem', marginBottom: '1rem' }}>
          <div style={{
            display: 'grid',
            gridTemplateColumns: timetableType === 'EXAM' ? '1fr 140px 140px 140px 110px' : '1fr 180px 120px',
            gap: '0.75rem',
            alignItems: 'end',
          }}>
            <div>
              <label style={labelStyle}>Όνομα</label>
              <input
                value={newName}
                onChange={e => setNewName(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') createTimetable(); }}
                placeholder={timetableType === 'EXAM' ? 'π.χ. Εξεταστική Ιουνίου 2026' : 'π.χ. Χειμερινό 2025-26'}
                style={inputStyle}
              />
            </div>

            <div>
              <label style={labelStyle}>Εξάμηνο</label>
              <select value={newSemesterType} onChange={e => setNewSemesterType(e.target.value)} style={inputStyle}>
                <option value="FALL">Χειμερινό</option>
                <option value="SPRING">Εαρινό</option>
                {timetableType === 'EXAM' && <option value="SEPTEMBER">Σεπτέμβριος</option>}
              </select>
            </div>

            {timetableType === 'EXAM' && (
              <>
                <div>
                  <label style={labelStyle}>Ημ. Έναρξης</label>
                  <input
                    type="date"
                    value={startDate}
                    onChange={e => setStartDate(e.target.value)}
                    style={inputStyle}
                  />
                </div>
                <div>
                  <label style={labelStyle}>Ημ. Λήξης</label>
                  <input
                    type="date"
                    value={endDate}
                    onChange={e => setEndDate(e.target.value)}
                    style={inputStyle}
                  />
                </div>
              </>
            )}

            <button
              onClick={createTimetable}
              disabled={saving}
              style={{ ...successBtnStyle, marginBottom: '0.8rem' }}
            >
              {saving ? '...' : 'Δημιουργία'}
            </button>
          </div>

          {timetableType === 'EXAM' && (
            <div style={{ marginTop: '0.25rem' }}>
              <label style={labelStyle}>Εξαιρούμενες ημερομηνίες (προαιρετικά)</label>
              <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                <input
                  type="date"
                  value={excludedInput}
                  min={startDate || undefined}
                  max={endDate || undefined}
                  onChange={e => setExcludedInput(e.target.value)}
                  style={{ ...inputStyle, marginBottom: 0, width: '180px' }}
                />
                <button
                  type="button"
                  onClick={addExcludedDate}
                  disabled={!excludedInput}
                  style={{ ...primaryBtnStyle, padding: '0.5rem 0.85rem', opacity: excludedInput ? 1 : 0.5 }}
                >
                  + Προσθήκη
                </button>
                <span style={{ color: '#64748b', fontSize: '0.78rem' }}>
                  Οι επίσημες αργίες εξαιρούνται αυτόματα.
                </span>
              </div>
              {excludedDates.length > 0 && (
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '5px', marginTop: '0.6rem' }}>
                  {excludedDates.map(d => (
                    <span
                      key={d}
                      style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', background: '#1a2744', color: '#cbd5e1', borderRadius: '6px', padding: '3px 8px', fontSize: '0.75rem', fontFamily: 'JetBrains Mono, monospace' }}
                    >
                      {d}
                      <button
                        type="button"
                        onClick={() => removeExcludedDate(d)}
                        title="Αφαίρεση"
                        style={{ border: 'none', background: 'transparent', color: '#f87171', cursor: 'pointer', fontWeight: 700, fontSize: '0.85rem', lineHeight: 1, padding: 0 }}
                      >
                        ×
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* Empty state */}
      {timetables.length === 0 && !showCreateForm && (
        <div style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>
          Δεν υπάρχουν προγράμματα. Πάτα &quot;+ Νέο Πρόγραμμα&quot;.
        </div>
      )}

      {/* Timetable cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: '0.75rem' }}>
        {timetables.map((t) => {
          const isSelected = selectedTimetableId === t.id;
          const semLabel =
            t.semesterType === 'FALL'      ? 'Χειμερινό' :
            t.semesterType === 'SPRING'    ? 'Εαρινό' :
            t.semesterType === 'SEPTEMBER' ? 'SEPTEMBER' :
            t.semesterType;
          const pct = isSelected && progress ? (progress.percentage ?? 0) : null;

          return (
            <div
              key={t.id}
              onClick={() => onSelect(t.id)}
              style={{
                background:    isSelected ? '#1e3a5f' : '#111827',
                border:        isSelected ? '2px solid #3b82f6' : '1px solid #1e293b',
                borderRadius:  '10px',
                padding:       '1rem',
                cursor:        'pointer',
                position:      'relative',
                transition:    'border-color 0.15s',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 700, fontSize: '1rem', marginBottom: '0.25rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {t.name}
                  </div>
                  <div style={{ fontSize: '0.8rem', color: '#94a3b8' }}>
                    {semLabel} · {t.academicYear || '2025-26'}
                  </div>
                  {t.startDate && t.endDate && (
                    <div style={{ fontSize: '0.72rem', color: '#475569', marginTop: '2px', fontFamily: 'JetBrains Mono, monospace' }}>
                      {t.startDate} → {t.endDate}
                    </div>
                  )}
                </div>
                <button
                  onClick={e => { e.stopPropagation(); deleteTimetable(t.id, t.name); }}
                  disabled={disabled || saving}
                  title="Διαγραφή προγράμματος"
                  style={{ border: 'none', background: 'rgba(239,68,68,0.15)', color: '#f87171', borderRadius: '6px', padding: '0.25rem 0.5rem', cursor: 'pointer', fontSize: '0.75rem', fontWeight: 700, flexShrink: 0, marginLeft: '8px' }}
                >
                  Διαγραφή
                </button>
              </div>

              {isSelected && pct !== null && (
                <div style={{ marginTop: '0.75rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', color: '#94a3b8', marginBottom: '0.25rem' }}>
                    <span>Πρόοδος</span>
                    <span>{pct}%</span>
                  </div>
                  <div style={{ height: '6px', background: '#0f172a', borderRadius: '999px', overflow: 'hidden' }}>
                    <div style={{ height: '100%', width: `${pct}%`, background: pct === 100 ? '#10b981' : '#3b82f6', transition: 'width 0.3s ease' }} />
                  </div>
                  {progress?.placedHours !== undefined && progress?.totalRequiredHours !== undefined && (
                    <div style={{ fontSize: '0.72rem', color: '#64748b', marginTop: '0.2rem' }}>
                      {progress.placedHours}/{progress.totalRequiredHours} ώρες
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

const primaryBtnStyle: CSSProperties  = { padding: '0.5rem 1rem', border: 'none', borderRadius: '8px', background: '#2563eb', color: '#fff', fontWeight: 700, cursor: 'pointer', fontFamily: "'IBM Plex Sans', sans-serif" };
const successBtnStyle: CSSProperties  = { padding: '0.5rem 1rem', border: 'none', borderRadius: '8px', background: '#10b981', color: '#fff', fontWeight: 700, cursor: 'pointer', fontFamily: "'IBM Plex Sans', sans-serif" };
const labelStyle: CSSProperties       = { display: 'block', color: '#94a3b8', fontSize: '0.85rem', marginBottom: '0.3rem' };
const inputStyle: CSSProperties       = { width: '100%', padding: '0.55rem', borderRadius: '8px', border: '1px solid #334155', background: '#0f172a', color: '#e2e8f0', marginBottom: '0.8rem', boxSizing: 'border-box', fontFamily: "'IBM Plex Sans', sans-serif" };
