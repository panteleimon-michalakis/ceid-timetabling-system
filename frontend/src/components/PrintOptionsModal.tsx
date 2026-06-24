import { useState } from 'react';
import type { CSSProperties } from 'react';
import type { PrintGroupBy } from '../utils/printTimetable';

export interface PrintRequest {
  groupBy: PrintGroupBy;
  selectedKeys: string[];        // ποιες οντότητες (default: όλες)
  colorByYear: boolean;          // default true
  showType: boolean;             // weekly only· default true
  showSemesterBadge: boolean;    // default true
}

interface PrintOptionsModalProps {
  open: boolean;
  onClose: () => void;
  showTypeToggle: boolean;        // true στο weekly, false στο exam
  available: Record<PrintGroupBy, { key: string; label: string }[]>;
  onPrint: (opts: PrintRequest) => void;
}

const GROUP_ORDER: PrintGroupBy[] = ['semester', 'room', 'teacher'];
const GROUP_LABELS: Record<PrintGroupBy, string> = {
  semester: 'Ανά εξάμηνο',
  room: 'Ανά αίθουσα',
  teacher: 'Ανά καθηγητή',
};

export default function PrintOptionsModal({ open, onClose, showTypeToggle, available, onPrint }: PrintOptionsModalProps) {
  const [groupBy, setGroupByState] = useState<PrintGroupBy>('semester');
  const [selectedKeys, setSelectedKeys] = useState<string[]>([]);
  const [colorByYear, setColorByYear] = useState(true);
  const [showType, setShowType] = useState(true);
  const [showSemesterBadge, setShowSemesterBadge] = useState(true);
  const [prevOpen, setPrevOpen] = useState(false);

  // Reset επιλεγμένων οντοτήτων σε «όλα» όταν ανοίγει το modal — render-phase adjust
  // (React docs: «adjusting state when a prop changes», χωρίς effect/cascading renders).
  if (open !== prevOpen) {
    setPrevOpen(open);
    if (open) setSelectedKeys(available[groupBy].map(o => o.key));
  }

  // Αλλαγή διάστασης → reset επιλογών σε «όλα» (στον handler, όχι σε effect).
  const setGroupBy = (g: PrintGroupBy) => {
    setGroupByState(g);
    setSelectedKeys(available[g].map(o => o.key));
  };

  if (!open) return null;

  const options = available[groupBy];
  const allSelected = options.length > 0 && selectedKeys.length === options.length;

  const toggleKey = (key: string) =>
    setSelectedKeys(prev => (prev.includes(key) ? prev.filter(k => k !== key) : [...prev, key]));
  const toggleAll = () =>
    setSelectedKeys(allSelected ? [] : options.map(o => o.key));

  const handlePrint = () => {
    if (selectedKeys.length === 0) return;
    onPrint({
      groupBy,
      selectedKeys,
      colorByYear,
      showType: showTypeToggle ? showType : false,
      showSemesterBadge,
    });
    onClose();
  };

  const pages = selectedKeys.length;

  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={modalStyle} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h2 style={{ fontSize: '1.2rem', margin: 0, color: '#5eead4' }}>🖨 Επιλογές εκτύπωσης</h2>
          <button onClick={onClose} title="Κλείσιμο" style={closeBtnStyle}>×</button>
        </div>

        {/* Ομαδοποίηση */}
        <div style={sectionStyle}>
          <div style={labelStyle}>Ομαδοποίηση</div>
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            {GROUP_ORDER.map((g) => (
              <label key={g} style={radioChipStyle(groupBy === g)}>
                <input
                  type="radio"
                  name="print-groupby"
                  checked={groupBy === g}
                  onChange={() => setGroupBy(g)}
                  style={{ accentColor: '#0f766e' }}
                />
                {GROUP_LABELS[g]}
              </label>
            ))}
          </div>
        </div>

        {/* Οντότητες */}
        <div style={sectionStyle}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
            <span style={labelStyle}>
              Οντότητες <span style={{ color: '#64748b', fontWeight: 400 }}>({pages} {pages === 1 ? 'σελίδα' : 'σελίδες'})</span>
            </span>
            <button onClick={toggleAll} disabled={options.length === 0} style={miniBtnStyle}>
              {allSelected ? 'Κανένα' : 'Επιλογή όλων'}
            </button>
          </div>
          <div style={listStyle}>
            {options.length === 0 ? (
              <div style={{ color: '#64748b', fontSize: '0.82rem', padding: '0.5rem' }}>Καμία διαθέσιμη οντότητα.</div>
            ) : (
              options.map((o) => (
                <label key={o.key} style={checkRowStyle}>
                  <input
                    type="checkbox"
                    checked={selectedKeys.includes(o.key)}
                    onChange={() => toggleKey(o.key)}
                    style={{ accentColor: '#0f766e' }}
                  />
                  <span style={{ color: '#e2e8f0', fontSize: '0.85rem' }}>{o.label}</span>
                </label>
              ))
            )}
          </div>
        </div>

        {/* Επιλογές εμφάνισης */}
        <div style={sectionStyle}>
          <div style={labelStyle}>Επιλογές εμφάνισης</div>
          <label style={checkRowStyle}>
            <input type="checkbox" checked={colorByYear} onChange={(e) => setColorByYear(e.target.checked)} style={{ accentColor: '#0f766e' }} />
            <span style={displayOptText}>Χρωματισμός ανά έτος</span>
          </label>
          {showTypeToggle && (
            <label style={checkRowStyle}>
              <input type="checkbox" checked={showType} onChange={(e) => setShowType(e.target.checked)} style={{ accentColor: '#0f766e' }} />
              <span style={displayOptText}>Ένδειξη τύπου Θ/Φ/Ε</span>
            </label>
          )}
          <label style={checkRowStyle}>
            <input type="checkbox" checked={showSemesterBadge} onChange={(e) => setShowSemesterBadge(e.target.checked)} style={{ accentColor: '#0f766e' }} />
            <span style={displayOptText}>Ένδειξη εξαμήνου στα κελιά</span>
          </label>
        </div>

        {/* Κουμπιά */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.6rem', marginTop: '1.2rem' }}>
          <button onClick={onClose} style={cancelBtnStyle}>Άκυρο</button>
          <button onClick={handlePrint} disabled={selectedKeys.length === 0} style={printBtnStyle(selectedKeys.length === 0)}>
            🖨 Εκτύπωση
          </button>
        </div>
      </div>
    </div>
  );
}

