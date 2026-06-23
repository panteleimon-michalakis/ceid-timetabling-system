import { useMemo, useState } from 'react';
import type { TeacherRole } from '../types';
import { TEACHER_ROLE_LABELS } from '../types';

// ─── Public types ───────────────────────────────────────────────────────────────

export interface DualListItem {
  id: number;
  label: string;       // κύριο display (π.χ. όνομα teacher, ή course code)
  sublabel?: string;   // δευτερεύον (π.χ. course name, τμήμα)
}

export interface DualListSelection {
  id: number;
  role: TeacherRole;
}

export interface DualListPickerProps {
  available: DualListItem[];                 // όλο το universe
  selected: DualListSelection[];             // τρέχουσα επιλογή με ρόλους
  onChange: (next: DualListSelection[]) => void;
  roleOptions: TeacherRole[];
  // ο γονιός ορίζει τον default ρόλο όταν προστίθεται νέο item:
  defaultRoleForNew: (current: DualListSelection[]) => TeacherRole;
  // optional advisory warning (π.χ. PRIMARY check)· επιστρέφει μήνυμα ή null:
  warning?: (selected: DualListSelection[]) => string | null;
  labels?: {
    availableTitle?: string;   // default 'Διαθέσιμοι'
    selectedTitle?: string;    // default 'Επιλεγμένοι'
    searchPlaceholder?: string;// default 'Αναζήτηση…'
    emptySelected?: string;    // default 'Καμία επιλογή'
  };
  disabled?: boolean;
}

// ─── Styles (ευθυγραμμισμένα με το dark theme της εφαρμογής) ─────────────────────

const panel: React.CSSProperties = {
  background: '#0d1b2e', border: '1px solid #1a2744', borderRadius: '10px',
  overflow: 'hidden', display: 'flex', flexDirection: 'column', minHeight: 0,
};
const panelHead: React.CSSProperties = {
  padding: '8px 12px', fontSize: '11px', color: '#94a3b8', fontWeight: 600,
  letterSpacing: '0.3px', borderBottom: '1px solid #1a2744',
  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
};
const searchInput: React.CSSProperties = {
  width: '100%', boxSizing: 'border-box', padding: '6px 10px', borderRadius: '0',
  border: 'none', borderBottom: '1px solid #1a2744', background: '#080f1a',
  color: '#e2e8f0', fontSize: '12px', fontFamily: "'IBM Plex Sans', sans-serif", outline: 'none',
};
const listBox: React.CSSProperties = {
  overflowY: 'auto', maxHeight: '220px', minHeight: '120px',
  display: 'flex', flexDirection: 'column',
};
const rowSt: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '8px',
  padding: '6px 10px', borderBottom: '1px solid #0f1f38',
};
const labelCol: React.CSSProperties = { flex: 1, minWidth: 0 };
const labelMain: React.CSSProperties = {
  fontSize: '12px', color: '#e2e8f0', fontWeight: 500,
  overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
};
const labelSub: React.CSSProperties = {
  fontSize: '10px', color: '#475569', marginTop: '1px',
  overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
};
const addBtn: React.CSSProperties = {
  padding: '3px 9px', border: 'none', borderRadius: '5px', cursor: 'pointer',
  background: '#1e3a5f', color: '#60a5fa', fontSize: '11px', fontWeight: 500,
  fontFamily: "'IBM Plex Sans', sans-serif", flexShrink: 0,
};
const removeBtn: React.CSSProperties = {
  padding: '3px 9px', border: 'none', borderRadius: '5px', cursor: 'pointer',
  background: '#450a0a', color: '#f87171', fontSize: '11px', fontWeight: 500,
  fontFamily: "'IBM Plex Sans', sans-serif", flexShrink: 0,
};
const roleSelect: React.CSSProperties = {
  padding: '3px 6px', borderRadius: '5px', border: '1px solid #1a2744',
  background: '#080f1a', color: '#e2e8f0', fontSize: '11px',
  fontFamily: "'IBM Plex Sans', sans-serif", flex: '1 1 0', minWidth: 0,
};
// Selected-panel row: ΚΑΘΕΤΟ layout (γρ.1 label/sublabel πλήρους πλάτους χωρίς
// clipping· γρ.2 role <select> flex:1 + κουμπί αφαίρεσης) ώστε το label να μην
// κόβεται από το <select> σε στενό panel.
const selRowSt: React.CSSProperties = {
  display: 'flex', flexDirection: 'column', gap: '6px',
  padding: '6px 10px', borderBottom: '1px solid #0f1f38',
};
const selLabelMain: React.CSSProperties = {
  fontSize: '13px', color: '#e2e8f0', fontWeight: 600, overflowWrap: 'anywhere',
};
const selLabelSub: React.CSSProperties = {
  fontSize: '11px', color: '#475569', marginTop: '1px', overflowWrap: 'anywhere',
};
const selActionsRow: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '8px',
};
const emptySt: React.CSSProperties = {
  padding: '16px', textAlign: 'center', color: '#334155', fontSize: '12px',
};
const warnBanner: React.CSSProperties = {
  background: '#78350f22', border: '1px solid #b45309', borderRadius: '7px',
  padding: '7px 12px', color: '#f59e0b', fontSize: '12px', marginBottom: '10px',
};

