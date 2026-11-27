import type { ScooterLive } from '../utils/fleet'

export async function getScooterStates(signal?: AbortSignal): Promise<ScooterLive[]> {
  const response = await fetch('/api/scooters/state', { signal })
  if (!response.ok) {
    throw new Error(`Failed to load the fleet (HTTP ${response.status})`)
  }
  return response.json() as Promise<ScooterLive[]>
}

type StreamHandlers = {
  onUpdate: (scooter: ScooterLive) => void
  onOpen: () => void
  onError: () => void
}

export function subscribeToScooters({ onUpdate, onOpen, onError }: StreamHandlers): () => void {
  const source = new EventSource('/api/scooters/stream')
  source.onopen = onOpen
  source.onerror = onError
  source.onmessage = (event: MessageEvent<string>) => onUpdate(JSON.parse(event.data) as ScooterLive)
  return () => source.close()
}
