import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import Hubs from './Hubs'

afterEach(() => { cleanup(); vi.unstubAllGlobals() })
const hub = { id: 7, name: 'North Gate', department: 'Security', capacity: 3, lat: 40.6, lon: -8.7 }
const response = (body: unknown, ok = true) => ({ ok, json: async () => body })

it('PT-33 adds a hub and immediately shows it to users', async () => {
  const fetchMock = vi.fn().mockResolvedValueOnce(response([])).mockResolvedValueOnce(response(hub))
  vi.stubGlobal('fetch', fetchMock)
  render(<Hubs />)
  await screen.findByText('No hubs yet.')
  await userEvent.type(screen.getByLabelText('Name'), 'North Gate')
  await userEvent.type(screen.getByLabelText('Department'), 'Security')
  await userEvent.clear(screen.getByLabelText('Capacity'))
  await userEvent.type(screen.getByLabelText('Capacity'), '3')
  await userEvent.type(screen.getByLabelText('Latitude'), '40.6')
  await userEvent.type(screen.getByLabelText('Longitude'), '-8.7')
  await userEvent.click(screen.getByRole('button', { name: 'Save hub' }))
  expect(await screen.findByText('North Gate')).toBeInTheDocument()
  expect(fetchMock).toHaveBeenLastCalledWith('/api/hubs', expect.objectContaining({ method: 'POST', body: JSON.stringify({ name: 'North Gate', department: 'Security', capacity: 3, lat: 40.6, lon: -8.7 }) }))
})

it('PT-33 edits a hub and preserves the form when the server rejects capacity', async () => {
  const fetchMock = vi.fn().mockResolvedValueOnce(response([hub]))
    .mockResolvedValueOnce(response({ detail: 'Capacity below occupancy' }, false))
    .mockResolvedValueOnce(response({ ...hub, capacity: 2 }))
  vi.stubGlobal('fetch', fetchMock)
  render(<Hubs />)
  await userEvent.click(await screen.findByRole('button', { name: 'Edit North Gate' }))
  await userEvent.clear(screen.getByLabelText('Capacity'))
  await userEvent.type(screen.getByLabelText('Capacity'), '1')
  await userEvent.click(screen.getByRole('button', { name: 'Save hub' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Capacity below occupancy')
  expect(screen.getByLabelText('Capacity')).toHaveValue(1)
  await userEvent.clear(screen.getByLabelText('Capacity'))
  await userEvent.type(screen.getByLabelText('Capacity'), '2')
  await userEvent.click(screen.getByRole('button', { name: 'Save hub' }))
  expect(await screen.findByText('Hub saved.')).toBeInTheDocument()
  expect(screen.getByText('Security · Capacity: 2')).toBeInTheDocument()
  expect(fetchMock).toHaveBeenLastCalledWith('/api/hubs/7', expect.objectContaining({ method: 'PUT' }))
})

it('PT-33 reports a list failure and can refresh', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValueOnce(new Error('Network unavailable')).mockResolvedValueOnce(response([hub])))
  render(<Hubs />)
  expect(await screen.findByRole('alert')).toHaveTextContent('Network unavailable')
  await userEvent.click(screen.getByRole('button', { name: 'Refresh hubs' }))
  expect(await screen.findByText('North Gate')).toBeInTheDocument()
})
