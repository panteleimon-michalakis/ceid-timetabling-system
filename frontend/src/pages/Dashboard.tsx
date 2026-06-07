import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/client';
import { healthService, timetableService } from '../api/services';
import type { Timetable } from '../types';

// ─── Types ────────────────────────────────────────────────────────────────────

interface HealthData {
  status: string; application: string;
  rooms: number; courses: number; users: number;
}

interface TimetableStat {
  id: number;
  percentage: number; placedHours: number; totalRequiredHours: number;
  errorCount: number; warningCount: number; valid: boolean;
}

// ─── Constants ────────────────────────────────────────────────────────────────

const SEM_LABEL: Record<string, string> = {
  FALL: 'Χειμερινό', SPRING: 'Εαρινό', SEPTEMBER: 'Σεπτέμβριος',
};

// ─── Sub-components ───────────────────────────────────────────────────────────

function StatCard({ value, label, color, sub }: {
  value: number | string; label: string; color: string; sub?: string;
}) {
  return (
    <div style={{
      background: '#0d1b2e', border: '1px solid #1a2744', borderRadius: 10,
      padding: '18px 20px', position: 'relative', overflow: 'hidden',
    }}>
      <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: 2, background: color }} />
      <div style={{
        fontFamily: 'JetBrains Mono, monospace', fontSize: 28, fontWeight: 500,
        color, lineHeight: 1,
      }}>{value}</div>
      <div style={{ fontSize: 11, color: '#64748b', marginTop: 5, fontWeight: 500 }}>{label}</div>
      {sub && <div style={{ fontSize: 10, color: '#334155', marginTop: 3, fontFamily: 'JetBrains Mono, monospace' }}>{sub}</div>}
    </div>
  );
}

function BarRow({ label, value, max, color, detail }: {
  label: string; value: number; max: number; color: string; detail?: string;
}) {
  const pct = max > 0 ? Math.round((value / max) * 100) : 0;
  return (
    <div style={{ marginBottom: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, marginBottom: 5 }}>
        <span style={{ color: '#94a3b8', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '70%' }}>
          {label}
        </span>
        <span style={{ color, fontFamily: 'JetBrains Mono, monospace', fontWeight: 600, flexShrink: 0 }}>
          {pct}%{detail ? ` · ${detail}` : ''}
        </span>
      </div>
      <div style={{ height: 6, background: '#1a2744', borderRadius: 3, overflow: 'hidden' }}>
        <div style={{
          height: '100%', width: `${pct}%`, background: color,
          borderRadius: 3, transition: 'width 0.8s ease',
        }} />
      </div>
    </div>
  );
}

