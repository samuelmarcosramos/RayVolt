import { Link, NavLink } from 'react-router-dom'

function Navbar() {
  return (
    <header className="navbar">
      <Link className="brand" to="/" aria-label="RayVolt home">
        RayVolt
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
    </header>
  )
}

export default Navbar
