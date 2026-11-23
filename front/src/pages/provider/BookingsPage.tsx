import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { DownloadSimple } from '@phosphor-icons/react'
import { toast } from 'sonner'
import { organizationApi } from '@/api/endpoints'
import type { BookingQuery, BookingRow, BookingStatus } from '@/api/types'
import { StatusBadge } from '@/components/ui/Badge'
import { EmptyState, ErrorState, Pagination } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Form'
import { ReasonModal } from '@/components/ui/ReasonModal'
import { Skeleton } from '@/components/ui/Spinner'
import { errorMessage } from '@/lib/errors'
import { BOOKING_STATUS, endOfDayInstant, formatDateTime, formatMoney, formatPercent, startOfDayInstant } from '@/lib/format'
import { CANCEL_REASON_MAX } from '@/lib/schemas'
import { useFilterParams } from '@/lib/useFilterParams'
import { useOrganization } from './OrganizationLayout'

const PAGE_SIZE = 20
const FILTERS = ['desde', 'hasta', 'estado', 'servicio', 'sede', 'codigo']

function Stat({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-line bg-surface p-4">
      <p className="text-xs font-medium text-muted">{label}</p>
      <p className="mt-1 font-display text-xl font-semibold tabular-nums">{value}</p>
    </div>
  )
}

