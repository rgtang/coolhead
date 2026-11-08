import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, money, type AuditLog, type MonthlyStats, type OrderView } from '../api/client'
import StatCard from '../components/StatCard'

const fmt = (iso: string) =>
  new Date(iso).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })

/** Demo highlight page: "this month Coolhead stopped $X", full audit trail, trust scores. */
export default function Dashboard() {
  const [stats, setStats] = useState<MonthlyStats | null>(null)
  const [logs, setLogs] = useState<AuditLog[]>([])
  const [trust, setTrust] = useState<Record<string, number>>({})
  const [orders, setOrders] = useState<OrderView[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const load = () =>
      Promise.all([api.audit.stats(), api.audit.logs(), api.audit.trust(), api.audit.orders()])
        .then(([s, l, t, o]) => {
          setStats(s)
          setLogs(l)
          setTrust(t)
          setOrders(o)
        })
        .catch((e) => setError(e.message))
    void load()
    const id = setInterval(load, 15_000)
    return () => clearInterval(id)
  }, [])

  return (
    <>
      <h1 className="page-title">Your cool head, in numbers.</h1>
      <p className="page-sub">Every transition below was triggered by you or by the clock — never by the model.</p>
      {error && <p className="error">{error}</p>}

      {stats && (
        <div className="stats">
          <StatCard hero label="Stopped this month" value={money(stats.blockedAmount)} />
          <StatCard label="Paused" value={stats.coolingCount} />
          <StatCard label="Overrides" value={stats.overrideCount} />
          <StatCard label="Let through" value={money(stats.capturedAmount)} />
          <StatCard label="Forgotten & auto-voided" value={stats.timerVoidCount} />
        </div>
      )}

      <div className="two">
        <section className="panel">
          <h2>Audit trail</h2>
          {logs.length ? (
            <table>
              <thead>
                <tr>
                  <th>When</th>
                  <th>Trigger</th>
                  <th>Action</th>
                  <th>Detail</th>
                  <th>Amount</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((l) => (
                  <tr key={l.id}>
                    <td className="mono">{fmt(l.createdAt)}</td>
                    <td>
                      <span className={`trigger ${l.trigger}`}>{l.trigger}</span>
                    </td>
                    <td className="mono">
                      <Link to={`/agent/${l.orderId}`}>{l.action}</Link>
                    </td>
                    <td style={{ maxWidth: 360, color: 'var(--muted)' }}>{l.detail}</td>
                    <td className="mono">{money(l.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="empty">Nothing yet. Go buy something you don't need.</p>
          )}
        </section>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          <section className="panel">
            <h2>Trust by category</h2>
            {Object.keys(trust).length ? (
              Object.entries(trust).map(([cat, score]) => (
                <div className="trust-row" key={cat}>
                  <span className="name">{cat}</span>
                  <div className="bar">
                    <span style={{ width: `${score}%` }} />
                  </div>
                  <span className="num">{score}</span>
                </div>
              ))
            ) : (
              <p className="hint">All categories start at 50. Waiting, cancelling and accepting verdicts raise it; overrides lower it. At 85+ a category skips the interrogation.</p>
            )}
          </section>

          <section className="panel">
            <h2>Recent orders</h2>
            {orders.length ? (
              <table>
                <tbody>
                  {orders.slice(0, 8).map((o) => (
                    <tr key={o.id}>
                      <td>
                        <Link to={`/agent/${o.id}`}>{o.productName}</Link>
                      </td>
                      <td>
                        <span className={`state-pill ${o.state}`}>{o.state}</span>
                      </td>
                      <td className="mono">{money(o.amount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p className="empty">No orders yet.</p>
            )}
          </section>
        </div>
      </div>
    </>
  )
}
