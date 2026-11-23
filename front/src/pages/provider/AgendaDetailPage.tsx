import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CaretLeft, CaretRight, Trash } from '@phosphor-icons/react'
import { toast } from 'sonner'
import { organizationApi } from '@/api/endpoints'
import type { AgendaException, AgendaExceptionType } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { Alert, Card, EmptyState, ErrorState } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input, Textarea } from '@/components/ui/Form'
import { PageLoader, Skeleton } from '@/components/ui/Spinner'
import { errorMessage, fieldErrors } from '@/lib/errors'
import { formatDate, isoDate } from '@/lib/format'
import { hhmm } from './BlocksEditor'
import { useOrganization } from './OrganizationLayout'

const DAYS_SHOWN = 14
const REASON_MAX = 300

function addDays(day: string, days: number): string {
  const date = new Date(`${day}T00:00:00`)
  date.setDate(date.getDate() + days)
  return isoDate(date)
}

const weekday = new Intl.DateTimeFormat('es-CO', { weekday: 'short', day: 'numeric', month: 'short' })

/** HU-007 exceptions and HU-008/HU-011 availability calendar of one agenda. */
export default function AgendaDetailPage() {
  const org = useOrganization()
  const { agendaId = '' } = useParams()
  const queryClient = useQueryClient()
  const today = isoDate(new Date())

  const agenda = useQuery({ queryKey: ['agenda', org.id, agendaId], queryFn: () => organizationApi.agenda(org.id, agendaId) })
  const exceptionsKey = ['agenda-exceptions', org.id, agendaId]
  const exceptions = useQuery({ queryKey: exceptionsKey, queryFn: () => organizationApi.agendaExceptions(org.id, agendaId) })

  // Availability is shown in the agenda's location zone, whatever the browser's zone is.
  const location = org.locations.find((l) => l.id === agenda.data?.locationId)
  const zone = location?.timezone || org.timezone
  const time = useMemo(
    () => new Intl.DateTimeFormat('es-CO', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: zone }),
    [zone],
  )

  const [start, setStart] = useState(today)
  const end = addDays(start, DAYS_SHOWN - 1)
  const availability = useQuery({
    queryKey: ['agenda-availability', org.id, agendaId, start, end],
    queryFn: () => organizationApi.agendaAvailability(org.id, agendaId, start, end),
  })

  // New exception form
  const [type, setType] = useState<AgendaExceptionType>('CLOSED')
  const [date, setDate] = useState('')
  const [from, setFrom] = useState('09:00')
  const [until, setUntil] = useState('13:00')
  const [reason, setReason] = useState('')
  const [formError, setFormError] = useState<string | null>(null)

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: exceptionsKey })
    queryClient.invalidateQueries({ queryKey: ['agenda-availability', org.id, agendaId] })
  }

  const create = useMutation({
    mutationFn: () =>
      type === 'CLOSED'
        ? organizationApi.closeAgendaDate(org.id, agendaId, { date, reason: reason.trim() || undefined })
        : organizationApi.specialHours(org.id, agendaId, { date, startTime: from, endTime: until, reason: reason.trim() || undefined }),
    onSuccess: (created) => {
      toast.success(created.type === 'CLOSED' ? `Cerrado el ${formatDate(created.date)}` : `Horario especial el ${formatDate(created.date)}`)
      setDate('')
      setReason('')
      refresh()
    },
    onError: (error) => {
      const fields = fieldErrors(error)
      setFormError(fields.length ? fields.map(([, message]) => message).join(' ') : errorMessage(error))
    },
  })

  const remove = useMutation({
    mutationFn: (exception: AgendaException) => organizationApi.deleteAgendaException(org.id, agendaId, exception.id),
    onSuccess: () => {
      toast.success('Excepción eliminada')
      refresh()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const submit = (event: React.FormEvent) => {
    event.preventDefault()
    setFormError(null)
    if (!date) return setFormError('Elige la fecha.')
    if (type === 'SPECIAL_HOURS' && (!from || !until || until <= from)) return setFormError('La hora de fin debe ser posterior a la de inicio.')
    if (exceptions.data?.some((e) => e.date === date)) return setFormError('Esa fecha ya tiene una excepción. Elimínala primero si quieres cambiarla.')
    create.mutate()
  }

  if (agenda.isPending) return <PageLoader label="Cargando la agenda…" />
  if (agenda.isError) return <ErrorState error={agenda.error} onRetry={() => agenda.refetch()} />

  const byDate = new Map((exceptions.data ?? []).map((e) => [e.date, e]))
  const sorted = [...(exceptions.data ?? [])].sort((a, b) => a.date.localeCompare(b.date))
  const upcoming = sorted.filter((e) => e.date >= today)
  const past = sorted.filter((e) => e.date < today)

  const exceptionLabel = (e: AgendaException) =>
    e.type === 'CLOSED' ? 'Cerrado' : `Horario especial ${hhmm(e.specialStart ?? '')}–${hhmm(e.specialEnd ?? '')}`

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link to={`/negocio/${org.id}/agendas`} className="mb-2 inline-flex items-center gap-1 text-sm text-muted hover:text-ink">
          <CaretLeft size={16} aria-hidden="true" /> Agendas
        </Link>
        <h2 className="text-xl">{agenda.data.name}</h2>
        <p className="text-sm text-muted">
          {location?.name ?? 'Sede'} · cada {agenda.data.slotMinutes} min · zona {zone}
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_24rem]">
        <Card>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3 className="text-lg">Disponibilidad</h3>
              <p className="text-sm text-muted">Horarios libres, ya descontadas reservas, excepciones y recursos.</p>
            </div>
            <div className="flex items-center gap-2">
              <Button variant="secondary" size="sm" aria-label="Dos semanas antes" disabled={start <= today} onClick={() => setStart(addDays(start, -DAYS_SHOWN) < today ? today : addDays(start, -DAYS_SHOWN))}>
                <CaretLeft size={16} aria-hidden="true" />
              </Button>
              <span className="text-sm tabular-nums">{formatDate(start)} – {formatDate(end)}</span>
              <Button variant="secondary" size="sm" aria-label="Dos semanas después" onClick={() => setStart(addDays(start, DAYS_SHOWN))}>
                <CaretRight size={16} aria-hidden="true" />
              </Button>
            </div>
          </div>
          {availability.isPending ? (
            <Skeleton className="h-64 w-full" />
          ) : availability.isError ? (
            <ErrorState error={availability.error} onRetry={() => availability.refetch()} />
          ) : (
            <ul className="grid gap-2 sm:grid-cols-2">
              {availability.data.map((day) => {
                const exception = byDate.get(day.date)
                return (
                  <li key={day.date} className="rounded-md border border-line p-3">
                    <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                      <p className="text-sm font-medium capitalize">{weekday.format(new Date(`${day.date}T00:00:00`))}</p>
                      {exception ? (
                        <Badge tone={exception.type === 'CLOSED' ? 'danger' : 'warn'}>{exceptionLabel(exception)}</Badge>
                      ) : (
                        <span className="text-xs text-muted">{day.slots.length} libres</span>
                      )}
                    </div>
                    {day.slots.length === 0 ? (
                      <p className="text-xs text-faint">Sin horarios libres</p>
                    ) : (
                      <p className="flex flex-wrap gap-1">
                        {day.slots.map((slot) => (
                          <span key={slot.startsAt} className="rounded bg-sunken px-1.5 py-0.5 text-xs tabular-nums">
                            {time.format(new Date(slot.startsAt))}
                          </span>
                        ))}
                      </p>
                    )}
                  </li>
                )
              })}
            </ul>
          )}
        </Card>

        <div className="flex flex-col gap-6">
          <Card>
            <h3 className="text-lg">Nueva excepción</h3>
            <p className="mb-4 text-sm text-muted">Festivos, cierres o un horario distinto en una fecha. Una por fecha.</p>
            <form noValidate onSubmit={submit} className="flex flex-col gap-4">
              <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="Tipo de excepción">
                {(
                  [
                    ['CLOSED', 'Cerrado'],
                    ['SPECIAL_HOURS', 'Horario especial'],
                  ] as const
                ).map(([value, label]) => (
                  <label
                    key={value}
                    className={`flex min-h-11 cursor-pointer items-center justify-center rounded-md border text-sm font-medium ${
                      type === value ? 'border-accent bg-accent text-white' : 'border-line-strong hover:bg-sunken'
                    }`}
                  >
                    <input type="radio" name="type" value={value} className="sr-only" checked={type === value} onChange={() => setType(value)} />
                    {label}
                  </label>
                ))}
              </div>
              <Input label="Fecha" type="date" required min={today} value={date} onChange={(e) => setDate(e.target.value)} />
              {type === 'SPECIAL_HOURS' && (
                <div className="grid grid-cols-2 gap-3">
                  <Input label="Desde" type="time" required value={from} onChange={(e) => setFrom(e.target.value)} />
                  <Input label="Hasta" type="time" required value={until} onChange={(e) => setUntil(e.target.value)} />
                </div>
              )}
              <Textarea
                label="Motivo"
                hint={`Opcional. Máximo ${REASON_MAX} caracteres.`}
                maxLength={REASON_MAX}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
              {formError && <Alert tone="danger" title={formError} />}
              <Button type="submit" loading={create.isPending}>
                Guardar excepción
              </Button>
            </form>
          </Card>

          <Card>
            <h3 className="mb-3 text-lg">Excepciones</h3>
            {exceptions.isPending ? (
              <Skeleton className="h-24 w-full" />
            ) : exceptions.isError ? (
              <ErrorState error={exceptions.error} onRetry={() => exceptions.refetch()} />
            ) : sorted.length === 0 ? (
              <EmptyState title="Sin excepciones" description="La agenda sigue su horario semanal todos los días." />
            ) : (
              <ul className="divide-y divide-line">
                {[...upcoming, ...past].map((e) => (
                  <li key={e.id} className={`flex items-start justify-between gap-3 py-3 ${e.date < today ? 'opacity-60' : ''}`}>
                    <div className="min-w-0">
                      <p className="text-sm font-medium">{formatDate(e.date)}</p>
                      <Badge tone={e.type === 'CLOSED' ? 'danger' : 'warn'}>{exceptionLabel(e)}</Badge>
                      {e.reason && <p className="mt-1 text-sm text-muted">{e.reason}</p>}
                    </div>
                    <Button
                      size="sm"
                      variant="ghost"
                      aria-label={`Eliminar la excepción del ${formatDate(e.date)}`}
                      loading={remove.isPending && remove.variables?.id === e.id}
                      onClick={() => remove.mutate(e)}
                    >
                      <Trash size={16} aria-hidden="true" />
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>
    </div>
  )
}
