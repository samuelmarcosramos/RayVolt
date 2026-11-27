export type Hub = {
  id: number
  name: string
}

export type AvailableScooter = {
  id: number
  type: string
  hubId: number
  status: string
  batteryPercent: number | null
}

function isHub(value: unknown): value is Hub {
  return typeof value === 'object' && value !== null &&
    'id' in value && typeof value.id === 'number' &&
    'name' in value && typeof value.name === 'string'
}

function isScooter(value: unknown): value is AvailableScooter {
  return typeof value === 'object' && value !== null &&
    'id' in value && typeof value.id === 'number' &&
    'type' in value && typeof value.type === 'string' &&
    'hubId' in value && typeof value.hubId === 'number' &&
    'status' in value && typeof value.status === 'string' &&
    'batteryPercent' in value &&
    (typeof value.batteryPercent === 'number' || value.batteryPercent === null)
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

export function getHubs(signal: AbortSignal): Promise<Hub[]> {
  return getJson('/api/hubs', signal, (value): value is Hub[] =>
    Array.isArray(value) && value.every(isHub))
}

export function getAvailableScooters(
  hubId: string,
  type: string,
  signal: AbortSignal,
): Promise<AvailableScooter[]> {
  const parameters = new URLSearchParams({ hubId })
  if (type) parameters.set('type', type)
  return getJson(`/api/scooters/available?${parameters.toString()}`, signal, (value): value is AvailableScooter[] =>
    Array.isArray(value) && value.every(isScooter))
}
