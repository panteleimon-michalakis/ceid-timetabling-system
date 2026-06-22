import type { CSSProperties } from 'react';
import type { ValidationIssue } from '../types';

interface ValidationIssuesModalProps {
  severity: 'ERROR' | 'WARNING' | null;
  issues: ValidationIssue[];
  onClose: () => void;
}

export default function ValidationIssuesModal({ severity, issues, onClose }: ValidationIssuesModalProps) {
  if (severity === null) return null;

  const isError = severity === 'ERROR';
  const accent = isError ? '#f87171' : '#fbbf24';
  const title = isError ? 'Σφάλματα' : 'Προειδοποιήσεις';
  const emptyText = isError ? 'Δεν υπάρχουν σφάλματα.' : 'Δεν υπάρχουν προειδοποιήσεις.';

  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={modalStyle} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h2 style={{ fontSize: '1.2rem', margin: 0, color: accent }}>
            {title} <span style={{ color: '#94a3b8', fontWeight: 600 }}>({issues.length})</span>
          </h2>
          <button onClick={onClose} title="Κλείσιμο" style={closeBtnStyle}>×</button>
        </div>

        {issues.length === 0 ? (
          <div style={{ color: '#64748b', fontSize: '0.9rem' }}>{emptyText}</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {issues.map((issue, index) => (
              <div
                key={`${issue.code}-${issue.referenceId}-${index}`}
                style={{
                  borderLeft: `3px solid ${accent}`,
                  background: '#0f172a',
                  borderRadius: '6px',
                  padding: '0.55rem 0.7rem',
                }}
              >
                <div style={{ color: '#e2e8f0', fontSize: '0.88rem', lineHeight: 1.35 }}>
                  {issue.message}
                </div>
                <div style={{ color: '#64748b', fontSize: '0.7rem', marginTop: '0.2rem', fontFamily: 'JetBrains Mono, monospace' }}>
                  {issue.code}
                </div>
              </div>
            ))}
          </div>
        )}
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
  width: '560px', maxHeight: '80vh', overflowY: 'auto',
};

const closeBtnStyle: CSSProperties = {
  border: 'none', borderRadius: '6px', background: '#334155', color: '#fff',
  cursor: 'pointer', padding: '0.2rem 0.6rem', fontSize: '1.1rem', lineHeight: 1,
};
