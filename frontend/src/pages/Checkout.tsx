import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { PayPalButtons, PayPalScriptProvider } from '@paypal/react-paypal-js'
import { api, money, type Product } from '../api/client'

const PAYPAL_CLIENT_ID = import.meta.env.VITE_PAYPAL_CLIENT_ID as string | undefined

/**
 * PayPal checkout with intent=AUTHORIZE. onApprove hands the approved order to the backend, which authorizes
 * (holds) the funds and runs the guardrails. The user is then routed into the agent flow.
 */
export default function Checkout() {
  const { productId = '' } = useParams()
  const navigate = useNavigate()
  const [product, setProduct] = useState<Product | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.store.product(productId).then(setProduct).catch((e) => setError(e.message))
  }, [productId])

  async function afterApproval(paypalOrderId: string) {
    setBusy(true)
    setError(null)
    try {
      const res = await api.checkout.authorize(paypalOrderId, productId)
      navigate(`/agent/${res.order.id}`, { replace: true })
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  async function simulate() {
    setBusy(true)
    setError(null)
    try {
      const created = await api.checkout.create(productId)
      if (!created.mock) {
        throw new Error(
          'Backend is using real PayPal, so the buyer must approve in the PayPal popup. ' +
            'Set VITE_PAYPAL_CLIENT_ID in frontend/.env.local and restart npm run dev.',
        )
      }
      await afterApproval(created.paypalOrderId)
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  if (!product) return <p className="empty">{error ?? 'Loading…'}</p>

  return (
    <div className="checkout">
      <section className="panel">
        <h2>{product.name}</h2>
        <img src={product.image} alt="" style={{ width: '100%', borderRadius: 10, aspectRatio: '16/9', objectFit: 'cover' }} />
        <div style={{ marginTop: 16 }}>
          <div className="kv">
            <span>Category</span>
            <span style={{ textTransform: 'capitalize' }}>{product.category}</span>
          </div>
          <div className="kv">
            <span>Total</span>
            <span className="price">{money(product.price)}</span>
          </div>
        </div>
        <div className="hold-note">
          PayPal will <b>authorize</b> this amount, not charge it. Coolhead decides with you whether and when it is captured.
          If nothing happens, the hold simply expires and you keep your money.
        </div>
      </section>

      <section className="panel">
        <h2>Pay with PayPal</h2>
        {error && <p className="error">{error}</p>}
        {PAYPAL_CLIENT_ID ? (
          <PayPalScriptProvider options={{ clientId: PAYPAL_CLIENT_ID, intent: 'authorize', currency: 'USD' }}>
            <PayPalButtons
              style={{ layout: 'vertical', shape: 'rect', label: 'pay' }}
              disabled={busy}
              createOrder={async () => (await api.checkout.create(productId)).paypalOrderId}
              onApprove={async (data) => afterApproval(data.orderID)}
              onError={(e) => setError(String(e))}
            />
          </PayPalScriptProvider>
        ) : (
          <>
            <p className="hint">
              No <code>VITE_PAYPAL_CLIENT_ID</code> configured — the backend is in mock mode, so you can simulate the buyer
              approving the PayPal popup.
            </p>
            <button className="btn accent" disabled={busy} onClick={simulate} style={{ width: '100%', marginTop: 12 }}>
              {busy ? 'Authorizing…' : `Simulate PayPal approval · ${money(product.price)}`}
            </button>
          </>
        )}
      </section>
    </div>
  )
}
