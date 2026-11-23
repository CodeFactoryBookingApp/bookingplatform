import type { ClientStatus, DayOfWeek, OrganizationStatus, ResourceCondition, ServiceStatus } from '@/api/types'

const cop = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })
export const formatPrice = (value: number) => cop.format(value)

const dateTime = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' })
const dateOnly = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium' })
export const formatDateTime = (iso: string) => dateTime.format(new Date(iso))
export const formatDate = (iso: string) => dateOnly.format(new Date(iso.length === 10 ? `${iso}T00:00:00` : iso))

export function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes} min`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return m ? `${h} h ${m} min` : `${h} h`
}

export const DAYS: { value: DayOfWeek; label: string; short: string }[] = [
  { value: 'MONDAY', label: 'Lunes', short: 'Lun' },
  { value: 'TUESDAY', label: 'Martes', short: 'Mar' },
  { value: 'WEDNESDAY', label: 'Miércoles', short: 'Mié' },
  { value: 'THURSDAY', label: 'Jueves', short: 'Jue' },
  { value: 'FRIDAY', label: 'Viernes', short: 'Vie' },
  { value: 'SATURDAY', label: 'Sábado', short: 'Sáb' },
  { value: 'SUNDAY', label: 'Domingo', short: 'Dom' },
]
export const dayLabel = (day: DayOfWeek) => DAYS.find((d) => d.value === day)?.label ?? day

export type Tone = 'ok' | 'warn' | 'danger' | 'info' | 'neutral'

export const ORGANIZATION_STATUS: Record<OrganizationStatus, { label: string; tone: Tone }> = {
  PENDING_APPROVAL: { label: 'Pendiente de aprobación', tone: 'warn' },
  ACTIVE: { label: 'Activo', tone: 'ok' },
  SUSPENDED: { label: 'Suspendido', tone: 'danger' },
  REJECTED: { label: 'Rechazado', tone: 'danger' },
}
export const CLIENT_STATUS: Record<ClientStatus, { label: string; tone: Tone }> = {
  PENDING_VERIFICATION: { label: 'Sin verificar', tone: 'warn' },
  ACTIVE: { label: 'Activo', tone: 'ok' },
  SUSPENDED: { label: 'Suspendido', tone: 'danger' },
}
export const SERVICE_STATUS: Record<ServiceStatus, { label: string; tone: Tone }> = {
  ACTIVE: { label: 'Activo', tone: 'ok' },
  INACTIVE: { label: 'Inactivo', tone: 'neutral' },
}
export const RESOURCE_CONDITION: Record<ResourceCondition, { label: string; tone: Tone }> = {
  AVAILABLE: { label: 'Disponible', tone: 'ok' },
  IN_MAINTENANCE: { label: 'En mantenimiento', tone: 'warn' },
  RETIRED: { label: 'Retirado', tone: 'neutral' },
}

export const CHANNELS = { EMAIL: 'Correo', SMS: 'SMS', WHATSAPP: 'WhatsApp' } as const

/** Local datetime-local value ("2026-11-03T08:00") to the Instant the API expects. */
export const toInstant = (local: string) => new Date(local).toISOString()
