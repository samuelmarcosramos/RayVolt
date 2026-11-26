import { useCallback, useEffect, useRef, useState } from 'react'
import { getScooterStates, subscribeToScooters } from '../../api/scooterService'
import StatCard from '../reusable/StatCard'
import FleetIntro from './FleetIntro'
import {
  batteryColor,
  mergeSnapshot,
  STATUS_LABELS,
  type ScooterLive,
  type ScooterStatus,
  summarize,
  timeAgo,
  upsert,
  where,
} from '../../utils/fleet'
import './Fleet.css'

type Connection = 'connecting' | 'live' | 'reconnecting'

type Filter = 'ALL' | ScooterStatus

const FILTERS: Filter[] = ['ALL', 'AVAILABLE', 'IN_USE', 'RESERVED', 'MAINTENANCE']

function FleetPage() {
  const [scooters, setScooters] = useState<ScooterLive[]>([])
  const [loadError, setLoadError] = useState(false)
  const [connection, setConnection] = useState<Connection>('connecting')
  const [now, setNow] = useState(() => Date.now())
  const [filter, setFilter] = useState<Filter>('ALL')
  const [intro, setIntro] = useState(true)
  const titleRef = useRef<HTMLHeadingElement>(null)
  const endIntro = useCallback(() => setIntro(false), [])

  useEffect(() => {
    const controller = new AbortController()
    const unsubscribe = subscribeToScooters({
      onUpdate: (update) => setScooters((current) => upsert(current, update)),
      onOpen: () => setConnection('live'),
      onError: () => setConnection('reconnecting'),
    })

    getScooterStates(controller.signal)
      .then((initial) => setScooters((current) => mergeSnapshot(current, initial)))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return
        }
        setLoadError(true)
      })

    return () => {
      controller.abort()
      unsubscribe()
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
    <section aria-labelledby="fleet-title" className={intro ? 'fleet fleet--intro' : 'fleet fleet--entered'}>
      <div className="page-header">
        <h1 id="fleet-title" ref={titleRef}>
          <span className="fleet-letter">F</span>
          <span className="fleet-letter">l</span>
          <span className="fleet-e">e</span>
          <span className="fleet-e">e</span>
          <span className="fleet-letter">t</span>
        </h1>
        {!loadError && (
          <span className={`live live--${connection}`} role="status">
            {connection === 'reconnecting' ? 'Reconnecting…' : connection === 'live' ? 'Live' : 'Connecting…'}
          </span>
        )}
      </div>
      {intro && <FleetIntro title={titleRef} onDone={endIntro} />}

      {loadError && (
        <p className="error-message" role="alert">
          Could not load the fleet. Try again later.
        </p>
      )}

      {!loadError && scooters.length === 0 && <p className="empty">No scooters yet.</p>}

      {scooters.length > 0 && (
        <>
          <dl className="summary">
            <StatCard label="Scooters" value={summary.total} />
            <StatCard label="Available" value={summary.available} hover="green" />
            <StatCard label="In use" value={summary.inUse} />
            <StatCard
              label="Maintenance"
              value={summary.maintenance}
              hover="orange"
              note={summary.faults > 0 && `${summary.faults} ${summary.faults === 1 ? 'fault' : 'faults'}`}
            />
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
                <tr key={scooter.scooterId}>
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
                        <span className="battery-bar" aria-hidden="true">
                          <span style={{ width: `${scooter.battery}%`, background: batteryColor(scooter.battery) }} />
                        </span>
                        {scooter.battery}%
                      </span>
                    )}
                  </td>
                  <td>
                    {scooter.positionStatus === 'AT_HUB' && scooter.hubName !== null ? (
                      <>
                        At <strong className="hub-name">{scooter.hubName}</strong>
                      </>
                    ) : (
                      where(scooter)
                    )}
                  </td>
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
