import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { DownloadSimple, Warning } from '@phosphor-icons/react'
import { toast } from 'sonner'
import { organizationApi } from '@/api/endpoints'
import type { DemandBucket, DemandMetrics } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { Alert, Card, EmptyState, ErrorState } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Form'
import { Skeleton } from '@/components/ui/Spinner'
import { errorMessage } from '@/lib/errors'
import { DAYS, formatDate, formatMoney, formatPercent, isoDate } from '@/lib/format'
import { useOrganization } from './OrganizationLayout'

const MAX_DAYS = 366

function addDays(day: string, days: number): string {
  const date = new Date(`${day}T00:00:00`)
  date.setDate(date.getDate() + days)
  return isoDate(date)
}
const daysBetween = (from: string, to: string) =>
  Math.round((new Date(`${to}T00:00:00`).getTime() - new Date(`${from}T00:00:00`).getTime()) / 86_400_000)

function Variation({ value, inverse = false }: { value: number | null | undefined; inverse?: boolean }) {
  if (value === null || value === undefined) return <span className="text-xs text-faint">sin base</span>
  const good = inverse ? value < 0 : value > 0
  const tone = value === 0 ? 'text-muted' : good ? 'text-ok' : 'text-danger'
  return <span className={`text-xs tabular-nums ${tone}`}>{formatPercent(value, true)}</span>
}

const revenue = (m: DemandMetrics) => {
  const entries = Object.entries(m.estimatedRevenueByCurrency)
  return entries.length ? entries.map(([c, v]) => formatMoney(v, c)).join(' · ') : formatMoney(0)
}

