import { useState } from 'react'
import { ApiError } from '@/api/client'
import { errorMessage } from '@/lib/errors'
import { Alert } from './Blocks'
import { Button } from './Button'
import { Textarea } from './Form'
import { Modal } from './Modal'

interface Props {
  open: boolean
  title: string
  description?: string
  confirmLabel: string
  danger?: boolean
  /** Runs the action; `confirm` is true on the second try after the backend asked for confirmation. */
  onSubmit: (reason: string, confirm: boolean) => Promise<void>
  onClose: () => void
}

/**
 * Every admin status change demands a reason (HU-003). A 409 CONFIRMATION_REQUIRED, which the
 * backend answers when a suspension affects future bookings, turns into an explicit second step.
 */
export function ReasonModal({ open, title, description, confirmLabel, danger, onSubmit, onClose }: Props) {
  const [reason, setReason] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [affected, setAffected] = useState<string | null>(null)

  const close = () => {
    setReason('')
    setError(null)
    setAffected(null)
    onClose()
  }

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!reason.trim()) return setError('Escribe el motivo.')
    setPending(true)
    setError(null)
    try {
      await onSubmit(reason.trim(), affected !== null)
      close()
    } catch (e) {
      if (e instanceof ApiError && e.errorCode === 'CONFIRMATION_REQUIRED') {
        setAffected(e.details.affectedBookings ?? '?')
      } else {
        setError(errorMessage(e))
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <Modal open={open} onClose={close} title={title} description={description}>
      <form noValidate onSubmit={submit} className="flex flex-col gap-4">
        {affected !== null && (
          <Alert tone="warn" title={`Hay ${affected} reservas futuras afectadas`}>
            Se conservan, pero el negocio dejará de recibir reservas nuevas. Si estás seguro, confirma de nuevo.
          </Alert>
        )}
        <Textarea
          label="Motivo"
          required
          autoFocus
          maxLength={500}
          hint="Queda guardado junto con el cambio. Máximo 500 caracteres."
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          disabled={affected !== null}
        />
        {error && <Alert tone="danger" title={error} />}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={close}>
            Cancelar
          </Button>
          <Button type="submit" variant={danger ? 'danger' : 'primary'} loading={pending}>
            {affected !== null ? 'Sí, confirmar' : confirmLabel}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
