import { Navigate, Route, Routes } from 'react-router-dom'
import ScooterAvailabilityPage from './components/availability/ScooterAvailabilityPage'
import FleetPage from './components/fleet/FleetPage'
import HomePage from './components/home/HomePage'
import Navbar from './components/layout/Navbar'
import './App.css'

function App() {
  return (
    <div className="app">
      <Navbar />
      <main className="content">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/availability" element={<ScooterAvailabilityPage />} />
          <Route path="/fleet" element={<FleetPage />} />
          <Route path="*" element={<Navigate replace to="/" />} />
        </Routes>
      </main>
      <footer className="footer">
        <span>RayVolt</span>
        <span>Reliable rides. Ready when you are.</span>
      </footer>
    </div>
  )
}

export default App
