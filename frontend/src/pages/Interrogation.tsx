import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, money, type OrderView } from '../api/client'
import ChatBubble, { TypingBubble } from '../components/ChatBubble'
import VerdictCard from '../components/VerdictCard'

/**
 * Layer 2 chat + verdict card + override. Polls the order while COOLING so the countdown and the
 * TIMER-driven reminder/void show up without a refresh.
 */
export default function Interrogation() {
  const { orderId = '' } = useParams()
  const [order, setOrder] = useState<OrderView | null>(null)
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)

  const refresh = useCallback(async () => {
    const o = await api.agent.get(orderId)
    setOrder(o)
    return o
  }, [orderId])

  useEffect(() => {
    refresh().catch((e) => setError(e.message))
  }, [refresh])

  // poll while cooling so the timer-driven state shows up
  useEffect(() => {
    if (order?.state !== 'COOLING') return
    const id = setInterval(() => refresh().catch(() => undefined), 10_000)
    return () => clearInterval(id)
  }, [order?.state, refresh])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [order?.transcript.length, busy])

  const run = async (fn: () => Promise<OrderView>) => {
    setBusy(true)
    setError(null)
    try {
      setOrder(await fn())
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function send() {
    const text = input.trim()
    if (!text || !order) return
    setInput('')
    setBusy(true)
    setError(null)
    try {
      const res = await api.agent.message(order.id, text)
      setOrder(res.order)
      if (res.done) {
        setDone(true)
        setOrder(await api.agent.verdict(order.id))
      }
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  if (!order) return <p className="empty">{error ?? 'Loading…'}</p>

  const chatting = order.state === 'AUTHORIZED' && !order.verdict && !done

  return (
    <>
      <h1 className="page-title">
        {order.productName} <span className="price" style={{ fontSize: 22, color: 'var(--muted)' }}>{money(order.amount)}</span>
      </h1>
      <p className="page-sub">
        {chatting
          ? 'Payment is authorized but not captured. A few quick questions first.'
          : 'Here is the balanced view. The decision — and the click — is yours.'}
      </p>

      <div className="convo">
        <section>
          <div className="chat">
            {order.transcript.map((t, i) => (
              <ChatBubble key={i} turn={t} />
            ))}
            {busy && chatting && <TypingBubble />}
            {!order.transcript.length && !chatting && (
              <div className="bubble assistant">
                {order.verdict?.source === 'GUARDRAIL'
                  ? 'No conversation needed — a hard rule decided this one.'
                  : 'The AI reviewer was unreachable, so the fail-safe default applies. No questions were asked.'}
              </div>
            )}
            <div ref={bottomRef} />
          </div>

          {chatting && (
            <form
              className="composer"
              onSubmit={(e) => {
                e.preventDefault()
                void send()
              }}
            >
              <input
                autoFocus
                placeholder="Answer honestly — this is just for you"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                disabled={busy}
              />
              <button className="btn" disabled={busy || !input.trim()}>
                Send
              </button>
              <button type="button" className="btn ghost" disabled={busy} onClick={() => run(() => api.agent.verdict(order.id))}>
                Skip to verdict
              </button>
            </form>
          )}
          {error && !order.verdict && <p className="error">{error}</p>}
          <p className="hint" style={{ marginTop: 18 }}>
            <Link to="/dashboard">See the audit trail →</Link>
          </p>
        </section>

        {order.verdict && (
          <VerdictCard
            order={order}
            busy={busy}
            error={error}
            onAccept={() => run(() => api.agent.accept(order.id))}
            onOverride={(reason) => run(() => api.agent.override(order.id, reason))}
            onConfirm={() => run(() => api.agent.confirm(order.id))}
            onCancel={() => run(() => api.agent.cancel(order.id))}
          />
        )}
      </div>
    </>
  )
}
