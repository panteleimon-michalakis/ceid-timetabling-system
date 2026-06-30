import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';

interface ErrorBoundaryProps {
  children: ReactNode;
  /** Προαιρετικό custom fallback· αν λείπει, εμφανίζεται το default UI. */
  fallback?: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

/**
 * Class-based Error Boundary: πιάνει render-time exceptions σε ολόκληρο το δέντρο των
 * children και εμφανίζει fallback UI αντί για «λευκή οθόνη». Τα error boundaries ΠΡΕΠΕΙ
 * να είναι class components — δεν υπάρχει hook ισοδύναμο για {@code componentDidCatch} /
 * {@code getDerivedStateFromError}.
 */
export default class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Καταγραφή για debugging· σε παραγωγή θα μπορούσε να σταλεί σε logging service.
    console.error('ErrorBoundary caught an error:', error, info.componentStack);
  }

  private handleReload = (): void => {
    window.location.reload();
  };

  render(): ReactNode {
    if (!this.state.hasError) {
      return this.props.children;
    }
    if (this.props.fallback !== undefined) {
      return this.props.fallback;
    }
    return (
      <div
        role="alert"
        style={{
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '1rem',
          padding: '2rem',
          background: '#080f1a',
          color: '#e2e8f0',
          fontFamily: "'IBM Plex Sans', sans-serif",
          textAlign: 'center',
        }}
      >
        <h1 style={{ margin: 0, fontSize: '1.5rem', color: '#f87171' }}>
          Κάτι πήγε στραβά
        </h1>
        <p style={{ margin: 0, maxWidth: 480, color: '#94a3b8' }}>
          Παρουσιάστηκε ένα απρόσμενο σφάλμα στην εφαρμογή. Δοκιμάστε να επαναφορτώσετε τη
          σελίδα. Αν το πρόβλημα επιμένει, επικοινωνήστε με τον διαχειριστή.
        </p>
        {this.state.error?.message && (
          <pre
            style={{
              margin: 0,
              padding: '0.75rem 1rem',
              maxWidth: 600,
              overflowX: 'auto',
              background: '#0d1b2e',
              border: '1px solid #1a2744',
              borderRadius: 8,
              color: '#cbd5e1',
              fontSize: '0.8rem',
            }}
          >
            {this.state.error.message}
          </pre>
        )}
        <button
          onClick={this.handleReload}
          style={{
            padding: '0.5rem 1.25rem',
            border: 'none',
            borderRadius: 8,
            background: '#1d4ed8',
            color: '#fff',
            cursor: 'pointer',
            fontFamily: "'IBM Plex Sans', sans-serif",
          }}
        >
          Επαναφόρτωση σελίδας
        </button>
      </div>
    );
  }
}
