import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import App from './App'

afterEach(() => {
  cleanup()
})

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

describe('RayVolt layout', () => {
  it('shows the home page with the navbar', () => {
    renderAt('/')
    expect(screen.getByRole('heading', { level: 1, name: 'Your site, in motion.' })).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: 'Main' })
    expect(nav).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Home' })).toHaveAttribute('aria-current', 'page')
  })

  it('opens the fleet page from the navbar', async () => {
    renderAt('/')
    await userEvent.click(screen.getByRole('link', { name: 'Fleet' }))
    expect(screen.getByRole('heading', { level: 1, name: 'Fleet' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Fleet' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current')
  })

  it('opens the fleet page from the home button', async () => {
    renderAt('/')
    await userEvent.click(screen.getByRole('link', { name: 'See the fleet' }))
    expect(screen.getByRole('heading', { level: 1, name: 'Fleet' })).toBeInTheDocument()
  })

  it('takes the user home from the logo', async () => {
    renderAt('/fleet')
    await userEvent.click(screen.getByRole('link', { name: 'RayVolt home' }))
    expect(screen.getByRole('heading', { level: 1, name: 'Your site, in motion.' })).toBeInTheDocument()
  })
})
