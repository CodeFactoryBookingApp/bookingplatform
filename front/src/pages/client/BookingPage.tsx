import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarCheck, CaretLeft, Clock, MapPin } from '@phosphor-icons/react'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import { clientBookingApi } from '@/api/endpoints'
import type { BookingPreview, BookingReceipt, ServiceCard, TimeSlot } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { ClientCancelModal } from '@/components/booking/ClientCancelModal'
import { StatusBadge } from '@/components/ui/Badge'
import { Alert, Card, EmptyState, ErrorState, PageHeader } from '@/components/ui/Blocks'
import { Button, LinkButton } from '@/components/ui/Button'
import { Input } from '@/components/ui/Form'
import { Skeleton } from '@/components/ui/Spinner'
import { errorMessage } from '@/lib/errors'
import { BOOKING_STATUS, formatDate, formatDuration, formatMoney, formatSlotTime, isoDate } from '@/lib/format'
import { saveBooking, updateBookingStatus } from '@/lib/myBookings'

function humanWindow(minutes: number): string {
  if (minutes === 0) return 'Sin cancelación gratuita'
  if (minutes % 1440 === 0) return `Gratis hasta ${minutes / 1440} ${minutes === 1440 ? 'día' : 'días'} antes`
  if (minutes % 60 === 0) return `Gratis hasta ${minutes / 60} ${minutes === 60 ? 'hora' : 'horas'} antes`
  return `Gratis hasta ${minutes} minutos antes`
}

