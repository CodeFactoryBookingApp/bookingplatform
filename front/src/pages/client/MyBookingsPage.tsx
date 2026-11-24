import { useState } from 'react'
import { toast } from 'sonner'
import { useAuth } from '@/auth/AuthContext'
import { ClientCancelModal } from '@/components/booking/ClientCancelModal'
import { StatusBadge } from '@/components/ui/Badge'
import { Alert, EmptyState, PageHeader } from '@/components/ui/Blocks'
import { Button, LinkButton } from '@/components/ui/Button'
import { BOOKING_STATUS, formatDate, formatMoney, formatSlotTime } from '@/lib/format'
import { forgetBooking, readBookings, updateBookingStatus, type SavedBooking } from '@/lib/myBookings'

/** Bookings made from this browser (see lib/myBookings: the backend has no client listing yet). */
export default function MyBookingsPage() {
  const { user } = useAuth()
  const userId = user?.id ?? ''
  const [bookings, setBookings] = useState<SavedBooking[]>(() => readBookings(userId))
  const [target, setTarget] = useState<SavedBooking | null>(null)
  const reload = () => setBookings(readBookings(userId))

  const now = Date.now()
  const upcoming = bookings.filter((b) => new Date(b.startsAt).getTime() > now)
  const past = bookings.filter((b) => new Date(b.startsAt).getTime() <= now)

  const list = (items: SavedBooking[], allowCancel: boolean) => (
    <ul className="grid gap-3">
      {items.map((b) => {
        const active = b.status === 'RESERVED' || b.status === 'CONFIRMED'
        return (
          <li key={b.id} className="flex flex-wrap items-start justify-between gap-4 rounded-lg border border-line bg-surface p-4">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <p className="font-medium">{b.service}</p>
                <StatusBadge map={BOOKING_STATUS} value={b.status} />
              </div>
              <p className="text-sm text-muted">
                {b.organization} · {b.location}
              </p>
              <p className="mt-1 text-sm">
                {formatDate(b.startsAt)}, {formatSlotTime(b.startsAt)} · {formatMoney(b.price, b.currency)}
              </p>
              <p className="mt-1 text-xs text-muted">
                Código <span className="font-mono">{b.code}</span>
              </p>
            </div>
            <div className="flex gap-2">
              {allowCancel && active && (
                <Button size="sm" variant="secondary" onClick={() => setTarget(b)}>
                  Cancelar
                </Button>
              )}
              {!active && (
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => {
                    forgetBooking(userId, b.id)
                    reload()
                  }}
                >
                  Quitar de la lista
                </Button>
              )}
            </div>
          </li>
        )
      })}
    </ul>
  )

  return (
    <div className="animate-in">
      <PageHeader title="Mis reservas" description="Las reservas que hiciste desde este navegador." actions={<LinkButton to="/">Reservar otro servicio</LinkButton>} />
      <div className="mb-6">
        <Alert tone="info" title="Esta lista vive en este navegador">
          Todavía no podemos consultar tus reservas desde el servidor, así que aquí solo aparecen las que hiciste en este
          dispositivo, y el estado puede no estar al día. El negocio te avisa de cualquier cambio en tu bandeja.
        </Alert>
      </div>
      {bookings.length === 0 ? (
        <EmptyState title="Aún no tienes reservas aquí" description="Busca un servicio en el catálogo y reserva un horario." action={<LinkButton to="/">Ir al catálogo</LinkButton>} />
      ) : (
        <div className="flex flex-col gap-8">
          {upcoming.length > 0 && (
            <section>
              <h2 className="mb-3 text-lg">Próximas</h2>
              {list(upcoming, true)}
            </section>
          )}
          {past.length > 0 && (
            <section>
              <h2 className="mb-3 text-lg">Anteriores</h2>
              {list(past, false)}
            </section>
          )}
        </div>
      )}
      {target && (
        <ClientCancelModal
          bookingId={target.id}
          code={target.code}
          onClose={() => setTarget(null)}
          onCancelled={(receipt) => {
            updateBookingStatus(userId, receipt.id, receipt.status)
            setTarget(null)
            reload()
            toast.success(receipt.lateCancellation ? 'Reserva cancelada con penalidad por cancelación tardía.' : 'Reserva cancelada.')
          }}
        />
      )}
    </div>
  )
}
