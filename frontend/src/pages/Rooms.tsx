import { useEffect, useState } from 'react';
import { roomService } from '../api/services';
import { useAuth } from '../context/AuthContext';
import type { Room } from '../types';

// ─── Constants ────────────────────────────────────────────────────────────────

const TYPE_CONFIG: Record<string, { label: string; accent: string; dim: string; border: string }> = {
  AMPHITHEATER: { label: 'ΑΜΦΙΘΕΑΤΡΟ', accent:'#3b82f6', dim:'#1e3a5f22', border:'#1e3a5f' },
  CLASSROOM:    { label: 'ΑΙΘΟΥΣΑ',    accent:'#10b981', dim:'#05311e22', border:'#064e3b' },
  LAB:          { label: 'ΕΡΓΑΣΤΗΡΙΟ', accent:'#f59e0b', dim:'#78350f22', border:'#78350f' },
  EXAM_HALL:    { label: 'ΕΞΕΤΑΣΤΙΚΗ', accent:'#ef4444', dim:'#450a0a22', border:'#7f1d1d' },
  MEETING_ROOM: { label: 'ΣΥΣΚΕΨΗ',    accent:'#8b5cf6', dim:'#3b0d8022', border:'#4c1d95' },
};

const ROOM_TYPES = ['AMPHITHEATER','CLASSROOM','LAB','EXAM_HALL','MEETING_ROOM'];

const TYPE_LABELS: Record<string,string> = {
  ALL:'Όλες', AMPHITHEATER:'Αμφιθέατρα', CLASSROOM:'Αίθουσες',
  LAB:'Εργαστήρια', EXAM_HALL:'Εξεταστικής', MEETING_ROOM:'Συσκέψεων',
};

const EMPTY_ROOM: Partial<Room> = {
  code:'', name:'', capacity: 50, roomType:'CLASSROOM',
  hasProjector: true, hasComputers: false,
  availableForSemester: true, availableForExams: true,
};

// ─── Styles ───────────────────────────────────────────────────────────────────

const S = `
@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;400;500;600&family=JetBrains+Mono:wght@400;500&display=swap');
.rm-root{font-family:'IBM Plex Sans',sans-serif;min-height:calc(100vh - 52px);background:#080f1a;color:#e2e8f0;padding:36px 48px;}
.rm-btn{padding:5px 12px;border:none;border-radius:6px;cursor:pointer;font-size:12px;font-family:'IBM Plex Sans',sans-serif;font-weight:500;}
.rm-btn-active{background:#1d4ed8;color:#fff;}
.rm-btn-inactive{background:#0d1b2e;color:#64748b;border:1px solid #1a2744;}
.rm-btn-inactive:hover{color:#94a3b8;border-color:#1e3a5f;}
.rm-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:14px;}
.rm-card{background:#0d1b2e;border:1px solid #1a2744;border-radius:10px;padding:18px 20px;position:relative;overflow:hidden;}
.rm-card::before{content:'';position:absolute;top:0;left:0;right:0;height:2px;background:var(--accent);}
.rm-code{font-family:'JetBrains Mono',monospace;font-size:22px;font-weight:600;color:var(--accent);line-height:1;}
.rm-type-badge{font-family:'JetBrains Mono',monospace;font-size:9px;font-weight:600;padding:3px 8px;border-radius:4px;letter-spacing:0.5px;background:var(--dim);color:var(--accent);border:1px solid var(--border);}
.rm-name{font-size:13px;color:#94a3b8;margin-bottom:14px;}
.rm-stat-val{font-family:'JetBrains Mono',monospace;font-size:16px;font-weight:600;color:#e2e8f0;}
.rm-stat-lbl{font-size:10px;color:#475569;text-transform:uppercase;letter-spacing:0.5px;}
.rm-tags{display:flex;flex-wrap:wrap;gap:5px;padding-top:10px;border-top:1px solid #1a2744;}
.rm-tag{font-size:10px;padding:2px 8px;border-radius:4px;background:#1a2744;color:#64748b;font-family:'JetBrains Mono',monospace;}
.rm-tag-on{background:#052e16;color:#22c55e;}
.overlay{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.75);display:flex;align-items:center;justify-content:center;z-index:1000;}
.modal{background:#0d1b2e;border:1px solid #1a2744;border-radius:12px;padding:28px 32px;width:480px;max-height:88vh;overflow-y:auto;}
.modal-title{font-size:1.1rem;font-weight:600;margin-bottom:20px;color:#f1f5f9;}
.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px;}
.form-group{display:flex;flex-direction:column;gap:5px;}
.form-label{font-size:11px;color:#94a3b8;font-weight:500;}
.form-input{padding:7px 10px;border-radius:7px;border:1px solid #1a2744;background:#080f1a;color:#e2e8f0;font-size:13px;font-family:'IBM Plex Sans',sans-serif;}
.form-input:focus{outline:none;border-color:#1d4ed8;}
.form-select{padding:7px 10px;border-radius:7px;border:1px solid #1a2744;background:#080f1a;color:#e2e8f0;font-size:13px;font-family:'IBM Plex Sans',sans-serif;}
.btn-primary{padding:8px 20px;border:none;border-radius:8px;background:#1d4ed8;color:#fff;font-weight:600;cursor:pointer;font-size:13px;font-family:'IBM Plex Sans',sans-serif;}
.btn-secondary{padding:8px 20px;border:none;border-radius:8px;background:#1a2744;color:#94a3b8;font-weight:600;cursor:pointer;font-size:13px;font-family:'IBM Plex Sans',sans-serif;}
`;

