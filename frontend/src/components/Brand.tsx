import { useLayoutEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Logo from './Logo'

export type Phase = 'idle' | 'hop' | 'roll' | 'tagline' | 'back'

export const PHASES: { phase: Phase; ms: number }[] = [
  { phase: 'idle', ms: 5000 },
  { phase: 'hop', ms: 1200 },
  { phase: 'roll', ms: 900 },
  { phase: 'tagline', ms: 4500 },
  { phase: 'back', ms: 700 },
]

function prefers(query: string): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia(query).matches
}

function Brand() {
  const [step, setStep] = useState(0)
  const [cycle, setCycle] = useState(0)
  const [widths, setWidths] = useState({ word: 0, tagline: 0 })
  const word = useRef<HTMLSpanElement>(null)
  const tagline = useRef<HTMLSpanElement>(null)
  const phase = PHASES[step].phase

  useLayoutEffect(() => {
    const measure = () =>
      setWidths({ word: word.current?.scrollWidth ?? 0, tagline: tagline.current?.scrollWidth ?? 0 })
    measure()
    void document.fonts?.ready.then(measure)
  }, [])

  useLayoutEffect(() => {
    if (prefers('(prefers-reduced-motion: reduce)')) {
      return
    }
    const timer = setTimeout(() => {
      let next = (step + 1) % PHASES.length
      if (PHASES[next].phase === 'roll' && prefers('(max-width: 640px)')) {
        next = 0
      }
      if (next === 0) {
        setCycle((current) => current + 1)
      }
      setStep(next)
    }, PHASES[step].ms)
    return () => clearTimeout(timer)
  }, [step])

  const showTagline = phase === 'tagline'
  const width = showTagline ? widths.tagline : widths.word

  return (
    <Link className={`brand brand--${phase}`} to="/" aria-label="RayVolt home">
      <Logo />
      <span className="brand-text" style={width ? { width } : undefined} aria-hidden="true">
        <span ref={word} className="brand-word">RayVolt</span>
        <span ref={tagline} key={showTagline ? `on-${cycle}` : 'off'} className="brand-tagline">
          <span className="hero-first">Your site,</span>{' '}
          <span className="hero-motion">in motion.</span>
        </span>
      </span>
    </Link>
  )
}

export default Brand
