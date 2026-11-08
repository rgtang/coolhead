import { useEffect, useState } from 'react'
import { api, type Product } from '../api/client'
import ProductCard from '../components/ProductCard'

export default function Store() {
  const [products, setProducts] = useState<Product[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.store.products().then(setProducts).catch((e) => setError(e.message))
  }, [])

  return (
    <>
      <h1 className="page-title">A store designed to tempt you.</h1>
      <p className="page-sub">
        Every purchase is authorized through PayPal but not charged until Coolhead has asked a few questions. Essentials and
        small amounts go straight through; everything else gets a conversation.
      </p>
      {error && <p className="error">{error}</p>}
      <div className="grid">
        {products.map((p) => (
          <ProductCard key={p.id} product={p} />
        ))}
      </div>
      {!products.length && !error && <p className="empty">Loading catalog…</p>}
    </>
  )
}
