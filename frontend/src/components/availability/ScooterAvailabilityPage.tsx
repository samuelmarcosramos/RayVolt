import { useEffect, useRef, useState } from 'react'
import './ScooterAvailabilityPage.css'

type Hub = {
  id: number
  name: string
}

type Scooter = {
  id: number
  type: string
  hubId: number
  status: string
  batteryPercent: number
}

type RequestState = 'idle' | 'loading' | 'ready' | 'error'

const SCOOTER_TYPES = ['Standard', 'Cargo', 'Patrol']
const DEFAULT_MINIMUM_BATTERY_PERCENT = 10

function isHub(value: unknown): value is Hub {
  return (
    typeof value === 'object' &&
    value !== null &&
    'id' in value &&
    typeof value.id === 'number' &&
    'name' in value &&
    typeof value.name === 'string'
  )
}

function isScooter(value: unknown): value is Scooter {
  return (
    typeof value === 'object' &&
    value !== null &&
    'id' in value &&
    typeof value.id === 'number' &&
    'type' in value &&
    typeof value.type === 'string' &&
    'hubId' in value &&
    typeof value.hubId === 'number' &&
    'status' in value &&
    typeof value.status === 'string' &&
    'batteryPercent' in value &&
    typeof value.batteryPercent === 'number'
  )
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Unexpected service error.'
}

async function getJson<T>(
  url: string,
  signal: AbortSignal,
  isExpectedData: (value: unknown) => value is T,
): Promise<T> {
  const response = await fetch(url, { signal })
  if (!response.ok) {
    throw new Error(`The service responded with HTTP ${response.status}.`)
  }

  const body: unknown = await response.json()
  if (!isExpectedData(body)) {
    throw new Error('The service returned an unexpected response.')
  }
  return body
}

function isHubList(value: unknown): value is Hub[] {
  return Array.isArray(value) && value.every(isHub)
}

function isScooterList(value: unknown): value is Scooter[] {
  return Array.isArray(value) && value.every(isScooter)
}

function ScooterAvailabilityPage() {
  const [hubs, setHubs] = useState<Hub[]>([])
  const [hubState, setHubState] = useState<RequestState>('loading')
  const [hubError, setHubError] = useState('')
  const [selectedHubId, setSelectedHubId] = useState('')
  const [selectedType, setSelectedType] = useState('')
  const [scooters, setScooters] = useState<Scooter[]>([])
  const [searchState, setSearchState] = useState<RequestState>('idle')
  const [searchError, setSearchError] = useState('')
  const availabilityRequest = useRef<AbortController | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    void getJson('/api/hubs', controller.signal, isHubList)
      .then((availableHubs) => {
        setHubs(availableHubs)
        setHubState('ready')
      })
      .catch((error: unknown) => {
        if (!isAbortError(error)) {
          setHubError(errorMessage(error))
          setHubState('error')
        }
      })

    return () => controller.abort()
  }, [])

  useEffect(
    () => () => {
      availabilityRequest.current?.abort()
    },
    [],
  )

  const searchScooters = (hubId: string, type: string) => {
    availabilityRequest.current?.abort()
    setScooters([])
    setSearchError('')

    if (!hubId) {
      setSearchState('idle')
      return
    }

    const controller = new AbortController()
    availabilityRequest.current = controller
    const parameters = new URLSearchParams({
      hubId,
      minBatteryPercent: String(DEFAULT_MINIMUM_BATTERY_PERCENT),
    })
    if (type) {
      parameters.set('type', type)
    }

    setSearchState('loading')
    void getJson(
      `/api/scooters/available?${parameters.toString()}`,
      controller.signal,
      isScooterList,
    )
      .then((availableScooters) => {
        setScooters(availableScooters)
        setSearchState('ready')
      })
      .catch((error: unknown) => {
        if (!isAbortError(error)) {
          setSearchError(errorMessage(error))
          setSearchState('error')
        }
      })
  }

  const handleHubChange = (hubId: string) => {
    setSelectedHubId(hubId)
    searchScooters(hubId, selectedType)
  }

  const handleTypeChange = (type: string) => {
    setSelectedType(type)
    searchScooters(selectedHubId, type)
  }

  const retrySearch = () => searchScooters(selectedHubId, selectedType)

  return (
    <section className="availability" aria-labelledby="availability-title">
      <p className="eyebrow">Move your workplace forward</p>
      <h1 id="availability-title">Find your ride.</h1>
      <p className="intro">
        Choose a hub and find an available scooter with the range you need.
      </p>

      <div className="availability-panel">
        <div className="availability-fields">
          <label className="availability-field">
            <span>Hub</span>
            <select
              aria-label="Hub"
              disabled={hubState !== 'ready' || hubs.length === 0}
              onChange={(event) => handleHubChange(event.target.value)}
              value={selectedHubId}
            >
              <option value="">
                {hubState === 'loading' ? 'Loading hubs...' : 'Choose a hub'}
              </option>
              {hubs.map((hub) => (
                <option key={hub.id} value={hub.id}>
                  {hub.name}
                </option>
              ))}
            </select>
          </label>

          <label className="availability-field">
            <span>Scooter type</span>
            <select
              aria-label="Scooter type"
              disabled={hubState !== 'ready' || hubs.length === 0}
              onChange={(event) => handleTypeChange(event.target.value)}
              value={selectedType}
            >
              <option value="">Any type</option>
              {SCOOTER_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </label>
        </div>

        <p className="availability-hint">
          Showing available scooters with at least {DEFAULT_MINIMUM_BATTERY_PERCENT}% battery.
        </p>

        {hubState === 'loading' && (
          <p className="availability-message" role="status">Loading hubs...</p>
        )}
        {hubState === 'error' && (
          <p className="availability-message availability-error" role="alert">
            Could not load hubs: {hubError}
          </p>
        )}
        {hubState === 'ready' && hubs.length === 0 && (
          <p className="availability-message" role="status">No hubs are available yet.</p>
        )}
        {searchState === 'loading' && (
          <p className="availability-message" role="status">Finding available scooters...</p>
        )}
        {searchState === 'error' && (
          <div className="availability-error-row">
            <p className="availability-message availability-error" role="alert">
              Could not load scooters: {searchError}
            </p>
            <button className="availability-retry" onClick={retrySearch} type="button">
              Try again
            </button>
          </div>
        )}
        {searchState === 'ready' && scooters.length === 0 && (
          <p className="availability-message" role="status">
            No scooters available at this hub.
          </p>
        )}
        {searchState === 'ready' && scooters.length > 0 && (
          <ul className="availability-list" aria-label="Available scooters">
            {scooters.map((scooter) => (
              <li className="availability-scooter" key={scooter.id}>
                <div>
                  <span className="availability-scooter-id">Scooter #{scooter.id}</span>
                  <span className="availability-scooter-type">{scooter.type}</span>
                </div>
                <span className="availability-battery">{scooter.batteryPercent}%</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

export default ScooterAvailabilityPage
