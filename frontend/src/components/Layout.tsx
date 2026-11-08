import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'

const PAYPAL_CLIENT_ID = import.meta.env.VITE_PAYPAL_CLIENT_ID as string | undefined

export default function Layout({ children }: { children: ReactNode }) {
  return (
    <div className="shell">
      <header className="topbar">
        <NavLink to="/" className="brand">
          cool<em>head</em>
        </NavLink>
        <nav className="nav">
          <NavLink to="/" end>
            Store
          </NavLink>
          <NavLink to="/dashboard">Dashboard</NavLink>
          {!PAYPAL_CLIENT_ID && <span className="badge-mock">PayPal mock mode</span>}
        </nav>
      </header>
      <main className="page">{children}</main>
    </div>
  )
}