function TimetableCard({ t, stat, to }: {
  t: Timetable; stat?: TimetableStat; to: string;
}) {
  const pct     = stat?.percentage ?? null;
  const barColor = pct === null ? '#475569' : pct === 100 ? '#22c55e' : stat?.errorCount ? '#ef4444' : '#3b82f6';
  const semColor = t.semesterType === 'FALL' ? '#60a5fa' : t.semesterType === 'SPRING' ? '#34d399' : '#fbbf24';
  const semBg    = t.semesterType === 'FALL' ? '#0f1a2e' : t.semesterType === 'SPRING' ? '#12181a' : '#1a180c';
  const semBorder= t.semesterType === 'FALL' ? '#1e3a5f' : t.semesterType === 'SPRING' ? '#064e3b' : '#78350f';

  return (
    <Link to={to} style={{ textDecoration: 'none', color: 'inherit' }}
      onClick={() => {
        const key = t.timetableType === 'EXAM' ? 'selectedExamTimetableId' : 'selectedTimetableId';
        localStorage.setItem(key, String(t.id));
      }}>
      <div style={{
        background: '#0d1b2e', border: '1px solid #1a2744', borderRadius: 10,
        padding: '16px 18px', display: 'flex', flexDirection: 'column', gap: 10,
        cursor: 'pointer', transition: 'border-color 0.15s, background 0.15s',
      }}
        onMouseEnter={e => { (e.currentTarget as HTMLDivElement).style.borderColor = '#1e3a5f'; (e.currentTarget as HTMLDivElement).style.background = '#0f2038'; }}
        onMouseLeave={e => { (e.currentTarget as HTMLDivElement).style.borderColor = '#1a2744'; (e.currentTarget as HTMLDivElement).style.background = '#0d1b2e'; }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: '#e2e8f0', lineHeight: 1.3, flex: 1 }}>
            {t.name}
          </div>
          <span style={{
            fontFamily: 'JetBrains Mono, monospace', fontSize: 9, fontWeight: 600,
            padding: '2px 7px', borderRadius: 3, letterSpacing: '0.5px',
            background: semBg, color: semColor, border: `1px solid ${semBorder}`,
            flexShrink: 0,
          }}>
            {SEM_LABEL[t.semesterType ?? ''] ?? t.semesterType ?? '—'}
          </span>
        </div>

        <div style={{ fontSize: 10, color: '#475569', fontFamily: 'JetBrains Mono, monospace' }}>
          {t.academicYear}
          {t.startDate && t.endDate && ` · ${t.startDate} → ${t.endDate}`}
        </div>

        {pct !== null && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: '#475569', marginBottom: 4 }}>
              <span>{stat!.placedHours}/{stat!.totalRequiredHours} ώρες</span>
              <span style={{ color: barColor, fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>
                {Math.round(pct)}%
              </span>
            </div>
            <div style={{ height: 5, background: '#1a2744', borderRadius: 3, overflow: 'hidden' }}>
              <div style={{ height: '100%', width: `${pct}%`, background: barColor, borderRadius: 3, transition: 'width 0.8s ease' }} />
            </div>
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: 8, borderTop: '1px solid #1a2744' }}>
          <div style={{ display: 'flex', gap: 8 }}>
            {stat?.errorCount   ? <span style={{ color: '#f87171', fontSize: 10 }}>⚠ {stat.errorCount} errors</span>   : null}
            {stat?.warningCount ? <span style={{ color: '#fbbf24', fontSize: 10 }}>{stat.warningCount} warnings</span> : null}
            {stat && !stat.errorCount && stat.valid && <span style={{ color: '#22c55e', fontSize: 10 }}>✓ Έγκυρο</span>}
            {!stat && t.timetableType === 'EXAM' && <span style={{ color: '#334155', fontSize: 10 }}>ΕΞΕΤΑΣΤΙΚΗ</span>}
            {!stat && t.timetableType === 'SEMESTER' && <span style={{ color: '#334155', fontSize: 10 }}>Φόρτωση...</span>}
          </div>
          <span style={{ fontSize: 11, color: '#3b82f6', fontWeight: 500 }}>Άνοιγμα →</span>
        </div>
      </div>
    </Link>
  );
}

// ─── Main ─────────────────────────────────────────────────────────────────────

