import { NavLink } from 'react-router-dom'
import Brand from './Brand'
import ThemeToggle from './ThemeToggle'

function Navbar() {
  return (
    <header className="navbar">
      <Brand />
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
      <ThemeToggle />
    </header>
  )
}

export default Navbar
