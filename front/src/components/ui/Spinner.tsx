import clsx from 'clsx'

export function Spinner({ className }: { className?: string }) {
  return (
    <svg
      className={clsx('animate-[spin_700ms_linear_infinite]', className ?? 'size-5')}
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="3" opacity="0.25" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  )
}

export function PageLoader({ label = 'Cargando…' }: { label?: string }) {
  return (
    <div role="status" className="flex min-h-[40vh] flex-col items-center justify-center gap-3 text-muted">
      <Spinner className="size-7 text-accent" />
      <p className="text-sm">{label}</p>
    </div>
  )
}

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden="true" className={clsx('skeleton', className ?? 'h-4 w-full')} />
}
