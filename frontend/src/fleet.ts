export type ScooterStatus = 'AVAILABLE' | 'RESERVED' | 'IN_USE' | 'MAINTENANCE'

export type PositionStatus = 'AT_HUB' | 'MOVING' | 'OUTSIDE_HUB'

export type ScooterLive = {
  scooterId: number
  status: ScooterStatus
  battery: number | null
  lat: number | null
  lon: number | null
  positionStatus: PositionStatus | null
  hubName: string | null
  fault: boolean
  lastSeen: string | null
}

export const STATUS_LABELS: Record<ScooterStatus, string> = {
  AVAILABLE: 'Available',
  RESERVED: 'Reserved',
  IN_USE: 'In use',
  MAINTENANCE: 'Maintenance',
}

export const POSITION_LABELS: Record<PositionStatus, string> = {
  AT_HUB: 'At hub',
  MOVING: 'Moving',
  OUTSIDE_HUB: 'Outside hub',
}

export function where(scooter: ScooterLive): string {
  if (scooter.positionStatus === null) {
    return '—'
  }
  if (scooter.positionStatus === 'AT_HUB' && scooter.hubName !== null) {
    return `At ${scooter.hubName}`
  }
  return POSITION_LABELS[scooter.positionStatus]
}

export function timeAgo(lastSeen: string | null, now: number): string {
  if (lastSeen === null) {
    return '—'
  }
  const seconds = Math.max(0, Math.floor((now - Date.parse(lastSeen)) / 1000))
  if (seconds < 60) {
    return `${seconds} s ago`
  }
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) {
    return `${minutes} min ago`
  }
  return `${Math.floor(minutes / 60)} h ago`
}

export function upsert(scooters: ScooterLive[], update: ScooterLive): ScooterLive[] {
  const others = scooters.filter((scooter) => scooter.scooterId !== update.scooterId)
  return [...others, update].sort((a, b) => a.scooterId - b.scooterId)
}

export type FleetSummary = {
  total: number
  available: number
  inUse: number
  reserved: number
  maintenance: number
  faults: number
}

export function summarize(scooters: ScooterLive[]): FleetSummary {
  return {
    total: scooters.length,
    available: scooters.filter((s) => s.status === 'AVAILABLE').length,
    inUse: scooters.filter((s) => s.status === 'IN_USE').length,
    reserved: scooters.filter((s) => s.status === 'RESERVED').length,
    maintenance: scooters.filter((s) => s.status === 'MAINTENANCE').length,
    faults: scooters.filter((s) => s.fault).length,
  }
}

export function batteryLevel(battery: number): 'low' | 'medium' | 'high' {
  if (battery < 20) {
    return 'low'
  }
  return battery < 50 ? 'medium' : 'high'
}
