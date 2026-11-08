/**
 * Typed wrapper over the Coolhead backend.
 * In dev, Vite proxies /api -> http://localhost:8080; in docker, nginx does the same.
 * Set VITE_API_BASE to call a remote backend directly.
 */

const BASE = (import.meta.env.VITE_API_BASE as string | undefined)?.replace(/\/$/, '') ?? ''

export type OrderState = 'AUTHORIZED' | 'COOLING' | 'CAPTURED' | 'VOIDED'
export type VerdictAction = 'RELEASE' | 'COOL' | 'BLOCK'
export type Trigger = 'TIMER' | 'USER_CLICK'

export interface Product {
  id: string
  name: string
  category: string
  price: number
  image: string
  blurb: string
}

export interface Turn {
  role: 'system' | 'assistant' | 'user'
  text: string
}

export interface VerdictView {
  action: VerdictAction
  reasonToBuy: string
  reasonToWait: string
  source: 'GUARDRAIL' | 'LLM' | 'FALLBACK'
  coolingHours: number | null
}

export interface OrderView {
  id: string
  productId: string
  productName: string
  category: string
  amount: number
  currency: string
  state: OrderState
  coolingHours: number | null
  coolingUntil: string | null
  coolingElapsed: boolean
  remindedAt: string | null
  verdict: VerdictView | null
  overrideReason: string | null
  interrogationTurns: number
  transcript: Turn[]
  paypalOrderId: string
  paypalAuthorizationId: string
  paypalCaptureId: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateCheckoutResponse {
  paypalOrderId: string
  status: string
  mock: boolean
  approveUrl: string
}

export interface AuthorizeResponse {
  order: OrderView
  nextStep: 'INTERROGATE' | 'VERDICT'
  question: string | null
}

export interface MessageResponse {
  question: string | null
  done: boolean
  urgencySignal: string | null
  order: OrderView
}

export interface AuditLog {
  id: number
  orderId: string
  trigger: Trigger
  action: string
  detail: string | null
  amount: number | null
  category: string | null
  createdAt: string
}

export interface MonthlyStats {
  blockedAmount: number
  blockedCount: number
  capturedAmount: number
  capturedCount: number
  coolingCount: number
  overrideCount: number
  timerVoidCount: number
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    ...init,
  })
  if (!res.ok) {
    let msg = res.statusText
    try {
      const body = (await res.json()) as { error?: string }
      if (body.error) msg = body.error
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, msg)
  }
  return (await res.json()) as T
}

const post = <T,>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })

export const api = {
  store: {
    products: () => request<Product[]>('/api/store/products'),
    product: (id: string) => request<Product>(`/api/store/products/${id}`),
  },
  checkout: {
    create: (productId: string) => post<CreateCheckoutResponse>('/api/checkout', { productId }),
    authorize: (paypalOrderId: string, productId: string) =>
      post<AuthorizeResponse>(`/api/checkout/${paypalOrderId}/authorize`, { productId }),
  },
  agent: {
    get: (orderId: string) => request<OrderView>(`/api/agent/${orderId}`),
    message: (orderId: string, text: string) => post<MessageResponse>(`/api/agent/${orderId}/message`, { text }),
    verdict: (orderId: string) => post<OrderView>(`/api/agent/${orderId}/verdict`),
    accept: (orderId: string) => post<OrderView>(`/api/agent/${orderId}/accept`),
    override: (orderId: string, reason: string) => post<OrderView>(`/api/agent/${orderId}/override`, { reason }),
    confirm: (orderId: string) => post<OrderView>(`/api/agent/${orderId}/confirm`),
    cancel: (orderId: string) => post<OrderView>(`/api/agent/${orderId}/cancel`),
  },
  audit: {
    logs: () => request<AuditLog[]>('/api/audit/logs'),
    logsForOrder: (orderId: string) => request<AuditLog[]>(`/api/audit/logs/${orderId}`),
    stats: () => request<MonthlyStats>('/api/audit/stats'),
    trust: () => request<Record<string, number>>('/api/audit/trust'),
    orders: () => request<OrderView[]>('/api/audit/orders'),
  },
}

export const money = (n: number | null | undefined, currency = 'USD') =>
  n == null ? '—' : new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(n)
