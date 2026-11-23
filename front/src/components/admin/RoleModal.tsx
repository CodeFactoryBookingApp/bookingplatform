import { useState } from 'react'
import { adminApi } from '@/api/endpoints'
import type { Role } from '@/api/types'
import { Alert } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Select } from '@/components/ui/Form'
import { Modal } from '@/components/ui/Modal'
import { errorMessage } from '@/lib/errors'

export const ROLE_LABELS: Record<Role, string> = {
  CLIENT: 'Cliente',
  PROVIDER: 'Proveedor',
  ADMIN: 'Administrador',
}

interface Props {
  userId: string
  name: string
  current?: Role
  onClose: () => void
  onChanged: (role: Role) => void
}

/** HU-022: changing the platform role closes the user's earlier sessions (ADR-0009). */
export function RoleModal({ userId, name, current, onClose, onChanged }: Props) {
  const [role, setRole] = useState<Role>(current === 'ADMIN' ? 'CLIENT' : 'PROVIDER')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setPending(true)
    setError(null)
    try {
      const result = await adminApi.changeRole(userId, role)
      onChanged(result.role)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setPending(false)
    }
  }

  return (
    <Modal open onClose={onClose} title={`Cambiar el rol de ${name}`} description="Define qué partes de la plataforma puede usar.">
      <form noValidate onSubmit={submit} className="flex flex-col gap-4">
        <Select label="Nuevo rol" value={role} onChange={(e) => setRole(e.target.value as Role)}>
          {(Object.keys(ROLE_LABELS) as Role[]).map((r) => (
            <option key={r} value={r}>
              {ROLE_LABELS[r]}
            </option>
          ))}
        </Select>
        <Alert tone="warn" title="Se cierran sus sesiones abiertas">
          Tendrá que iniciar sesión de nuevo para usar el rol nuevo.
          {role === 'ADMIN' && ' Un administrador tiene acceso a todos los proveedores y clientes.'}
        </Alert>
        {error && <Alert tone="danger" title={error} />}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" variant={role === 'ADMIN' ? 'danger' : 'primary'} loading={pending}>
            Cambiar rol
          </Button>
        </div>
      </form>
    </Modal>
  )
}
