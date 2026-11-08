import { useEffect, useState } from 'react'
import { money, type OrderView } from '../api/client'

const HEADLINES: Record<string, string> = {
  RELEASE: 'Go ahead.',
  COOL: 'Sleep on it.',
  BLOCK: 'Let this one go.',
}

function useCountdown(until: string | null) {
  const [left, setLeft] = useState('')
  useEffect(() => {
    if (!until) return
    const tick = () => {
      const ms = new Date(until).getTime() - Date.now()
      if (ms <= 0) return setLeft('00:00:00')
      const h = Math.floor(ms / 3_600_000)
      const m = Math.floor((ms % 3_600_000) / 60_000)
      const s = Math.floor((ms % 60_000) / 1000)
      setLeft(`${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`)
    }
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [until])
  return left
}

export interface VerdictCardProps {
  order: OrderView
  busy: boolean
  error?: string | null
  onAccept: () => void
  onOverride: (reason: string) => void
  onConfirm: () => void
  onCancel: () => void
}

/**
 * The verdict card: balanced reasons, then the human decides.
 * Buttons map 1:1 to the USER_CLICK endpoints; nothing here happens automatically.
 */
export default function VerdictCard({ order, busy, error, onAccept, onOverride, onConfirm, onCancel }: VerdictCardProps) {
  const [showOverride, setShowOverride] = useState(false)
  const [reason, setReason] = useState('')
  const countdown = useCountdown(order.state === 'COOLING' ? order.coolingUntil : null)
  const v = order.verdict

  if (!v) return null

  const terminal = order.state === 'CAPTURED' || order.state === 'VOIDED'

  return (
    <aside className={`verdict ${v.action}`}>
      <div>
        <span className={`state-pill ${order.state}`}>{order.state}</span>
        <div className="headline">{HEADLINES[v.action]}</div>
        <div className="source">
          {v.source === 'GUARDRAIL' ? 'hard rule — no AI involved' : v.source === 'LLM' ? 'ai review' : 'fail-safe default'}
          {v.action === 'COOL' && v.coolingHours ? ` · ${v.coolingHours}h hold` : ''}
        </div>
      </div>

      <div className="reason buy">
        <b>Why you might buy</b>
        {v.reasonToBuy}
      </div>
      <div className="reason wait">
        <b>Why you might wait</b>
        {v.reasonToWait}
      </div>

      {order.state === 'COOLING' && (
        <div>
          <div className="hint">{order.coolingElapsed ? 'Cooling period is over. Still want it?' : 'Payment is on hold. Decide after:'}</div>
          <div className="countdown">{countdown}</div>
        </div>
      )}

      {order.state === 'CAPTURED' && (
        <div className="hint">
          {money(order.amount, order.currency)} captured
          {order.overrideReason ? ` — override: “${order.overrideReason}”` : ''}.
        </div>
      )}
      {order.state === 'VOIDED' && <div className="hint">Hold released. {money(order.amount, order.currency)} stays in your account.</div>}

      {error && <div className="error">{error}</div>}

      {!terminal && (
        <div className="actions">
          {order.state === 'AUTHORIZED' && (
            <button className={`btn ${v.action === 'RELEASE' ? 'accent' : v.action === 'COOL' ? 'cool' : 'danger'}`} disabled={busy} onClick={onAccept}>
              {v.action === 'RELEASE' ? `Pay ${money(order.amount)}` : v.action === 'COOL' ? `Hold for ${v.coolingHours}h` : 'Release the hold'}
            </button>
          )}
          {order.state === 'COOLING' && (
            <button className="btn accent" disabled={busy || !order.coolingElapsed} onClick={onConfirm}>
              {order.coolingElapsed ? `Yes, pay ${money(order.amount)}` : 'Locked until cooling ends'}
            </button>
          )}
          {v.action !== 'RELEASE' && (
            <button className="btn warn" disabled={busy} onClick={() => setShowOverride((s) => !s)}>
              Override
            </button>
          )}
          {v.action !== 'BLOCK' && (
            <button className="btn ghost" disabled={busy} onClick={onCancel}>
              Never mind
            </button>
          )}
        </div>
      )}

      {showOverride && !terminal && (
        <div className="override-box">
          <textarea
            placeholder="Tell future-you why this can't wait (min 10 characters). This is logged."
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
          <div className="actions" style={{ marginTop: 8 }}>
            <button className="btn warn" disabled={busy || reason.trim().length < 10} onClick={() => onOverride(reason.trim())}>
              Pay anyway
            </button>
            <span className="hint">Overrides lower your trust score for “{order.category}”.</span>
          </div>
        </div>
      )}
    </aside>
  )
}
