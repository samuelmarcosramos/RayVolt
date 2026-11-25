import { Route, Routes } from 'react-router-dom'
import Navbar from './components/Navbar'
import FleetPage from './pages/FleetPage'
import HomePage from './pages/HomePage'
import './App.css'

function App() {
  return (
    <div className="app">
      <Navbar />
      <main className="content">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/fleet" element={<FleetPage />} />
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
