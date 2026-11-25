import { act, cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ScooterLive } from '../fleet'
import { FakeEventSource } from '../test/fakeEventSource'
import FleetPage from './FleetPage'

const available: ScooterLive = {
  scooterId: 1, status: 'AVAILABLE', battery: 96, lat: 40.61, lon: -8.7135, positionStatus: 'AT_HUB',
  hubName: 'Security Post', fault: false, lastSeen: new Date().toISOString(),
}

function serve(body: unknown, ok = true) {
  const fetchMock = vi.fn().mockResolvedValue({ ok, status: ok ? 200 : 500, json: async () => body })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function row(name: string) {
  return screen.getByRole('rowheader', { name }).closest('tr') as HTMLElement
}

beforeEach(() => {
  FakeEventSource.instances = []
  vi.stubGlobal('EventSource', FakeEventSource)
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('Fleet dashboard', () => {
  it('shows every scooter from the first load', async () => {
    serve([available, { ...available, scooterId: 2, battery: null, lat: null, lon: null, positionStatus: null, hubName: null, lastSeen: null }])
    render(<FleetPage />)
    const first = await screen.findByRole('rowheader', { name: '#1' })
    const cells = within(first.closest('tr') as HTMLElement)
    expect(cells.getByText('Available')).toBeInTheDocument()
    expect(cells.getByText('96%')).toBeInTheDocument()
    expect(cells.getByText('40.6100, -8.7135')).toBeInTheDocument()
    expect(cells.getByText('At Security Post')).toBeInTheDocument()
    expect(within(row('#2')).getAllByText('—')).toHaveLength(4)
  })

  it('updates a scooter from the stream without reloading', async () => {
    const fetchMock = serve([available])
    render(<FleetPage />)
    await screen.findByRole('rowheader', { name: '#1' })
    act(() => FakeEventSource.latest().send({ ...available, battery: 88, positionStatus: 'MOVING', hubName: null }))
    expect(within(row('#1')).getByText('88%')).toBeInTheDocument()
    expect(within(row('#1')).getByText('Moving')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(FakeEventSource.latest().url).toBe('/api/scooters/stream')
  })

  it('shows a fault and the maintenance status', async () => {
    serve([available])
    render(<FleetPage />)
    await screen.findByRole('rowheader', { name: '#1' })
    act(() => FakeEventSource.latest().send({ ...available, status: 'MAINTENANCE', fault: true }))
    expect(within(row('#1')).getByText('Maintenance')).toBeInTheDocument()
    expect(within(row('#1')).getByText('Fault')).toBeInTheDocument()
  })

  it('adds a scooter that appears on the stream', async () => {
    serve([available])
    render(<FleetPage />)
    await screen.findByRole('rowheader', { name: '#1' })
    act(() => FakeEventSource.latest().send({ ...available, scooterId: 5 }))
    expect(screen.getByRole('rowheader', { name: '#5' })).toBeInTheDocument()
  })

  it('shows when the stream is live and when it reconnects', async () => {
    serve([available])
    render(<FleetPage />)
    await screen.findByRole('rowheader', { name: '#1' })
    expect(screen.getByRole('status')).toHaveTextContent('Connecting…')
    act(() => FakeEventSource.latest().open())
    expect(screen.getByRole('status')).toHaveTextContent('Live')
    act(() => FakeEventSource.latest().fail())
    expect(screen.getByRole('status')).toHaveTextContent('Reconnecting…')
  })

  it('shows an empty fleet', async () => {
    serve([])
    render(<FleetPage />)
    expect(await screen.findByText('No scooters yet.')).toBeInTheDocument()
  })

  it('shows an error when the first load fails', async () => {
    serve(null, false)
    render(<FleetPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the fleet')
  })

  it('closes the stream when leaving the page', async () => {
    serve([available])
    const { unmount } = render(<FleetPage />)
    await screen.findByRole('rowheader', { name: '#1' })
    unmount()
    expect(FakeEventSource.latest().closed).toBe(true)
  })

  it('sums up the fleet in the cards and keeps them live', async () => {
    serve([available, { ...available, scooterId: 2, status: 'IN_USE', battery: 40 }])
    render(<FleetPage />)
    await screen.findByRole('rowheader', { name: '#1' })
    const card = (label: string) => screen.getByText(label, { selector: 'dt' }).nextElementSibling
    expect(card('Scooters')).toHaveTextContent('2')
    expect(card('In use')).toHaveTextContent('1')
    act(() => FakeEventSource.latest().send({ ...available, status: 'MAINTENANCE', fault: true }))
    expect(card('Maintenance')).toHaveTextContent('1')
    expect(card('Maintenance')).toHaveTextContent('1 fault')
  })

  it('filters the table by status', async () => {
    serve([available, { ...available, scooterId: 2, status: 'IN_USE' }])
    render(<FleetPage />)
    await screen.findByRole('rowheader', { name: '#1' })
    await userEvent.click(screen.getByRole('button', { name: /In use/ }))
    expect(screen.queryByRole('rowheader', { name: '#1' })).not.toBeInTheDocument()
    expect(screen.getByRole('rowheader', { name: '#2' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Reserved/ }))
    expect(screen.getByText('No scooters with this status.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /All/ }))
    expect(screen.getAllByRole('rowheader')).toHaveLength(2)
  })
})