export default function Dashboard() {
  const [health,        setHealth]        = useState<HealthData | null>(null);
  const [timetables,    setTimetables]    = useState<Timetable[]>([]);
  const [stats,         setStats]         = useState<Map<number, TimetableStat>>(new Map());
  const [teacherCount,  setTeacherCount]  = useState(0);
  const [error,         setError]         = useState<string | null>(null);

  useEffect(() => {
    Promise.all([
      healthService.check(),
      timetableService.getAll(),
      api.get('/teachers').catch(() => ({ data: [] })),
    ]).then(([hRes, ttRes, tcRes]) => {
      setHealth(hRes.data);
      const tts = ttRes.data as Timetable[];
      setTimetables(tts);
      setTeacherCount(Array.isArray(tcRes.data) ? tcRes.data.length : 0);
      // Load progress + validation for SEMESTER timetables
      const semIds = tts.filter(t => t.timetableType === 'SEMESTER').map(t => t.id);
      Promise.all(semIds.map(id =>
        Promise.all([
          api.get(`/timetables/${id}/progress`).catch(() => ({ data: {} })),
          api.get(`/timetables/${id}/validation`).catch(() => ({ data: {} })),
        ]).then(([p, v]) => ({
          id,
          percentage:         p.data.percentage         ?? 0,
          placedHours:        p.data.placedHours        ?? 0,
          totalRequiredHours: p.data.totalRequiredHours ?? 0,
          errorCount:         v.data.errorCount         ?? 0,
          warningCount:       v.data.warningCount       ?? 0,
          valid:              v.data.valid              ?? true,
        } as TimetableStat))
      )).then(results => {
        const m = new Map<number, TimetableStat>();
        results.forEach(r => m.set(r.id, r));
        setStats(m);
      });
    }).catch(() => setError('Αδύνατη σύνδεση με τον server. Τρέχει το backend;'));
  }, []);

  const semTimetables  = useMemo(() => timetables.filter(t => t.timetableType === 'SEMESTER'), [timetables]);
  const examTimetables = useMemo(() => timetables.filter(t => t.timetableType === 'EXAM'),     [timetables]);

  const totalPlaced   = useMemo(() => [...stats.values()].reduce((s, v) => s + v.placedHours,  0), [stats]);
  const totalErrors   = useMemo(() => [...stats.values()].reduce((s, v) => s + v.errorCount,   0), [stats]);
  const totalWarnings = useMemo(() => [...stats.values()].reduce((s, v) => s + v.warningCount, 0), [stats]);

  const font = "'IBM Plex Sans', sans-serif";

  if (error) return (
    <div style={{ padding: '40px 48px', background: '#080f1a', minHeight: 'calc(100vh - 52px)', color: '#f87171', fontFamily: font }}>
      ⚠ {error}
    </div>
  );

  if (!health) return (
    <div style={{ padding: '40px 48px', background: '#080f1a', minHeight: 'calc(100vh - 52px)', color: '#334155', fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>
      Φόρτωση δεδομένων...
    </div>
  );

  const sectionTitle = (text: string) => (
    <div style={{
      fontFamily: 'JetBrains Mono, monospace', fontSize: 10, fontWeight: 600,
      color: '#475569', letterSpacing: '1.5px', textTransform: 'uppercase' as const,
      marginBottom: 14, marginTop: 36,
    }}>{text}</div>
  );

  return (
    <div style={{ fontFamily: font, minHeight: 'calc(100vh - 52px)', background: '#080f1a', color: '#e2e8f0', padding: '36px 48px' }}>
      <style>{`@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:ital,wght@0,300;0,400;0,500;0,600;1,300&family=JetBrains+Mono:wght@400;500&display=swap');`}</style>

      {/* Header */}
      <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 10, color: '#3b82f6', letterSpacing: 2, textTransform: 'uppercase', marginBottom: 8 }}>
        ΤΜΗΥΠ · Πανεπιστήμιο Πατρών
      </div>
      <div style={{ fontSize: 26, fontWeight: 600, letterSpacing: '-0.5px', marginBottom: 4 }}>
        Σύστημα Διαχείρισης Ωρολογίου
      </div>
      <div style={{ fontSize: 13, fontWeight: 300, color: '#475569', fontStyle: 'italic', marginBottom: 32 }}>
        Τμήμα Μηχανικών Ηλεκτρονικών Υπολογιστών και Πληροφορικής
      </div>

      {/* ── Stat cards ── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 14 }}>
        <StatCard value={health.courses}  label="Μαθήματα"         color="#3b82f6" />
        <StatCard value={health.rooms}    label="Αίθουσες"          color="#10b981" />
        <StatCard value={teacherCount}    label="Καθηγητές"         color="#8b5cf6" />
        <StatCard value={totalPlaced}     label="Τοποθ. Ώρες"       color="#f59e0b" sub="εξαμηνιαία" />
        <StatCard
          value={totalErrors === 0 ? '✓' : totalErrors}
          label="Errors"
          color={totalErrors === 0 ? '#22c55e' : '#ef4444'}
          sub={totalWarnings > 0 ? `${totalWarnings} warnings` : 'καθαρό'}
        />
      </div>

      {/* ── Placement progress chart ── */}
      {semTimetables.length > 0 && stats.size > 0 && (
        <>
          {sectionTitle('Πρόοδος Τοποθέτησης')}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>

            {/* Bar chart (CSS) */}
            <div style={{ background: '#0d1b2e', border: '1px solid #1a2744', borderRadius: 10, padding: '20px 22px' }}>
              <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 10, fontWeight: 600, color: '#475569', letterSpacing: '0.8px', marginBottom: 18, textTransform: 'uppercase' }}>
                Ανά Πρόγραμμα (%)
              </div>
              {semTimetables.map(t => {
                const st = stats.get(t.id);
                if (!st) return null;
                const color = st.percentage === 100 ? '#22c55e' : st.errorCount > 0 ? '#ef4444' : '#3b82f6';
                return (
                  <BarRow key={t.id}
                    label={t.name}
                    value={st.percentage} max={100}
                    color={color}
                    detail={`${st.placedHours}/${st.totalRequiredHours}h`}
                  />
                );
              })}
            </div>

            {/* Summary stats */}
            <div style={{ background: '#0d1b2e', border: '1px solid #1a2744', borderRadius: 10, padding: '20px 22px' }}>
              <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 10, fontWeight: 600, color: '#475569', letterSpacing: '0.8px', marginBottom: 18, textTransform: 'uppercase' }}>
                Συγκεντρωτικά
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                {[
                  { label: 'Εξαμηνιαία',    val: semTimetables.length,  color: '#3b82f6' },
                  { label: 'Εξεταστικές',   val: examTimetables.length, color: '#f59e0b' },
                  { label: 'Τοποθετημένα',  val: `${totalPlaced}h`,     color: '#10b981' },
                  { label: 'Συνολικά',      val: timetables.length,     color: '#8b5cf6' },
                  { label: 'Errors',        val: totalErrors,   color: totalErrors   > 0 ? '#ef4444' : '#22c55e' },
                  { label: 'Warnings',      val: totalWarnings, color: totalWarnings > 0 ? '#fbbf24' : '#22c55e' },
                ].map(s => (
                  <div key={s.label} style={{ background: '#111e33', borderRadius: 8, padding: '12px 14px', borderTop: `2px solid ${s.color}` }}>
                    <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 20, fontWeight: 600, color: s.color }}>{s.val}</div>
                    <div style={{ fontSize: 10, color: '#64748b', marginTop: 3 }}>{s.label}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </>
      )}

      {/* ── Semester timetables ── */}
      {semTimetables.length > 0 && (
        <>
          {sectionTitle('Εξαμηνιαία Προγράμματα')}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(290px, 1fr))', gap: 12 }}>
            {semTimetables.map(t => (
              <TimetableCard key={t.id} t={t} stat={stats.get(t.id)} to="/timetable" />
            ))}
          </div>
        </>
      )}

      {/* ── Exam timetables ── */}
      {examTimetables.length > 0 && (
        <>
          {sectionTitle('Εξεταστικές Περίοδοι')}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(290px, 1fr))', gap: 12 }}>
            {examTimetables.map(t => (
              <TimetableCard key={t.id} t={t} to="/exams" />
            ))}
          </div>
        </>
      )}

      {timetables.length === 0 && (
        <div style={{ textAlign: 'center', padding: 48, color: '#334155', fontSize: 13 }}>
          Δεν βρέθηκαν αποθηκευμένα προγράμματα.
        </div>
      )}

      {/* Status bar */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 16, padding: '12px 16px',
        background: '#0d1b2e', border: '1px solid #1a2744', borderRadius: 8,
        fontSize: 11, color: '#475569', fontFamily: 'JetBrains Mono, monospace', marginTop: 36,
      }}>
        <div style={{ width: 7, height: 7, borderRadius: '50%', background: '#22c55e',
          animation: 'none', flexShrink: 0, boxShadow: '0 0 6px #22c55e' }} />
        <span style={{ color: '#22c55e' }}>ONLINE</span>
        <span>·</span><span>{health.application}</span>
        <span>·</span><span>PostgreSQL 16</span>
        <span>·</span><span>Spring Boot 3.5</span>
        <span>·</span><span>Timefold Solver</span>
        <span>·</span><span>React + TypeScript</span>
      </div>
    </div>
  );
}
