import { describe, expect, it } from 'vitest'
import { batteryLevel, type ScooterLive, summarize, timeAgo, upsert, where } from './fleet'

const NOW = Date.parse('2026-11-25T12:00:00Z')

describe('timeAgo', () => {
  it('shows seconds, minutes and hours', () => {
    expect(timeAgo('2026-11-25T11:59:57Z', NOW)).toBe('3 s ago')
    expect(timeAgo('2026-11-25T11:58:00Z', NOW)).toBe('2 min ago')
    expect(timeAgo('2026-11-25T09:00:00Z', NOW)).toBe('3 h ago')
  })

  it('shows a dash when the scooter never sent telemetry', () => {
    expect(timeAgo(null, NOW)).toBe('—')
  })

  it('never shows a negative time', () => {
    expect(timeAgo('2026-11-25T12:00:02Z', NOW)).toBe('0 s ago')
  })
})

describe('upsert', () => {
  const scooter = (scooterId: number, battery: number): ScooterLive => ({
    scooterId, status: 'AVAILABLE', battery, lat: 40.61, lon: -8.71, positionStatus: 'AT_HUB', hubName: 'Main Gate', fault: false,
    lastSeen: '2026-11-25T12:00:00Z',
  })

  it('replaces the scooter with the same id and keeps the order by id', () => {
    const result = upsert([scooter(1, 90), scooter(3, 80)], scooter(1, 70))
    expect(result.map((s) => [s.scooterId, s.battery])).toEqual([[1, 70], [3, 80]])
  })

  it('adds a new scooter in id order', () => {
    const result = upsert([scooter(1, 90), scooter(3, 80)], scooter(2, 60))
    expect(result.map((s) => s.scooterId)).toEqual([1, 2, 3])
  })
})

describe('summarize', () => {
  const base: ScooterLive = {
    scooterId: 1, status: 'AVAILABLE', battery: 80, lat: 40.61, lon: -8.71, positionStatus: 'AT_HUB', hubName: 'Main Gate', fault: false,
    lastSeen: '2026-11-25T12:00:00Z',
  }

  it('counts each status and the faults', () => {
    const summary = summarize([
      base,
      { ...base, scooterId: 2, status: 'IN_USE', battery: 41 },
      { ...base, scooterId: 3, status: 'MAINTENANCE', battery: 100, fault: true },
      { ...base, scooterId: 4, status: 'RESERVED', battery: null },
    ])
    expect(summary).toEqual({
      total: 4, available: 1, inUse: 1, reserved: 1, maintenance: 1, faults: 1,
    })
  })
})

describe('batteryLevel', () => {
  it('is low under 20, medium under 50 and high from 50', () => {
    expect([19, 20, 49, 50].map(batteryLevel)).toEqual(['low', 'medium', 'medium', 'high'])
  })
})

describe('where', () => {
  const at = (positionStatus: ScooterLive['positionStatus'], hubName: string | null) => where({
    scooterId: 1, status: 'AVAILABLE', battery: 80, lat: 40.61, lon: -8.71, positionStatus, hubName, fault: false,
    lastSeen: null,
  })

  it('names the hub when the scooter is at one', () => {
    expect(at('AT_HUB', 'Main Gate')).toBe('At Main Gate')
  })

  it('says moving or outside hub otherwise', () => {
    expect(at('MOVING', null)).toBe('Moving')
    expect(at('OUTSIDE_HUB', null)).toBe('Outside hub')
  })

  it('falls back to at hub without a name and to a dash without telemetry', () => {
    expect(at('AT_HUB', null)).toBe('At hub')
    expect(at(null, null)).toBe('—')
  })
})
