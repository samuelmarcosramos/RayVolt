import { useEffect, useState } from 'react'
import {
  batteryLevel,
  STATUS_LABELS,
  type ScooterLive,
  type ScooterStatus,
  summarize,
  timeAgo,
  upsert,
  where,
} from '../fleet'

type Connection = 'connecting' | 'live' | 'reconnecting'

type Filter = 'ALL' | ScooterStatus

const FILTERS: Filter[] = ['ALL', 'AVAILABLE', 'IN_USE', 'RESERVED', 'MAINTENANCE']

function FleetPage() {
  const [scooters, setScooters] = useState<ScooterLive[]>([])
  const [loadError, setLoadError] = useState(false)
  const [connection, setConnection] = useState<Connection>('connecting')
  const [now, setNow] = useState(() => Date.now())
  const [filter, setFilter] = useState<Filter>('ALL')
  const [versions, setVersions] = useState<Record<number, number>>({})

  useEffect(() => {
    const controller = new AbortController()
    let stream: EventSource | null = null

    fetch('/api/scooters/state', { signal: controller.signal })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`)
        }
        return response.json() as Promise<ScooterLive[]>
      })
      .then((initial) => {
        setScooters([...initial].sort((a, b) => a.scooterId - b.scooterId))
        stream = new EventSource('/api/scooters/stream')
        stream.onopen = () => setConnection('live')
        stream.onerror = () => setConnection('reconnecting')
        stream.onmessage = (event: MessageEvent<string>) => {
          const update = JSON.parse(event.data) as ScooterLive
          setScooters((current) => upsert(current, update))
          setVersions((current) => ({ ...current, [update.scooterId]: (current[update.scooterId] ?? 0) + 1 }))
        }
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return
        }
        setLoadError(true)
      })

    return () => {
      controller.abort()
      stream?.close()
    }
  }, [])

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  const summary = summarize(scooters)
  const counts: Record<Filter, number> = {
    ALL: summary.total,
    AVAILABLE: summary.available,
    IN_USE: summary.inUse,
    RESERVED: summary.reserved,
    MAINTENANCE: summary.maintenance,
  }
  const shown = filter === 'ALL' ? scooters : scooters.filter((scooter) => scooter.status === filter)

  return (
    <section aria-labelledby="fleet-title">
      <div className="page-header">
        <h1 id="fleet-title">Fleet</h1>
        {!loadError && (
          <span className={`live live--${connection}`} role="status">
            {connection === 'reconnecting' ? 'Reconnecting…' : connection === 'live' ? 'Live' : 'Connecting…'}
          </span>
        )}
      </div>

      {loadError && (
        <p className="error-message" role="alert">
          Could not load the fleet. Try again later.
        </p>
      )}

      {!loadError && scooters.length === 0 && <p className="empty">No scooters yet.</p>}

      {scooters.length > 0 && (
        <>
          <dl className="summary">
            <div className="summary-card">
              <dt>Scooters</dt>
              <dd>{summary.total}</dd>
            </div>
            <div className="summary-card summary-card--available">
              <dt>Available</dt>
              <dd>{summary.available}</dd>
            </div>
            <div className="summary-card summary-card--in-use">
              <dt>In use</dt>
              <dd>{summary.inUse}</dd>
            </div>
            <div className="summary-card summary-card--maintenance">
              <dt>Maintenance</dt>
              <dd>
                {summary.maintenance}
                {summary.faults > 0 && (
                  <span className="summary-note">
                    {summary.faults} {summary.faults === 1 ? 'fault' : 'faults'}
                  </span>
                )}
              </dd>
            </div>
          </dl>

          <div className="filters" role="group" aria-label="Filter by status">
            {FILTERS.map((option) => (
              <button
                key={option}
                type="button"
                className="filter"
                aria-pressed={filter === option}
                onClick={() => setFilter(option)}
              >
                {option === 'ALL' ? 'All' : STATUS_LABELS[option]}
                <span className="filter-count">{counts[option]}</span>
              </button>
            ))}
          </div>

        <div className="table-wrap">
          <table className="fleet-table">
            <thead>
              <tr>
                <th scope="col">Scooter</th>
                <th scope="col">Status</th>
                <th scope="col">Battery</th>
                <th scope="col">Where</th>
                <th scope="col">Position</th>
                <th scope="col">Last seen</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((scooter) => (
                <tr
                  key={`${scooter.scooterId}-${versions[scooter.scooterId] ?? 0}`}
                  className={versions[scooter.scooterId] ? 'flash' : undefined}
                >
                  <th scope="row">#{scooter.scooterId}</th>
                  <td>
                    <span className={`status status--${scooter.status.toLowerCase()}`}>
                      {STATUS_LABELS[scooter.status]}
                    </span>
                    {scooter.fault && <span className="fault">Fault</span>}
                  </td>
                  <td>
                    {scooter.battery === null ? (
                      '—'
                    ) : (
                      <span className="battery">
                        <span className={`battery-bar battery-bar--${batteryLevel(scooter.battery)}`} aria-hidden="true">
                          <span style={{ width: `${scooter.battery}%` }} />
                        </span>
                        {scooter.battery}%
                      </span>
                    )}
                  </td>
                  <td>{where(scooter)}</td>
                  <td className="muted">
                    {scooter.lat === null || scooter.lon === null
                      ? '—'
                      : `${scooter.lat.toFixed(4)}, ${scooter.lon.toFixed(4)}`}
                  </td>
                  <td className="muted">{timeAgo(scooter.lastSeen, now)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {shown.length === 0 && <p className="empty">No scooters with this status.</p>}
        </div>
        </>
      )}
    </section>
  )
}

export default FleetPage
