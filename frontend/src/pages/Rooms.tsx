import { useEffect, useState } from 'react';
import { roomService } from '../api/services';
import type { Room } from '../types';

const STYLES = `
@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;400;500;600&family=JetBrains+Mono:wght@400;500&display=swap');
.rm-root { font-family:'IBM Plex Sans',sans-serif; min-height:calc(100vh - 52px); background:#080f1a; color:#e2e8f0; padding:36px 48px; }
.rm-header { margin-bottom:28px; }
.rm-title { font-size:1.5rem; font-weight:600; letter-spacing:-0.4px; margin-bottom:4px; }
.rm-sub { font-size:13px; color:#475569; }
.rm-filters { display:flex; gap:4px; margin-bottom:24px; flex-wrap:wrap; }
.rm-btn { padding:5px 12px; border:none; border-radius:6px; cursor:pointer; font-size:12px; font-family:'IBM Plex Sans',sans-serif; font-weight:500; }
.rm-btn-active { background:#1d4ed8; color:#fff; }
.rm-btn-inactive { background:#0d1b2e; color:#64748b; border:1px solid #1a2744; }
.rm-btn-inactive:hover { color:#94a3b8; border-color:#1e3a5f; }
.rm-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(260px,1fr)); gap:14px; }
.rm-card { background:#0d1b2e; border:1px solid #1a2744; border-radius:10px; padding:18px 20px; position:relative; overflow:hidden; }
.rm-card::before { content:''; position:absolute; top:0; left:0; right:0; height:2px; background:var(--accent); }
.rm-card-header { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:8px; }
.rm-code { font-family:'JetBrains Mono',monospace; font-size:22px; font-weight:600; color:var(--accent); line-height:1; }
.rm-type-badge { font-family:'JetBrains Mono',monospace; font-size:9px; font-weight:600; padding:3px 8px; border-radius:4px; letter-spacing:0.5px; background:var(--accent-dim); color:var(--accent); border:1px solid var(--accent-border); }
.rm-name { font-size:13px; color:#94a3b8; margin-bottom:14px; }
.rm-stats { display:flex; gap:16px; margin-bottom:10px; }
.rm-stat { display:flex; flex-direction:column; gap:2px; }
.rm-stat-val { font-family:'JetBrains Mono',monospace; font-size:16px; font-weight:600; color:#e2e8f0; }
.rm-stat-lbl { font-size:10px; color:#475569; text-transform:uppercase; letter-spacing:0.5px; }
.rm-tags { display:flex; flex-wrap:wrap; gap:5px; padding-top:10px; border-top:1px solid #1a2744; }
.rm-tag { font-size:10px; padding:2px 8px; border-radius:4px; background:#1a2744; color:#64748b; font-family:'JetBrains Mono',monospace; }
.rm-tag-on { background:#052e16; color:#22c55e; }
.rm-empty { text-align:center; padding:48px; color:#334155; font-size:13px; }
`;

const TYPE_CONFIG: Record<string, { label: string; accent: string; dim: string; border: string }> = {
  AMPHITHEATER: { label: 'ΑΜΦΙΘΕΑΤΡΟ',  accent: '#3b82f6', dim: '#1e3a5f22', border: '#1e3a5f' },
  CLASSROOM:    { label: 'ΑΙΘΟΥΣΑ',     accent: '#10b981', dim: '#05311e22', border: '#064e3b' },
  LAB:          { label: 'ΕΡΓΑΣΤΗΡΙΟ',  accent: '#f59e0b', dim: '#78350f22', border: '#78350f' },
  EXAM_HALL:    { label: 'ΕΞΕΤΑΣΤΙΚΗ',  accent: '#ef4444', dim: '#450a0a22', border: '#7f1d1d' },
  MEETING_ROOM: { label: 'ΣΥΣΚΕΨΗ',     accent: '#8b5cf6', dim: '#3b0d8022', border: '#4c1d95' },
};

export default function Rooms() {
  const [rooms, setRooms]       = useState<Room[]>([]);
  const [filter, setFilter]     = useState('ALL');
  const [loading, setLoading]   = useState(true);

  useEffect(() => {
    roomService.getAll().then(r => setRooms(r.data)).finally(() => setLoading(false));
  }, []);

  const types = ['ALL', 'AMPHITHEATER', 'CLASSROOM', 'LAB', 'EXAM_HALL', 'MEETING_ROOM'];
  const typeLabels: Record<string, string> = {
    ALL: 'Όλες', AMPHITHEATER: 'Αμφιθέατρα', CLASSROOM: 'Αίθουσες',
    LAB: 'Εργαστήρια', EXAM_HALL: 'Εξεταστικής', MEETING_ROOM: 'Συσκέψεων',
  };

  const filtered = filter === 'ALL' ? rooms : rooms.filter(r => r.roomType === filter);

  return (
    <div className="rm-root">
      <style>{STYLES}</style>

      <div className="rm-header">
        <div className="rm-title">Αίθουσες</div>
        <div className="rm-sub">{rooms.length} χώροι · ΤΜΗΥΠ</div>
      </div>

      <div className="rm-filters">
        {types.map(t => (
          <button key={t} className={`rm-btn ${filter === t ? 'rm-btn-active' : 'rm-btn-inactive'}`}
            onClick={() => setFilter(t)}>
            {typeLabels[t]} {t !== 'ALL' && `(${rooms.filter(r => r.roomType === t).length})`}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="rm-empty">Φόρτωση...</div>
      ) : (
        <div className="rm-grid">
          {filtered.map(r => {
            const tc = TYPE_CONFIG[r.roomType] ?? TYPE_CONFIG.CLASSROOM;
            return (
              <div key={r.id} className="rm-card" style={{
                '--accent': tc.accent, '--accent-dim': tc.dim, '--accent-border': tc.border,
              } as React.CSSProperties}>
                <div className="rm-card-header">
                  <div className="rm-code">{r.code}</div>
                  <div className="rm-type-badge">{tc.label}</div>
                </div>
                <div className="rm-name">{r.name}</div>
                <div className="rm-stats">
                  <div className="rm-stat">
                    <div className="rm-stat-val">{r.capacity}</div>
                    <div className="rm-stat-lbl">Χωρητικότητα</div>
                  </div>
                </div>
                <div className="rm-tags">
                  {r.availableForSemester && <span className="rm-tag rm-tag-on">Εξαμηνιαίο</span>}
                  {r.availableForExams && <span className="rm-tag rm-tag-on">Εξεταστική</span>}
                  {r.hasProjector && <span className="rm-tag">Projector</span>}
                  {r.hasComputers && <span className="rm-tag">Η/Υ</span>}
                  {!r.availableForSemester && !r.availableForExams && (
                    <span className="rm-tag">Ανενεργή</span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