// ─── Room Modal ───────────────────────────────────────────────────────────────

function RoomModal({ room, onClose, onSaved }: {
  room: Partial<Room>; onClose: () => void; onSaved: () => void;
}) {
  const [form, setForm] = useState<Partial<Room>>(room);
  const [saving, setSaving] = useState(false);
  const [error,  setError]  = useState('');
  const isNew = !form.id;
  const set = (k: keyof Room, v: unknown) => setForm(f => ({ ...f, [k]: v }));

  async function handleSave() {
    if (!form.code?.trim() || !form.name?.trim()) {
      setError('Κωδικός και Όνομα είναι υποχρεωτικά.'); return;
    }
    setSaving(true);
    try {
      if (isNew) await roomService.create(form as Room);
      else        await roomService.update(form.id!, form as Room);
      onSaved(); onClose();
    } catch (e: any) {
      setError(e?.response?.data?.error ?? 'Σφάλμα αποθήκευσης.');
    } finally { setSaving(false); }
  }

  const bool = (v: boolean | undefined, k: keyof Room) => (
    <select className="form-select" value={v ? 'true' : 'false'} onChange={e => set(k, e.target.value === 'true')}>
      <option value="true">Ναι</option>
      <option value="false">Όχι</option>
    </select>
  );

  return (
    <div className="overlay" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <div className="modal-title">{isNew ? '+ Νέα Αίθουσα' : 'Επεξεργασία Αίθουσας'}</div>
        <div className="form-grid">
          <div className="form-group">
            <label className="form-label">Κωδικός *</label>
            <input className="form-input" value={form.code??''} onChange={e => set('code',e.target.value)} placeholder="π.χ. Γ" />
          </div>
          <div className="form-group">
            <label className="form-label">Τύπος</label>
            <select className="form-select" value={form.roomType??'CLASSROOM'} onChange={e => set('roomType',e.target.value)}>
              {ROOM_TYPES.map(t => <option key={t} value={t}>{TYPE_CONFIG[t]?.label??t}</option>)}
            </select>
          </div>
          <div className="form-group" style={{ gridColumn:'1/-1' }}>
            <label className="form-label">Όνομα *</label>
            <input className="form-input" value={form.name??''} onChange={e => set('name',e.target.value)} placeholder="π.χ. Αμφιθέατρο Γ" />
          </div>
          <div className="form-group">
            <label className="form-label">Χωρητικότητα</label>
            <input className="form-input" type="number" min={0} value={form.capacity??0} onChange={e => set('capacity',+e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label">Projector</label>
            {bool(form.hasProjector, 'hasProjector')}
          </div>
          <div className="form-group">
            <label className="form-label">Υπολογιστές (Η/Υ)</label>
            {bool(form.hasComputers, 'hasComputers')}
          </div>
          <div className="form-group">
            <label className="form-label">Για Εξαμηνιαίο</label>
            {bool(form.availableForSemester, 'availableForSemester')}
          </div>
          <div className="form-group">
            <label className="form-label">Για Εξεταστική</label>
            {bool(form.availableForExams, 'availableForExams')}
          </div>
          <div className="form-group" style={{ gridColumn:'1/-1' }}>
            <label className="form-label">Σημειώσεις</label>
            <input className="form-input" value={form.notes??''} onChange={e => set('notes',e.target.value)} placeholder="Προαιρετικές σημειώσεις" />
          </div>
        </div>
        {error && <div style={{ color:'#f87171', fontSize:12, marginTop:12 }}>{error}</div>}
        <div style={{ display:'flex', justifyContent:'flex-end', gap:10, marginTop:20 }}>
          <button className="btn-secondary" onClick={onClose}>Ακύρωση</button>
          <button className="btn-primary" onClick={handleSave} disabled={saving}>
            {saving ? 'Αποθήκευση...' : 'Αποθήκευση'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Main Page ────────────────────────────────────────────────────────────────

export default function Rooms() {
  const { user } = useAuth();
  const isAdmin  = user?.role === 'ADMIN';

  const [rooms,   setRooms]   = useState<Room[]>([]);
  const [filter,  setFilter]  = useState('ALL');
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<Partial<Room> | false>(false);
  const [toast,   setToast]   = useState('');

  function load() {
    setLoading(true);
    roomService.getAll().then(r => setRooms(r.data)).finally(() => setLoading(false));
  }

  useEffect(() => { load(); }, []);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(''), 3000);
    return () => clearTimeout(t);
  }, [toast]);

  async function handleDelete(r: Room) {
    if (!confirm(`Διαγραφή αίθουσας "${r.name}";`)) return;
    try {
      await roomService.delete(r.id);
      setToast('Η αίθουσα διαγράφηκε.');
      load();
    } catch { setToast('Σφάλμα διαγραφής.'); }
  }

  const filtered = filter === 'ALL' ? rooms : rooms.filter(r => r.roomType === filter);

  return (
    <div className="rm-root">
      <style>{S}</style>

      {toast && (
        <div style={{ position:'fixed', top:16, right:16, background:'#052e16', border:'1px solid #22c55e',
          borderRadius:8, padding:'12px 20px', color:'#4ade80', fontSize:13, zIndex:3000 }}>
          {toast}
        </div>
      )}

      {/* Header */}
      <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-start', marginBottom:24 }}>
        <div>
          <div style={{ fontSize:22, fontWeight:600, letterSpacing:'-0.4px', marginBottom:4 }}>Αίθουσες</div>
          <div style={{ fontSize:13, color:'#475569' }}>{rooms.length} χώροι · ΤΜΗΥΠ</div>
        </div>
        {isAdmin && (
          <button style={{ padding:'8px 20px', border:'none', borderRadius:8, background:'#1d4ed8',
            color:'#fff', fontWeight:600, cursor:'pointer', fontSize:13, fontFamily:"'IBM Plex Sans',sans-serif" }}
            onClick={() => setEditing(EMPTY_ROOM)}>
            + Νέα Αίθουσα
          </button>
        )}
      </div>

      {/* Filters */}
      <div style={{ display:'flex', gap:4, marginBottom:24, flexWrap:'wrap' }}>
        {['ALL',...ROOM_TYPES].map(t => (
          <button key={t} className={`rm-btn ${filter===t?'rm-btn-active':'rm-btn-inactive'}`}
            onClick={() => setFilter(t)}>
            {TYPE_LABELS[t]} {t!=='ALL'&&`(${rooms.filter(r=>r.roomType===t).length})`}
          </button>
        ))}
      </div>

      {/* Grid */}
      {loading ? (
        <div style={{ color:'#334155', fontSize:13, padding:32, textAlign:'center' }}>Φόρτωση...</div>
      ) : (
        <div className="rm-grid">
          {filtered.map(r => {
            const tc = TYPE_CONFIG[r.roomType] ?? TYPE_CONFIG.CLASSROOM;
            return (
              <div key={r.id} className="rm-card" style={{
                '--accent':tc.accent,'--dim':tc.dim,'--border':tc.border,
              } as React.CSSProperties}>
                <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-start', marginBottom:6 }}>
                  <div className="rm-code">{r.code}</div>
                  <div style={{ display:'flex', flexDirection:'column', alignItems:'flex-end', gap:4 }}>
                    <div className="rm-type-badge">{tc.label}</div>
                    {isAdmin && (
                      <div style={{ display:'flex', gap:4, marginTop:2 }}>
                        <button onClick={() => setEditing(r)}
                          style={{ padding:'2px 8px', border:'none', borderRadius:4, background:'#1e3a5f',
                            color:'#60a5fa', cursor:'pointer', fontSize:10, fontFamily:"'IBM Plex Sans',sans-serif" }}>
                          Επεξ.
                        </button>
                        <button onClick={() => handleDelete(r)}
                          style={{ padding:'2px 8px', border:'none', borderRadius:4, background:'#450a0a',
                            color:'#f87171', cursor:'pointer', fontSize:10, fontFamily:"'IBM Plex Sans',sans-serif" }}>
                          Διαγρ.
                        </button>
                      </div>
                    )}
                  </div>
                </div>
                <div className="rm-name">{r.name}</div>
                <div style={{ display:'flex', gap:16, marginBottom:10 }}>
                  <div>
                    <div className="rm-stat-val">{r.capacity}</div>
                    <div className="rm-stat-lbl">Χωρητικότητα</div>
                  </div>
                </div>
                <div className="rm-tags">
                  {r.availableForSemester && <span className="rm-tag rm-tag-on">Εξαμηνιαίο</span>}
                  {r.availableForExams    && <span className="rm-tag rm-tag-on">Εξεταστική</span>}
                  {r.hasProjector         && <span className="rm-tag">Projector</span>}
                  {r.hasComputers         && <span className="rm-tag">Η/Υ</span>}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {editing !== false && (
        <RoomModal
          room={editing}
          onClose={() => setEditing(false)}
          onSaved={() => { load(); setToast('Η αίθουσα αποθηκεύτηκε.'); }}
        />
      )}
    </div>
  );
}
