import type { ReactNode } from 'react'
import './StatCard.css'

type StatCardProps = {
  label: string
  value: ReactNode
  note?: ReactNode
  hover?: 'green' | 'orange'
}

function StatCard({ label, value, note, hover }: StatCardProps) {
  return (
    <div className={hover ? `stat-card stat-card--${hover}` : 'stat-card'}>
      <dt>{label}</dt>
      <dd>
        {value}
        {note && <span className="stat-card-note">{note}</span>}
      </dd>
    </div>
  )
}

export default StatCard