const overlayStyle: CSSProperties = {
  position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
  background: 'rgba(0,0,0,0.72)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  zIndex: 1000,
};

const modalStyle: CSSProperties = {
  background: '#111827', border: '1px solid #334155',
  borderRadius: '14px', padding: '1.5rem',
  width: '460px', maxHeight: '88vh', overflowY: 'auto',
};

const closeBtnStyle: CSSProperties = {
  border: 'none', borderRadius: '6px', background: '#334155', color: '#fff',
  cursor: 'pointer', padding: '0.2rem 0.6rem', fontSize: '1.1rem', lineHeight: 1,
};

const sectionStyle: CSSProperties = { marginBottom: '1.1rem' };

const labelStyle: CSSProperties = {
  color: '#94a3b8', fontSize: '0.8rem', fontWeight: 600,
  textTransform: 'uppercase', letterSpacing: '0.04em',
};

const listStyle: CSSProperties = {
  maxHeight: '220px', overflowY: 'auto',
  border: '1px solid #334155', borderRadius: '8px',
  padding: '0.35rem 0.5rem', background: '#0f172a',
  display: 'flex', flexDirection: 'column', gap: '0.15rem',
};

const checkRowStyle: CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '0.5rem',
  padding: '0.2rem 0.1rem', cursor: 'pointer',
};

const displayOptText: CSSProperties = { color: '#cbd5e1', fontSize: '0.85rem' };

const miniBtnStyle: CSSProperties = {
  border: '1px solid #334155', borderRadius: '6px', background: 'transparent',
  color: '#5eead4', cursor: 'pointer', padding: '0.2rem 0.6rem', fontSize: '0.75rem', fontWeight: 600,
};

const cancelBtnStyle: CSSProperties = {
  border: '1px solid #334155', borderRadius: '8px', background: 'transparent',
  color: '#cbd5e1', cursor: 'pointer', padding: '0.5rem 1.1rem', fontSize: '0.88rem', fontWeight: 600,
};

const radioChipStyle = (active: boolean): CSSProperties => ({
  display: 'flex', alignItems: 'center', gap: '0.4rem',
  padding: '0.4rem 0.7rem', borderRadius: '8px', cursor: 'pointer',
  border: `1px solid ${active ? '#0f766e' : '#334155'}`,
  background: active ? '#0f2e2b' : '#0f172a',
  color: active ? '#5eead4' : '#cbd5e1', fontSize: '0.85rem',
});

const printBtnStyle = (disabled: boolean): CSSProperties => ({
  border: 'none', borderRadius: '8px', padding: '0.5rem 1.1rem',
  background: disabled ? '#334155' : '#0f766e', color: '#fff',
  fontWeight: 600, cursor: disabled ? 'not-allowed' : 'pointer', fontSize: '0.88rem',
});
