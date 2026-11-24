import { useState } from 'react'
import { ApiError } from '@/api/client'
import { clientBookingApi } from '@/api/endpoints'
import type { CancellationReceipt } from '@/api/types'
import { Alert } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Textarea } from '@/components/ui/Form'
import { Modal } from '@/components/ui/Modal'
import { errorMessage } from '@/lib/errors'
import { CANCEL_REASON_MAX } from '@/lib/schemas'

interface Props {
  bookingId: string
  code: string
  onClose: () => void
  onCancelled: (receipt: CancellationReceipt) => void
}

function humanWindow(minutes: number): string {
  if (minutes % 1440 === 0) return `${minutes / 1440} ${minutes === 1440 ? 'día' : 'días'}`
  if (minutes % 60 === 0) return `${minutes / 60} ${minutes === 60 ? 'hora' : 'horas'}`
  return `${minutes} minutos`
}

/**
 * HU-013. The first request goes without `confirm`; the backend answers CONFIRMATION_REQUIRED saying
 * whether the late-cancellation penalty applies, and the client confirms knowing that.
 */
export function ClientCancelModal({ bookingId, code, onClose, onCancelled }: Props) {
  const [reason, setReason] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [terms, setTerms] = useState<{ penalty: boolean; window: number } | null>(null)

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setPending(true)
    setError(null)
    try {
      const receipt = await clientBookingApi.cancel(bookingId, {
        confirm: terms !== null,
        reason: reason.trim() || undefined,
      })
      onCancelled(receipt)
    } catch (e) {
      if (e instanceof ApiError && e.errorCode === 'CONFIRMATION_REQUIRED') {
        setTerms({
          penalty: e.details.penaltyApplies === 'true',
          window: Number(e.details.freeCancellationWindowMinutes) || 0,
        })
      } else {
        setError(errorMessage(e))
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <Modal open onClose={onClose} title={`Cancelar la reserva ${code}`} description="El horario queda libre para otras personas.">
      <form noValidate onSubmit={submit} className="flex flex-col gap-4">
        {terms &&
          (terms.penalty ? (
            <Alert tone="warn" title="Se aplica penalidad por cancelación tardía">
              Ya pasó el plazo de cancelación gratuita ({humanWindow(terms.window)} antes de la cita). Si cancelas ahora,
              queda registrada como cancelación tardía.
            </Alert>
          ) : (
            <Alert tone="ok" title="Cancelación sin penalidad">
              Estás dentro del plazo de cancelación gratuita ({humanWindow(terms.window)} antes de la cita).
            </Alert>
          ))}
        <Textarea
          label="Motivo"
          hint={`Opcional. Máximo ${CANCEL_REASON_MAX} caracteres.`}
          maxLength={CANCEL_REASON_MAX}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
        {error && <Alert tone="danger" title={error} />}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Volver
          </Button>
          <Button type="submit" variant="danger" loading={pending}>
            {terms ? 'Sí, cancelar la reserva' : 'Cancelar reserva'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
