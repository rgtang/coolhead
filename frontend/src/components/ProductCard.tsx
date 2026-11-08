import { Link } from 'react-router-dom'
import { money, type Product } from '../api/client'

export default function ProductCard({ product }: { product: Product }) {
  return (
    <article className="card">
      <img src={product.image} alt="" loading="lazy" />
      <div className="card-body">
        <span className="cat">{product.category}</span>
        <h3>{product.name}</h3>
        <p className="blurb">{product.blurb}</p>
        <div className="row">
          <span className="price">{money(product.price)}</span>
          <Link className="btn sm" to={`/checkout/${product.id}`}>
            Buy
          </Link>
        </div>
      </div>
    </article>
  )
}
