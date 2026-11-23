import type { ReactNode } from 'react'
import clsx from 'clsx'
import { CaretLeft, CaretRight, CheckCircle, Info, Warning, WarningCircle } from '@phosphor-icons/react'
import { errorMessage, traceOf } from '@/lib/errors'
import { Button } from './Button'

export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <h1 className="text-2xl sm:text-3xl">{title}</h1>
        {description && <p className="mt-1.5 max-w-prose text-muted">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return <section className={clsx('rounded-lg border border-line bg-surface p-5 sm:p-6', className)}>{children}</section>
}

type AlertTone = 'info' | 'ok' | 'warn' | 'danger'
const alertStyles: Record<AlertTone, { box: string; icon: typeof Info }> = {
  info: { box: 'border-info/25 bg-info-soft text-ink', icon: Info },
  ok: { box: 'border-ok/25 bg-ok-soft text-ink', icon: CheckCircle },
  warn: { box: 'border-warn/25 bg-warn-soft text-ink', icon: Warning },
  danger: { box: 'border-danger/25 bg-danger-soft text-ink', icon: WarningCircle },
}

export function Alert({ tone = 'info', title, children }: { tone?: AlertTone; title?: string; children?: ReactNode }) {
  const { box, icon: Icon } = alertStyles[tone]
  return (
    <div role={tone === 'danger' ? 'alert' : 'status'} className={clsx('flex gap-3 rounded-md border p-3.5 text-sm', box)}>
      <Icon size={20} weight="fill" className="mt-0.5 shrink-0" aria-hidden="true" />
      <div className="min-w-0">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className={title ? 'mt-0.5 text-muted' : ''}>{children}</div>}
      </div>
    </div>
  )
}

/** A failed request: what happened in plain words, a retry, and the trace id when it is the server's fault. */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const trace = traceOf(error)
  return (
    <div className="flex flex-col items-start gap-3">
      <Alert tone="danger" title="No pudimos cargar la información">
        {errorMessage(error)}
        {trace && <span className="mt-1 block text-xs">Código de soporte: {trace}</span>}
      </Alert>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          Reintentar
        </Button>
      )}
    </div>
  )
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center rounded-lg border border-dashed border-line-strong px-6 py-12 text-center">
      <h3 className="text-lg">{title}</h3>
      {description && <p className="mt-1.5 max-w-sm text-sm text-muted">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function Pagination({
  page,
  size,
  total,
  onPage,
}: {
  page: number
  size: number
  total: number
  onPage: (page: number) => void
}) {
  const pages = Math.max(1, Math.ceil(total / size))
  if (total === 0) return null
  const from = page * size + 1
  const to = Math.min(total, (page + 1) * size)
  return (
    <nav aria-label="Paginación" className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm text-muted">
      <p>
        {from}–{to} de {total}
      </p>
      <div className="flex items-center gap-2">
        <Button variant="secondary" size="sm" disabled={page <= 0} onClick={() => onPage(page - 1)}>
          <CaretLeft size={16} aria-hidden="true" /> Anterior
        </Button>
        <span aria-live="polite">
          Página {page + 1} de {pages}
        </span>
        <Button variant="secondary" size="sm" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>
          Siguiente <CaretRight size={16} aria-hidden="true" />
        </Button>
      </div>
    </nav>
  )
}
