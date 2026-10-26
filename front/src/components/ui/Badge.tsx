import clsx from 'clsx'
import type { Tone } from '@/lib/format'

const tones: Record<Tone, string> = {
  ok: 'bg-ok-soft text-ok',
  warn: 'bg-warn-soft text-warn',
  danger: 'bg-danger-soft text-danger',
  info: 'bg-info-soft text-info',
  neutral: 'bg-sunken text-muted',
}

/** Status is always text as well as color. */
export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: React.ReactNode }) {
  return (
    <span className={clsx('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold', tones[tone])}>
      {children}
    </span>
  )
}

export function StatusBadge({ map, value }: { map: Record<string, { label: string; tone: Tone }>; value: string }) {
  const entry = map[value]
  return <Badge tone={entry?.tone}>{entry?.label ?? value}</Badge>
}
