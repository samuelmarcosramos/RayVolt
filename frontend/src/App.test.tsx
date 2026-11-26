import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

const hubs = [
  { id: 1, name: 'North Gate' },
  { id: 2, name: 'South Gate' },
]

const availableScooters = [
  { id: 14, type: 'Standard', hubId: 1, status: 'AVAILABLE', batteryPercent: 75 },
]

function jsonResponse(body: unknown) {
  return {
    ok: true,
    json: async () => body,
  }
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('scooter availability search', () => {
  it('loads hubs and finds available scooters with the default battery threshold', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(hubs))
      .mockResolvedValueOnce(jsonResponse(availableScooters))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    render(<App />)
    await user.selectOptions(await screen.findByRole('combobox', { name: 'Hub' }), '1')

    expect(await screen.findByText('Scooter #14')).toBeInTheDocument()
    expect(screen.getByText('Standard', { selector: '.scooter-type' })).toBeInTheDocument()
    expect(screen.getByText(/75\s*%/)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/scooters/available?hubId=1&minBatteryPercent=10',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
  })

  it('applies the optional scooter type and displays the no-results message', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(hubs))
      .mockResolvedValueOnce(jsonResponse(availableScooters))
      .mockResolvedValueOnce(jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    render(<App />)
    await user.selectOptions(await screen.findByRole('combobox', { name: 'Hub' }), '1')
    expect(await screen.findByText('Scooter #14')).toBeInTheDocument()

    await user.selectOptions(screen.getByRole('combobox', { name: 'Scooter type' }), 'Cargo')

    expect(await screen.findByText('No scooters available at this hub.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      '/api/scooters/available?hubId=1&minBatteryPercent=10&type=Cargo',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
  })

  it('reports an API error and lets the user retry the search', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(hubs))
      .mockRejectedValueOnce(new Error('Network unavailable'))
      .mockResolvedValueOnce(jsonResponse(availableScooters))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    render(<App />)
    await user.selectOptions(await screen.findByRole('combobox', { name: 'Hub' }), '1')
    expect(await screen.findByRole('alert')).toHaveTextContent('Network unavailable')

    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Scooter #14')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('reports a hub-loading failure instead of hiding it', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error('Backend unavailable'))
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Backend unavailable')
    expect(screen.getByRole('combobox', { name: 'Hub' })).toBeDisabled()
  })
})
