import { useCallback, useEffect, useState } from 'react'
import './App.css'

type BackendState =
  | { kind: 'loading' }
  | { kind: 'connected'; status: string }
  | { kind: 'error'; message: string }

const HEALTH_ENDPOINT = import.meta.env.VITE_API_HEALTH_URL || '/api/health'

function isHealthResponse(value: unknown): value is { status: string } {
  return (
    typeof value === 'object' &&
    value !== null &&
    'status' in value &&
    typeof value.status === 'string'
  )
}

function App() {
  const [backend, setBackend] = useState<BackendState>({ kind: 'loading' })

  const checkBackend = useCallback(async (signal?: AbortSignal) => {
    try {
      const response = await fetch(HEALTH_ENDPOINT, { signal })
      if (!response.ok) {
        throw new Error(`The backend responded with HTTP ${response.status}.`)
      }

      const body: unknown = await response.json()
      if (!isHealthResponse(body)) {
        throw new Error('The backend returned an unexpected health response.')
      }

      setBackend({ kind: 'connected', status: body.status })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return
      }

      setBackend({
        kind: 'error',
        message:
          error instanceof Error
            ? error.message
            : 'Could not connect to the backend.',
      })
    }
  }, [])

  const retryBackend = () => {
    setBackend({ kind: 'loading' })
    void checkBackend()
  }

  useEffect(() => {
    const controller = new AbortController()
    void checkBackend(controller.signal)
    return () => controller.abort()
  }, [checkBackend])

  return (
    <main className="page-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="RayVolt home">
          <span className="brand-mark" aria-hidden="true">R</span>
          <span>RayVolt</span>
        </a>
        <span className="service-label">Fleet service</span>
      </header>

      <section className="welcome" aria-labelledby="welcome-title">
        <p className="eyebrow">Move your workplace forward</p>
        <h1 id="welcome-title">Your site, in motion.</h1>
        <p className="intro">
          Shared e-scooters for the people who keep Valdren Works moving.
          Find a ride, get where you need to be, and leave the fleet to us.
        </p>

        <section className="connection-card" aria-label="Backend connection">
          <div className="connection-copy">
            <span className="connection-label">Service connection</span>
            {backend.kind === 'loading' && (
              <p role="status">Connecting to the RayVolt service...</p>
            )}
            {backend.kind === 'connected' && (
              <p role="status">
                Connected <span className="health-value">({backend.status})</span>
              </p>
            )}
            {backend.kind === 'error' && (
              <p className="error-message" role="alert">
                Service unavailable: {backend.message}
              </p>
            )}
          </div>
          <span
            className={`connection-indicator connection-indicator--${backend.kind}`}
            aria-hidden="true"
          />
          {backend.kind === 'error' && (
            <button
              className="retry-button"
              onClick={retryBackend}
              type="button"
            >
              Try again
            </button>
          )}
        </section>
      </section>

      <footer className="footer">
        <span>RayVolt</span>
        <span>Reliable rides. Ready when you are.</span>
      </footer>
    </main>
  )
}

export default App
