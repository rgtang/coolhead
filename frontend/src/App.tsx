import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import Store from './pages/Store'
import Checkout from './pages/Checkout'
import Interrogation from './pages/Interrogation'
import Dashboard from './pages/Dashboard'

export default function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Store />} />
        <Route path="/checkout/:productId" element={<Checkout />} />
        <Route path="/agent/:orderId" element={<Interrogation />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  )
}