/** HU-018: search, summary and export of the organization's bookings, with confirm and cancel (HU-012, HU-013). */
export default function BookingsPage() {
  const org = useOrganization()
  const queryClient = useQueryClient()
  const { get, update, page, setPage, hasFilters, reset } = useFilterParams(FILTERS)
  const [cancelling, setCancelling] = useState<BookingRow | null>(null)

  const services = useQuery({ queryKey: ['services', org.id], queryFn: () => organizationApi.services(org.id) })

  const query: BookingQuery = {
    from: get('desde') ? startOfDayInstant(get('desde')) : undefined,
    to: get('hasta') ? endOfDayInstant(get('hasta')) : undefined,
    status: (get('estado') || undefined) as BookingStatus | undefined,
    serviceId: get('servicio') || undefined,
    locationId: get('sede') || undefined,
    code: get('codigo') || undefined,
    page,
    size: PAGE_SIZE,
  }

  const list = useQuery({
    queryKey: ['bookings', org.id, query],
    queryFn: () => organizationApi.bookings(org.id, query),
    placeholderData: keepPreviousData,
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['bookings', org.id] })

  const exportCsv = useMutation({
    mutationFn: () => organizationApi.exportBookings(org.id, query),
    onError: (error) => toast.error(errorMessage(error)),
  })
  const confirm = useMutation({
    mutationFn: (row: BookingRow) => organizationApi.confirmBooking(org.id, row.id),
    onSuccess: (receipt) => {
      toast.success(`Reserva ${receipt.code} confirmada`)
      refresh()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const data = list.data
  const summary = data?.summary
  const amounts = summary ? Object.entries(summary.amountByCurrency) : []
  const now = Date.now()

  return (
    <div>
      <section aria-label="Filtros" className="rounded-lg border border-line bg-surface p-4 sm:p-5">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Input label="Citas desde" type="date" value={get('desde')} max={get('hasta') || undefined} onChange={(e) => update({ desde: e.target.value || null })} />
          <Input label="Citas hasta" type="date" value={get('hasta')} min={get('desde') || undefined} onChange={(e) => update({ hasta: e.target.value || null })} />
          <Select label="Estado" value={get('estado')} onChange={(e) => update({ estado: e.target.value || null })}>
            <option value="">Todos</option>
            {Object.entries(BOOKING_STATUS).map(([value, { label }]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </Select>
          <Select label="Servicio" value={get('servicio')} onChange={(e) => update({ servicio: e.target.value || null })}>
            <option value="">Todos</option>
            {services.data?.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </Select>
          <Select label="Sede" value={get('sede')} onChange={(e) => update({ sede: e.target.value || null })}>
            <option value="">Todas</option>
            {org.locations.map((l) => (
              <option key={l.id} value={l.id}>{l.name}</option>
            ))}
          </Select>
          <Input
            label="Código de reserva"
            key={`code-${get('codigo')}`}
            defaultValue={get('codigo')}
            spellCheck={false}
            onBlur={(e) => e.target.value.trim() !== get('codigo') && update({ codigo: e.target.value.trim() || null })}
            onKeyDown={(e) => e.key === 'Enter' && e.currentTarget.blur()}
          />
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          {hasFilters && (
            <Button variant="ghost" size="sm" onClick={reset}>
              Restablecer filtros
            </Button>
          )}
          <Button variant="secondary" size="sm" className="ml-auto" loading={exportCsv.isPending} onClick={() => exportCsv.mutate()}>
            <DownloadSimple size={16} aria-hidden="true" /> Exportar CSV
          </Button>
        </div>
      </section>

      {summary && (
        <section aria-label="Resumen" className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Stat label="Reservas" value={summary.total} />
          <Stat label="Confirmadas" value={summary.byStatus.CONFIRMED ?? 0} />
          <Stat label="Tasa de cancelación" value={formatPercent(summary.cancellationRatePercent)} />
          <Stat
            label="Importe"
            value={amounts.length === 0 ? formatMoney(0) : amounts.map(([currency, amount]) => formatMoney(amount, currency)).join(' · ')}
          />
        </section>
      )}

      <div className="mt-6" aria-busy={list.isFetching}>
        {list.isPending ? (
          <Skeleton className="h-64 w-full" />
        ) : list.isError ? (
          <ErrorState error={list.error} onRetry={() => list.refetch()} />
        ) : data && data.items.length === 0 ? (
          <EmptyState title="Sin reservas" description={hasFilters ? 'Ninguna reserva coincide con estos filtros.' : 'Cuando tus clientes reserven, aparecerán aquí.'} />
        ) : (
          data && (
            <>
              <div className={`overflow-x-auto rounded-lg border border-line bg-surface ${list.isFetching ? 'opacity-70' : ''}`}>
                <table className="w-full min-w-[56rem] text-left text-sm">
                  <caption className="sr-only">Reservas del negocio</caption>
                  <thead className="border-b border-line bg-sunken/60 text-muted">
                    <tr>
                      <th scope="col" className="px-4 py-3 font-medium">Código</th>
                      <th scope="col" className="px-4 py-3 font-medium">Cliente</th>
                      <th scope="col" className="px-4 py-3 font-medium">Servicio</th>
                      <th scope="col" className="px-4 py-3 font-medium">Inicio</th>
                      <th scope="col" className="px-4 py-3 font-medium text-right">Importe</th>
                      <th scope="col" className="px-4 py-3 font-medium">Estado</th>
                      <th scope="col" className="px-4 py-3"><span className="sr-only">Acciones</span></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {data.items.map((row) => {
                      const future = new Date(row.startsAt).getTime() > now
                      const active = row.status === 'RESERVED' || row.status === 'CONFIRMED'
                      return (
                        <tr key={row.id} className="align-top">
                          <td className="px-4 py-3 font-mono text-xs">{row.code}</td>
                          <td className="px-4 py-3">
                            <p className="font-medium">{row.clientName}</p>
                            {row.attendees > 1 && <p className="text-xs text-muted">{row.attendees} asistentes</p>}
                          </td>
                          <td className="px-4 py-3">
                            <p>{row.serviceName}</p>
                            <p className="text-xs text-muted">{row.locationName}</p>
                          </td>
                          <td className="px-4 py-3 whitespace-nowrap">{formatDateTime(row.startsAt)}</td>
                          <td className="px-4 py-3 text-right tabular-nums">{formatMoney(row.totalPrice, row.currency)}</td>
                          <td className="px-4 py-3"><StatusBadge map={BOOKING_STATUS} value={row.status} /></td>
                          <td className="px-4 py-3">
                            <div className="flex justify-end gap-2">
                              {row.status === 'RESERVED' && future && (
                                <Button
                                  size="sm"
                                  loading={confirm.isPending && confirm.variables?.id === row.id}
                                  aria-label={`Confirmar la reserva ${row.code}`}
                                  onClick={() => confirm.mutate(row)}
                                >
                                  Confirmar
                                </Button>
                              )}
                              {active && future && (
                                <Button size="sm" variant="secondary" aria-label={`Cancelar la reserva ${row.code}`} onClick={() => setCancelling(row)}>
                                  Cancelar
                                </Button>
                              )}
                            </div>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
              <Pagination page={data.page} size={data.size} total={data.summary.total} onPage={setPage} />
            </>
          )
        )}
      </div>

      {cancelling && (
        <ReasonModal
          open
          title={`Cancelar la reserva ${cancelling.code}`}
          description={`${cancelling.clientName} · ${cancelling.serviceName}, ${formatDateTime(cancelling.startsAt)}. El cliente recibe el aviso con el motivo.`}
          confirmLabel="Cancelar reserva"
          danger
          maxLength={CANCEL_REASON_MAX}
          onSubmit={async (reason) => {
            const receipt = await organizationApi.cancelBooking(org.id, cancelling.id, reason)
            toast.success(`Reserva ${receipt.code} cancelada`)
            refresh()
          }}
          onClose={() => setCancelling(null)}
        />
      )}
    </div>
  )
}