/** Bookings per weekday (rows) and hour (columns) in the organization's zone. */
function Heatmap({ buckets, label }: { buckets: DemandBucket[]; label: string }) {
  const max = Math.max(1, ...buckets.map((b) => b.bookings))
  const count = (day: number, hour: number) => buckets.find((b) => b.dayOfWeek === day && b.hour === hour)?.bookings ?? 0
  const hours = Array.from({ length: 24 }, (_, h) => h)
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[44rem] border-separate border-spacing-0.5 text-xs">
        <caption className="sr-only">{label}</caption>
        <thead>
          <tr>
            <th scope="col" className="w-12" />
            {hours.map((h) => (
              <th key={h} scope="col" className="font-normal text-muted tabular-nums">
                {h % 3 === 0 ? h : ''}
                <span className="sr-only">{h}:00</span>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {DAYS.map((day, index) => (
            <tr key={day.value}>
              <th scope="row" className="pr-2 text-left font-normal text-muted">{day.short}</th>
              {hours.map((h) => {
                const n = count(index + 1, h)
                const share = Math.round((n / max) * 100)
                return (
                  <td
                    key={h}
                    title={`${day.label} ${h}:00 · ${n} reservas`}
                    className="h-6 rounded-sm text-center tabular-nums"
                    style={{
                      background: n ? `color-mix(in srgb, var(--color-accent) ${15 + share * 0.85}%, transparent)` : 'var(--color-sunken)',
                      color: share > 55 ? 'white' : undefined,
                    }}
                  >
                    {n || ''}
                    <span className="sr-only">{n ? '' : '0'}</span>
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** HU-020: usage, demand and comparison with the previous period of equal length. */
export default function ReportsPage() {
  const org = useOrganization()
  const today = isoDate(new Date())
  const [range, setRange] = useState({ from: addDays(today, -29), until: today })
  const [compare, setCompare] = useState(false)
  // The API takes [from, to) as dates; the form shows the last day included.
  const to = addDays(range.until, 1)
  const days = daysBetween(range.from, to)
  const valid = days >= 1 && days <= MAX_DAYS

  const report = useQuery({
    queryKey: ['service-demand', org.id, range.from, to],
    queryFn: () => organizationApi.demand(org.id, range.from, to),
    enabled: valid,
  })
  const exportCsv = useMutation({
    mutationFn: () => organizationApi.exportDemand(org.id, range.from, to),
    onError: (error) => toast.error(errorMessage(error)),
  })
  // HU-019 takes both dates included, unlike the demand report.
  const occupancy = useQuery({
    queryKey: ['occupancy', org.id, range.from, range.until],
    queryFn: () => organizationApi.occupancy(org.id, range.from, range.until),
    enabled: valid,
  })

  const data = report.data
  return (
    <div className="flex flex-col gap-6">
      <section aria-label="Periodo" className="flex flex-wrap items-end gap-4 rounded-lg border border-line bg-surface p-4 sm:p-5">
        <Input label="Desde" type="date" value={range.from} max={range.until} onChange={(e) => e.target.value && setRange((r) => ({ ...r, from: e.target.value }))} />
        <Input label="Hasta (incluido)" type="date" value={range.until} min={range.from} onChange={(e) => e.target.value && setRange((r) => ({ ...r, until: e.target.value }))} />
        <div className="flex gap-2">
          {[7, 30, 90].map((n) => (
            <Button key={n} size="sm" variant="ghost" onClick={() => setRange({ from: addDays(today, -(n - 1)), until: today })}>
              Últimos {n} días
            </Button>
          ))}
        </div>
        <Button variant="secondary" size="sm" className="ml-auto" disabled={!valid} loading={exportCsv.isPending} onClick={() => exportCsv.mutate()}>
          <DownloadSimple size={16} aria-hidden="true" /> Exportar CSV
        </Button>
      </section>

      {!valid ? (
        <Alert tone="warn" title={`El periodo debe tener entre 1 y ${MAX_DAYS} días.`} />
      ) : report.isPending ? (
        <Skeleton className="h-72 w-full" />
      ) : report.isError ? (
        <ErrorState error={report.error} onRetry={() => report.refetch()} />
      ) : (
        data && (
          <>
            <p className="text-sm text-muted">
              Del {formatDate(data.period.from)} al {formatDate(addDays(data.period.to, -1))}, comparado con el{' '}
              {formatDate(data.previousPeriod.from)} al {formatDate(addDays(data.previousPeriod.to, -1))}. Zona horaria {data.timezone}.
              Las reservas se agrupan por fecha de creación; los ingresos suman confirmadas y completadas.
            </p>

            {data.services.length === 0 ? (
              <EmptyState title="Sin actividad en este periodo" description="Prueba con un periodo más largo." />
            ) : (
              <div className="overflow-x-auto rounded-lg border border-line bg-surface">
                <table className="w-full min-w-[56rem] text-left text-sm">
                  <caption className="sr-only">Métricas por servicio</caption>
                  <thead className="border-b border-line bg-sunken/60 text-muted">
                    <tr>
                      <th scope="col" className="px-4 py-3 font-medium">Servicio</th>
                      <th scope="col" className="px-4 py-3 font-medium text-right">Creadas</th>
                      <th scope="col" className="px-4 py-3 font-medium text-right">Completadas</th>
                      <th scope="col" className="px-4 py-3 font-medium text-right">Cancelación</th>
                      <th scope="col" className="px-4 py-3 font-medium text-right">Inasistencia</th>
                      <th scope="col" className="px-4 py-3 font-medium text-right">Ingresos estimados</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {data.services.map((s) => (
                      <tr key={s.id} className="align-top">
                        <td className="px-4 py-3">
                          <p className="font-medium">{s.name}</p>
                          {s.highCancellation && (
                            <Badge tone="danger">
                              <Warning size={12} className="mr-1" aria-hidden="true" /> Cancelación alta
                            </Badge>
                          )}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {s.current.created} <span className="block text-xs text-muted">antes {s.previous.created}</span>
                          <Variation value={s.variation.createdPercent} />
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {s.current.completed} <span className="block text-xs text-muted">{formatPercent(s.current.completionRatePercent)}</span>
                          <Variation value={s.variation.completedPercent} />
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {formatPercent(s.current.cancellationRatePercent)}
                          <span className="block text-xs text-muted">{s.current.cancelled} canceladas</span>
                          <Variation value={s.variation.cancellationRatePercent} inverse />
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {formatPercent(s.current.noShowRatePercent)}
                          <span className="block text-xs text-muted">{s.current.noShow} sin asistir</span>
                          <Variation value={s.variation.noShowRatePercent} inverse />
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {revenue(s.current)}
                          <span className="block text-xs text-muted">antes {revenue(s.previous)}</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <Card>
              <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h2 className="text-lg">Demanda por día y hora</h2>
                  <p className="text-sm text-muted">Según la hora de la cita.</p>
                </div>
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" className="size-4 accent-accent" checked={compare} onChange={(e) => setCompare(e.target.checked)} />
                  Ver el periodo anterior
                </label>
              </div>
              <Heatmap buckets={compare ? data.previousDemand : data.demand} label={compare ? 'Demanda del periodo anterior' : 'Demanda del periodo'} />
            </Card>

            <Card>
              <h2 className="text-lg">Ocupación por agenda</h2>
              <p className="mb-4 text-sm text-muted">Horarios ocupados sobre los ofrecidos en el periodo, sin contar los días cerrados.</p>
              {occupancy.isPending ? (
                <Skeleton className="h-24 w-full" />
              ) : occupancy.isError ? (
                <ErrorState error={occupancy.error} onRetry={() => occupancy.refetch()} />
              ) : occupancy.data.length === 0 ? (
                <EmptyState title="Sin agendas" description="Crea una agenda para medir su ocupación." />
              ) : (
                <ul className="flex flex-col gap-4">
                  {occupancy.data.map((a) => {
                    const percent = Math.round(a.occupancyRate * 1000) / 10
                    return (
                      <li key={a.agendaId}>
                        <div className="mb-1 flex flex-wrap items-baseline justify-between gap-2 text-sm">
                          <span className="font-medium">{a.agendaName}</span>
                          <span className="tabular-nums text-muted">
                            {a.occupiedSlots} de {a.totalSlots} horarios · <span className="font-semibold text-ink">{formatPercent(percent)}</span>
                          </span>
                        </div>
                        <div
                          className="h-2.5 overflow-hidden rounded-full bg-sunken"
                          role="meter"
                          aria-label={`Ocupación de ${a.agendaName}`}
                          aria-valuemin={0}
                          aria-valuemax={100}
                          aria-valuenow={percent}
                        >
                          <div className="h-full rounded-full bg-accent" style={{ width: `${Math.min(100, percent)}%` }} />
                        </div>
                      </li>
                    )
                  })}
                </ul>
              )}
            </Card>
          </>
        )
      )}
    </div>
  )
}
