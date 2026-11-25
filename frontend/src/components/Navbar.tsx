import { NavLink } from 'react-router-dom'
import Brand from './Brand'

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
    </header>
  )
}

export default Navbar
