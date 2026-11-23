import type { BookingStatus, ClientStatus, DayOfWeek, OrganizationStatus, ResourceCondition, ServiceStatus } from '@/api/types'

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

export const BOOKING_STATUS: Record<BookingStatus, { label: string; tone: Tone }> = {
  RESERVED: { label: 'Reservada', tone: 'warn' },
  CONFIRMED: { label: 'Confirmada', tone: 'ok' },
  CANCELLED: { label: 'Cancelada', tone: 'danger' },
  COMPLETED: { label: 'Completada', tone: 'info' },
  NO_SHOW: { label: 'No asistió', tone: 'neutral' },
}

const moneyFormats = new Map<string, Intl.NumberFormat>()
/** Amount in any ISO currency; the booking APIs group totals by currency. */
export function formatMoney(value: number, currency = 'COP'): string {
  let format = moneyFormats.get(currency)
  if (!format) {
    try {
      format = new Intl.NumberFormat('es-CO', { style: 'currency', currency, maximumFractionDigits: currency === 'COP' ? 0 : 2 })
    } catch {
      format = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 })
    }
    moneyFormats.set(currency, format)
  }
  return format.format(value)
}

/** null → "—", 12.5 → "12,5 %"; the reports send null when there is no base to compare. */
export function formatPercent(value: number | null | undefined, signed = false): string {
  if (value === null || value === undefined) return '—'
  const text = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 }).format(value)
  return `${signed && value > 0 ? '+' : ''}${text} %`
}

const timeOnly = new Intl.DateTimeFormat('es-CO', { timeStyle: 'short' })
/** Time of an OffsetDateTime as the wall clock of the location, whatever the browser's zone. */
export function formatSlotTime(offsetDateTime: string): string {
  const match = /T(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?[+-]\d{2}:\d{2}$/.exec(offsetDateTime)
  if (!match) return timeOnly.format(new Date(offsetDateTime))
  const date = new Date(2000, 0, 1, Number(match[1]), Number(match[2]))
  return timeOnly.format(date)
}

/** "2026-11-23" in the browser's local calendar. */
export function isoDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** A local date ("2026-11-23") to the instant at the start of that day, for the from/to filters. */
export const startOfDayInstant = (day: string) => new Date(`${day}T00:00:00`).toISOString()
/** The exclusive end of a local date range: start of the next day. */
export function endOfDayInstant(day: string): string {
  const date = new Date(`${day}T00:00:00`)
  date.setDate(date.getDate() + 1)
  return date.toISOString()
}
