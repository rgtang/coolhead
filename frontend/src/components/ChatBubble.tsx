import type { Turn } from '../api/client'

export default function ChatBubble({ turn }: { turn: Turn }) {
  if (turn.role === 'system') return null
  return <div className={`bubble ${turn.role}`}>{turn.text}</div>
}

export function TypingBubble() {
  return <div className="bubble assistant typing">Coolhead is thinking…</div>
}
