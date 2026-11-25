import { Link } from 'react-router-dom'

function HomePage() {
  return (
    <section className="welcome" aria-labelledby="welcome-title">
      <p className="eyebrow">Move your workplace forward</p>
      <h1 id="welcome-title" className="hero-title">
        <span className="hero-first">Your site,</span>{' '}
        <span className="hero-motion">in motion.</span>
      </h1>
      <p className="intro">
        Shared e-scooters for the people who keep Valdren Works moving.
        Find a ride, get where you need to be, and leave the fleet to us.
      </p>
      <Link className="button" to="/fleet">
        See the fleet
      </Link>
    </section>
  )
}

export default HomePage
