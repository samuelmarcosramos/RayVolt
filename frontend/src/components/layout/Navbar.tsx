import { useLayoutEffect, useRef, useState } from 'react'
import { Link, NavLink } from 'react-router-dom'
import Logo from '../reusable/Logo'
import './Navbar.css'

type Phase = 'idle' | 'hop' | 'roll' | 'tagline' | 'back'

type Theme = 'dark' | 'light'

const PHASES: { phase: Phase; ms: number }[] = [
  { phase: 'idle', ms: 5000 },
  { phase: 'hop', ms: 1200 },
  { phase: 'roll', ms: 900 },
  { phase: 'tagline', ms: 4500 },
  { phase: 'back', ms: 700 },
]

function prefers(query: string): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia(query).matches
}

function currentTheme(): Theme {
  return document.documentElement.dataset.theme === 'light' ? 'light' : 'dark'
}

function remember(theme: Theme): boolean {
  try {
    localStorage.setItem('theme', theme)
    return true
  } catch {
    return false
  }
}

function Navbar() {
  const [step, setStep] = useState(0)
  const [cycle, setCycle] = useState(0)
  const [widths, setWidths] = useState({ word: 0, tagline: 0 })
  const [theme, setTheme] = useState<Theme>(currentTheme)
  const word = useRef<HTMLSpanElement>(null)
  const tagline = useRef<HTMLSpanElement>(null)
  const phase = PHASES[step].phase
  const nextTheme: Theme = theme === 'dark' ? 'light' : 'dark'

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

  const toggleTheme = () => {
    document.documentElement.dataset.theme = nextTheme
    remember(nextTheme)
    setTheme(nextTheme)
  }

  const showTagline = phase === 'tagline'
  const width = showTagline ? widths.tagline : widths.word

  return (
    <header className="navbar">
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
      <nav aria-label="Main">
        <ul className="nav-links">
          <li>
            <NavLink to="/" end>
              Home
            </NavLink>
          </li>
          <li>
            <NavLink to="/fleet">Fleet</NavLink>
          </li>
        </ul>
      </nav>
      <button type="button" className="theme-toggle" aria-label={`Switch to ${nextTheme} theme`} onClick={toggleTheme}>
        {theme === 'dark' ? (
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="4.5" />
            <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4" />
          </svg>
        ) : (
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5Z" />
          </svg>
        )}
      </button>
    </header>
  )
}

export default Navbar
