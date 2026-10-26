import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { DownloadSimple } from '@phosphor-icons/react'
import { toast } from 'sonner'
import { adminApi } from '@/api/endpoints'
import type { ClientRow } from '@/api/types'
import { StatusBadge } from '@/components/ui/Badge'
import { EmptyState, ErrorState, Pagination } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Form'
import { ReasonModal } from '@/components/ui/ReasonModal'
import { Skeleton } from '@/components/ui/Spinner'
import { errorMessage } from '@/lib/errors'
import { CHANNELS, CLIENT_STATUS, formatDate } from '@/lib/format'
import { useListParams } from '@/lib/useListParams'

const SORTS = [
  { value: 'registered', label: 'Fecha de registro' },
  { value: 'name', label: 'Nombre' },
  { value: 'email', label: 'Correo' },
  { value: 'city', label: 'Ciudad' },
  { value: 'status', label: 'Estado' },
]

export default function AdminClientsPage() {
  const { get, update, query, hasFilters, reset } = useListParams()
  const queryClient = useQueryClient()
  const [target, setTarget] = useState<{ row: ClientRow; action: 'suspension' | 'reactivation' } | null>(null)

  const list = useQuery({
    queryKey: ['admin-clients', query],
    queryFn: () => adminApi.clients(query),
    placeholderData: keepPreviousData,
  })
  const exportCsv = useMutation({
    mutationFn: () => adminApi.exportClients(query),
    onError: (error) => toast.error(errorMessage(error)),
  })

  const run = async (reason: string) => {
    if (!target) return
    await adminApi.clientAction(target.row.id, target.action, reason)
    toast.success(target.action === 'suspension' ? 'Cliente suspendido' : 'Cliente reactivado')
    queryClient.invalidateQueries({ queryKey: ['admin-clients'] })
  }

  const data = list.data
  return (
    <div>
      <section aria-label="Filtros" className="rounded-lg border border-line bg-surface p-4 sm:p-5">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Select label="Estado" value={get('estado')} onChange={(e) => update({ estado: e.target.value || null })}>
            <option value="">Todos</option>
            {Object.entries(CLIENT_STATUS).map(([value, { label }]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </Select>
          <Input
            label="Ciudad"
            key={`city-${get('ciudad')}`}
            defaultValue={get('ciudad')}
            onBlur={(e) => e.target.value.trim() !== get('ciudad') && update({ ciudad: e.target.value.trim() || null })}
            onKeyDown={(e) => e.key === 'Enter' && e.currentTarget.blur()}
          />
          <Input label="Registrado desde" type="date" value={get('desde')} max={get('hasta') || undefined} onChange={(e) => update({ desde: e.target.value || null })} />
          <Input label="Registrado hasta" type="date" value={get('hasta')} min={get('desde') || undefined} onChange={(e) => update({ hasta: e.target.value || null })} />
          <Select label="Ordenar por" value={query.sort} onChange={(e) => update({ orden: e.target.value })}>
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>{s.label}</option>
            ))}
          </Select>
          <Select label="Dirección" value={query.direction} onChange={(e) => update({ dir: e.target.value })}>
            <option value="desc">Más recientes primero</option>
            <option value="asc">Más antiguos primero</option>
          </Select>
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

      <div className="mt-6" aria-busy={list.isFetching}>
        {list.isPending ? (
          <Skeleton className="h-64 w-full" />
        ) : list.isError ? (
          <ErrorState error={list.error} onRetry={() => list.refetch()} />
        ) : data && data.items.length === 0 ? (
          <EmptyState title="Sin clientes" description="Ningún cliente coincide con estos filtros." />
        ) : (
          data && (
            <>
              <div className={`overflow-x-auto rounded-lg border border-line bg-surface ${list.isFetching ? 'opacity-70' : ''}`}>
                <table className="w-full min-w-[52rem] text-left text-sm">
                  <caption className="sr-only">Clientes registrados</caption>
                  <thead className="border-b border-line bg-sunken/60 text-muted">
                    <tr>
                      <th scope="col" className="px-4 py-3 font-medium">Cliente</th>
                      <th scope="col" className="px-4 py-3 font-medium">Contacto</th>
                      <th scope="col" className="px-4 py-3 font-medium">Ciudad</th>
                      <th scope="col" className="px-4 py-3 font-medium">Avisos</th>
                      <th scope="col" className="px-4 py-3 font-medium">Estado</th>
                      <th scope="col" className="px-4 py-3 font-medium">Registro</th>
                      <th scope="col" className="px-4 py-3"><span className="sr-only">Acciones</span></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {data.items.map((row) => (
                      <tr key={row.id} className="align-top">
                        <td className="px-4 py-3">
                          <p className="font-medium">{row.fullName}</p>
                          <p className="text-xs text-muted">{row.document}</p>
                        </td>
                        <td className="px-4 py-3">
                          <p>{row.email}</p>
                          <p className="text-xs text-muted">{row.phone}</p>
                        </td>
                        <td className="px-4 py-3">{row.city}</td>
                        <td className="px-4 py-3">{CHANNELS[row.notificationChannel]}</td>
                        <td className="px-4 py-3"><StatusBadge map={CLIENT_STATUS} value={row.status} /></td>
                        <td className="px-4 py-3 whitespace-nowrap">{formatDate(row.registeredAt)}</td>
                        <td className="px-4 py-3">
                          <div className="flex justify-end">
                            {row.status === 'ACTIVE' && (
                              <Button size="sm" variant="secondary" aria-label={`Suspender a ${row.fullName}`} onClick={() => setTarget({ row, action: 'suspension' })}>
                                Suspender
                              </Button>
                            )}
                            {row.status === 'SUSPENDED' && (
                              <Button size="sm" aria-label={`Reactivar a ${row.fullName}`} onClick={() => setTarget({ row, action: 'reactivation' })}>
                                Reactivar
                              </Button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={data.page} size={data.size} total={data.total} onPage={(p) => update({ pagina: p ? String(p) : null })} />
            </>
          )
        )}
      </div>

      {target && (
        <ReasonModal
          open
          title={`${target.action === 'suspension' ? 'Suspender' : 'Reactivar'} a ${target.row.fullName}`}
          description={target.action === 'suspension' ? 'Un cliente suspendido no puede confirmar reservas.' : 'Vuelve a poder confirmar reservas.'}
          confirmLabel={target.action === 'suspension' ? 'Suspender' : 'Reactivar'}
          danger={target.action === 'suspension'}
          onSubmit={run}
          onClose={() => setTarget(null)}
        />
      )}
    </div>
  )
}
