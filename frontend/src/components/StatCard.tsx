export default function StatCard({
  label,
  value,
  hero = false,
}: {
  label: string
  value: string | number
  hero?: boolean
}) {
  return (
    <div className={`stat${hero ? ' hero' : ''}`}>
      <div className="label">{label}</div>
      <div className="value">{value}</div>
    </div>
  )
}
