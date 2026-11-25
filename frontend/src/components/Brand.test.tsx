import { act, cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Brand, { PHASES } from './Brand'

function renderBrand() {
  render(
    <MemoryRouter>
      <Brand />
    </MemoryRouter>,
  )
  return screen.getByRole('link', { name: 'RayVolt home' })
}

function advance(phase: string) {
  const ms = PHASES.find((p) => p.phase === phase)?.ms ?? 0
  act(() => {
    vi.advanceTimersByTime(ms)
  })
}

function mediaMatches(query: string) {
  vi.stubGlobal('matchMedia', (q: string) => ({ matches: q === query }))
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('Brand', () => {
  it('hops, rolls, shows the tagline and comes back to RayVolt', () => {
    const link = renderBrand()
    expect(link).toHaveClass('brand--idle')
    advance('idle')
    expect(link).toHaveClass('brand--hop')
    advance('hop')
    expect(link).toHaveClass('brand--roll')
    advance('roll')
    expect(link).toHaveClass('brand--tagline')
    expect(link).toHaveTextContent('Your site, in motion.')
    advance('tagline')
    expect(link).toHaveClass('brand--back')
    advance('back')
    expect(link).toHaveClass('brand--idle')
  })

  it('only hops on a small screen', () => {
    mediaMatches('(max-width: 640px)')
    const link = renderBrand()
    advance('idle')
    advance('hop')
    expect(link).toHaveClass('brand--idle')
  })

  it('stays still when the user asks for reduced motion', () => {
    mediaMatches('(prefers-reduced-motion: reduce)')
    const link = renderBrand()
    advance('idle')
    advance('hop')
    expect(link).toHaveClass('brand--idle')
  })
})
