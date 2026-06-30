import type { ReactNode } from 'react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import App from './App';
import ErrorBoundary from './components/ErrorBoundary';

afterEach(() => {
  cleanup();
});

describe('smoke tests', () => {
  it('renders the App without crashing', () => {
    const { container } = render(<App />);
    expect(container.firstChild).not.toBeNull();
  });

  it('ErrorBoundary renders fallback UI when a child throws', () => {
    // Το throw στο render πιάνεται από το boundary· καταπνίγουμε το αναμενόμενο
    // React error log ώστε το output του test να μένει καθαρό.
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    function Boom(): ReactNode {
      throw new Error('boom');
    }

    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );

    expect(screen.getByText('Κάτι πήγε στραβά')).toBeTruthy();
    errorSpy.mockRestore();
  });
});