/** HU-012: pick a day, a free slot, review the exact terms and book them. */
export default function BookingPage() {
  const { serviceId = '', locationId = '' } = useParams()
  const card = (useLocation().state as { card?: ServiceCard } | null)?.card
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const today = isoDate(new Date())
  const [date, setDate] = useState(today)
  const [slot, setSlot] = useState<TimeSlot | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [receipt, setReceipt] = useState<{ booking: BookingReceipt; preview: BookingPreview } | null>(null)
  const [cancelling, setCancelling] = useState(false)

  const slotsKey = ['availability', serviceId, locationId, date]
  const slots = useQuery({
    queryKey: slotsKey,
    queryFn: () => clientBookingApi.availability(serviceId, locationId, date),
    enabled: Boolean(date) && !receipt,
  })
  const startsAt = slot ? new Date(slot.startsAt).toISOString() : ''
  const preview = useQuery({
    queryKey: ['booking-preview', serviceId, locationId, startsAt],
    queryFn: () => clientBookingApi.preview(serviceId, locationId, startsAt),
    enabled: Boolean(slot) && !receipt,
  })

  const book = useMutation({
    mutationFn: (terms: BookingPreview) =>
      clientBookingApi.create({ serviceId, locationId, startsAt, termsVersion: terms.termsVersion }),
    onSuccess: (booking, terms) => {
      setReceipt({ booking, preview: terms })
      if (user) {
        saveBooking(user.id, {
          id: booking.id,
          code: booking.code,
          status: booking.status,
          service: terms.service,
          organization: terms.organization,
          location: terms.location,
          startsAt: terms.slot.startsAt,
          endsAt: terms.slot.endsAt,
          price: terms.price,
          currency: terms.currency,
          freeCancellationWindowMinutes: terms.freeCancellationWindowMinutes,
          savedAt: new Date().toISOString(),
        })
      }
      queryClient.invalidateQueries({ queryKey: ['availability', serviceId, locationId] })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.errorCode === 'BOOKING_TERMS_CHANGED') {
        setNotice('Las condiciones cambiaron mientras reservabas. Revisa el resumen actualizado y confirma de nuevo.')
        preview.refetch()
      } else if (error instanceof ApiError && error.errorCode === 'SLOT_UNAVAILABLE') {
        setNotice('Alguien tomó ese horario antes. Elige otro.')
        setSlot(null)
        slots.refetch()
      } else {
        setNotice(errorMessage(error))
      }
    },
  })

  const title = card?.name ?? preview.data?.service ?? 'Reservar'

  if (receipt) {
    const { booking, preview: terms } = receipt
    return (
      <div className="animate-in mx-auto max-w-xl">
        <Card>
          <div className="flex items-start gap-3">
            <CalendarCheck size={32} weight="duotone" className="shrink-0 text-accent" aria-hidden="true" />
            <div className="min-w-0">
              <h1 className="text-2xl">
                {booking.status === 'CANCELLED' ? 'Reserva cancelada' : booking.status === 'RESERVED' ? 'Reserva recibida' : 'Reserva confirmada'}
              </h1>
              <p className="mt-1 text-muted">
                Código <span className="font-mono font-semibold text-ink">{booking.code}</span>
              </p>
            </div>
          </div>
          <dl className="mt-5 grid gap-2 text-sm">
            <div className="flex justify-between gap-4"><dt className="text-muted">Servicio</dt><dd className="text-right">{terms.service}</dd></div>
            <div className="flex justify-between gap-4"><dt className="text-muted">Negocio</dt><dd className="text-right">{terms.organization}</dd></div>
            <div className="flex justify-between gap-4"><dt className="text-muted">Sede</dt><dd className="text-right">{terms.location}</dd></div>
            <div className="flex justify-between gap-4">
              <dt className="text-muted">Fecha</dt>
              <dd className="text-right">{formatDate(terms.slot.startsAt)}, {formatSlotTime(terms.slot.startsAt)}</dd>
            </div>
            <div className="flex justify-between gap-4"><dt className="text-muted">Precio</dt><dd className="text-right tabular-nums">{formatMoney(terms.price, terms.currency)}</dd></div>
            <div className="flex justify-between gap-4"><dt className="text-muted">Estado</dt><dd><StatusBadge map={BOOKING_STATUS} value={booking.status} /></dd></div>
          </dl>
          {booking.status === 'RESERVED' && (
            <div className="mt-4">
              <Alert tone="info" title="El negocio confirmará tu reserva">
                Te avisaremos en tu bandeja cuando la confirme.
              </Alert>
            </div>
          )}
          <div className="mt-6 flex flex-wrap justify-end gap-2">
            {booking.status !== 'CANCELLED' && (
              <Button variant="ghost" onClick={() => setCancelling(true)}>
                Cancelar reserva
              </Button>
            )}
            <LinkButton variant="secondary" to="/mis-reservas">Mis reservas</LinkButton>
            <LinkButton to="/">Volver al catálogo</LinkButton>
          </div>
        </Card>
        {cancelling && (
          <ClientCancelModal
            bookingId={booking.id}
            code={booking.code}
            onClose={() => setCancelling(false)}
            onCancelled={(cancelled) => {
              setCancelling(false)
              if (user) updateBookingStatus(user.id, cancelled.id, cancelled.status)
              setReceipt({ booking: { ...booking, status: cancelled.status }, preview: terms })
              toast.success(cancelled.lateCancellation ? 'Reserva cancelada con penalidad por cancelación tardía.' : 'Reserva cancelada.')
            }}
          />
        )}
      </div>
    )
  }

  return (
    <div className="animate-in">
      <Link to="/" className="mb-3 inline-flex items-center gap-1 text-sm text-muted hover:text-ink">
        <CaretLeft size={16} aria-hidden="true" /> Catálogo
      </Link>
      <PageHeader
        title={title}
        description={card ? `${card.provider.name} · ${card.location.name}, ${card.location.city}` : 'Elige el día y el horario.'}
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
        <Card>
          <Input
            label="Día"
            type="date"
            min={today}
            value={date}
            onChange={(e) => {
              setDate(e.target.value)
              setSlot(null)
              setNotice(null)
            }}
            wrapperClassName="max-w-xs"
          />
          <h2 className="mt-6 mb-3 text-lg">Horarios disponibles</h2>
          {!date ? null : slots.isPending ? (
            <Skeleton className="h-24 w-full" />
          ) : slots.isError ? (
            <ErrorState error={slots.error} onRetry={() => slots.refetch()} />
          ) : slots.data.length === 0 ? (
            <EmptyState title="No hay horarios este día" description="Prueba con otra fecha. Las reglas del negocio fijan con cuánta anticipación se puede reservar." />
          ) : (
            <ul className="grid grid-cols-3 gap-2 sm:grid-cols-4 md:grid-cols-6" aria-label="Horarios">
              {slots.data.map((s) => {
                const selected = slot?.startsAt === s.startsAt
                return (
                  <li key={s.startsAt}>
                    <button
                      type="button"
                      aria-pressed={selected}
                      onClick={() => {
                        setSlot(s)
                        setNotice(null)
                      }}
                      className={`min-h-11 w-full rounded-md border text-sm font-medium tabular-nums transition-colors ${
                        selected ? 'border-accent bg-accent text-white' : 'border-line-strong bg-surface hover:bg-sunken'
                      }`}
                    >
                      {formatSlotTime(s.startsAt)}
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </Card>

        <Card className="h-fit lg:sticky lg:top-24">
          <h2 className="mb-3 text-lg">Resumen</h2>
          {!slot ? (
            <p className="text-sm text-muted">Elige un horario para ver el precio y las condiciones.</p>
          ) : preview.isPending ? (
            <Skeleton className="h-40 w-full" />
          ) : preview.isError ? (
            <ErrorState error={preview.error} onRetry={() => preview.refetch()} />
          ) : (
            <div className="flex flex-col gap-4">
              <dl className="grid gap-2 text-sm">
                <div className="flex items-center gap-2">
                  <dt className="sr-only">Sede</dt>
                  <MapPin size={16} className="text-muted" aria-hidden="true" />
                  <dd>{preview.data.organization} · {preview.data.location}</dd>
                </div>
                <div className="flex items-center gap-2">
                  <dt className="sr-only">Horario</dt>
                  <Clock size={16} className="text-muted" aria-hidden="true" />
                  <dd>
                    {formatDate(preview.data.slot.startsAt)}, {formatSlotTime(preview.data.slot.startsAt)} – {formatSlotTime(preview.data.slot.endsAt)}
                    {card && <span className="text-muted"> ({formatDuration(card.durationMinutes)})</span>}
                  </dd>
                </div>
                <div className="flex justify-between gap-4 border-t border-line pt-3">
                  <dt className="text-muted">Precio</dt>
                  <dd className="font-display text-lg font-semibold tabular-nums">{formatMoney(preview.data.price, preview.data.currency)}</dd>
                </div>
                <div className="flex justify-between gap-4">
                  <dt className="text-muted">Cancelación</dt>
                  <dd className="text-right">{humanWindow(preview.data.freeCancellationWindowMinutes)}</dd>
                </div>
                <div className="flex justify-between gap-4">
                  <dt className="text-muted">Confirmación</dt>
                  <dd className="text-right">{preview.data.manualConfirmation ? 'La confirma el negocio' : 'Inmediata'}</dd>
                </div>
              </dl>
              {notice && <Alert tone="warn" title={notice} />}
              <Button size="lg" loading={book.isPending} onClick={() => book.mutate(preview.data)}>
                Confirmar reserva
              </Button>
              <p className="text-xs text-muted">Al confirmar aceptas el precio y las reglas de cancelación mostradas.</p>
            </div>
          )}
          {!slot && notice && <div className="mt-3"><Alert tone="warn" title={notice} /></div>}
        </Card>
      </div>
    </div>
  )
}