// ─── Component (controlled, presentation-only — κανένα fetch/save) ───────────────

export default function DualListPicker({
  available, selected, onChange, roleOptions,
  defaultRoleForNew, warning, labels, disabled = false,
}: DualListPickerProps) {
  const [availSearch, setAvailSearch] = useState('');
  const [selSearch, setSelSearch] = useState('');

  const availableTitle = labels?.availableTitle ?? 'Διαθέσιμοι';
  const selectedTitle = labels?.selectedTitle ?? 'Επιλεγμένοι';
  const searchPlaceholder = labels?.searchPlaceholder ?? 'Αναζήτηση…';
  const emptySelected = labels?.emptySelected ?? 'Καμία επιλογή';

  const selectedIds = useMemo(() => new Set(selected.map(s => s.id)), [selected]);
  const itemById = useMemo(() => {
    const m = new Map<number, DualListItem>();
    for (const it of available) m.set(it.id, it);
    return m;
  }, [available]);

  const availableFiltered = useMemo(() => {
    const q = availSearch.trim().toLowerCase();
    return available
      .filter(it => !selectedIds.has(it.id))
      .filter(it => !q || it.label.toLowerCase().includes(q)
        || (it.sublabel ?? '').toLowerCase().includes(q));
  }, [available, selectedIds, availSearch]);

  const selectedFiltered = useMemo(() => {
    const q = selSearch.trim().toLowerCase();
    if (!q) return selected;
    return selected.filter(s => {
      const it = itemById.get(s.id);
      const label = it?.label ?? String(s.id);
      const sub = it?.sublabel ?? '';
      return label.toLowerCase().includes(q) || sub.toLowerCase().includes(q);
    });
  }, [selected, itemById, selSearch]);

  const warn = warning ? warning(selected) : null;

  function add(id: number) {
    if (disabled || selectedIds.has(id)) return;
    onChange([...selected, { id, role: defaultRoleForNew(selected) }]);
  }
  function remove(id: number) {
    if (disabled) return;
    onChange(selected.filter(s => s.id !== id));
  }
  function changeRole(id: number, role: TeacherRole) {
    if (disabled) return;
    onChange(selected.map(s => (s.id === id ? { ...s, role } : s)));
  }

  return (
    <div>
      {warn && <div style={warnBanner}>⚠ {warn}</div>}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
        {/* ── Available ── */}
        <div style={panel}>
          <div style={panelHead}>
            <span>{availableTitle}</span>
            <span style={{ color: '#334155', fontFamily: 'JetBrains Mono, monospace' }}>
              {availableFiltered.length}
            </span>
          </div>
          <input
            style={searchInput} value={availSearch}
            onChange={e => setAvailSearch(e.target.value)}
            placeholder={searchPlaceholder} aria-label={`${searchPlaceholder} — ${availableTitle}`}
          />
          <div style={listBox}>
            {availableFiltered.length === 0 ? (
              <div style={emptySt}>—</div>
            ) : availableFiltered.map(it => (
              <div key={it.id} style={rowSt}>
                <div style={labelCol}>
                  <div style={labelMain}>{it.label}</div>
                  {it.sublabel && <div style={labelSub}>{it.sublabel}</div>}
                </div>
                <button type="button" style={addBtn} onClick={() => add(it.id)} disabled={disabled}>
                  + Προσθήκη
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* ── Selected ── */}
        <div style={panel}>
          <div style={panelHead}>
            <span>{selectedTitle}</span>
            <span style={{ color: '#334155', fontFamily: 'JetBrains Mono, monospace' }}>
              {selected.length}
            </span>
          </div>
          <input
            style={searchInput} value={selSearch}
            onChange={e => setSelSearch(e.target.value)}
            placeholder={searchPlaceholder} aria-label={`${searchPlaceholder} — ${selectedTitle}`}
          />
          <div style={listBox}>
            {selected.length === 0 ? (
              <div style={emptySt}>{emptySelected}</div>
            ) : selectedFiltered.map(s => {
              const it = itemById.get(s.id);
              return (
                <div key={s.id} style={selRowSt}>
                  <div>
                    <div style={selLabelMain}>{it?.label ?? `#${s.id}`}</div>
                    {it?.sublabel && <div style={selLabelSub}>{it.sublabel}</div>}
                  </div>
                  <div style={selActionsRow}>
                    <select
                      style={roleSelect} value={s.role} disabled={disabled}
                      aria-label="Ρόλος"
                      onChange={e => changeRole(s.id, e.target.value as TeacherRole)}
                    >
                      {roleOptions.map(r => (
                        <option key={r} value={r}>{TEACHER_ROLE_LABELS[r]}</option>
                      ))}
                    </select>
                    <button type="button" style={{ ...removeBtn, flex: '0 0 auto' }} onClick={() => remove(s.id)} disabled={disabled}>
                      × Αφαίρεση
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
