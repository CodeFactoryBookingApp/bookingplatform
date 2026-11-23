import { useEffect, useState } from 'react'
import { useIsFetching, useIsMutating } from '@tanstack/react-query'
import { Spinner } from '@/components/ui/Spinner'

/**
 * Render's free plan sleeps the backend after ~15 minutes; the first request then takes up to a
 * minute. If anything stays in flight for a few seconds, say why instead of leaving a blank wait.
 */
export function WakeNotice() {
  const busy = useIsFetching() + useIsMutating() > 0
  const [slow, setSlow] = useState(false)

  useEffect(() => {
    if (!busy) {
      setSlow(false)
      return
    }
    const timer = window.setTimeout(() => setSlow(true), 6000)
    return () => window.clearTimeout(timer)
  }, [busy])

  if (!slow) return null
  return (
    <div
      role="status"
      className="animate-in fixed inset-x-4 bottom-4 z-40 mx-auto flex max-w-md items-center gap-3 rounded-lg border border-line bg-surface p-4 text-sm shadow-lg"
    >
      <Spinner className="size-5 shrink-0 text-accent" />
      <p>
        <strong className="font-semibold">Despertando el servidor…</strong> La primera carga después de un rato sin uso puede tardar un par de minutos.
      </p>
    </div>
  )
}
