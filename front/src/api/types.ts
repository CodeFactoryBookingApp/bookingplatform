// Mirrors the backend DTOs (see the back-end *Request / *Response records and OpenAPI at /v3/api-docs).

export type Role = 'CLIENT' | 'PROVIDER' | 'ADMIN'
export type NotificationChannel = 'EMAIL' | 'SMS' | 'WHATSAPP'
export type IdentificationType = 'NIT' | 'CC' | 'CE' | 'PASAPORTE'
export type OrganizationStatus = 'PENDING_APPROVAL' | 'ACTIVE' | 'SUSPENDED' | 'REJECTED'
export type ClientStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'SUSPENDED'
export type ServiceStatus = 'ACTIVE' | 'INACTIVE'
export type ResourceCondition = 'AVAILABLE' | 'IN_MAINTENANCE' | 'RETIRED'
export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}
export interface Me {
  id: string
  email: string
  role: Role | null
}

export interface Page<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface Category {
  id: string
  name: string
}
export interface City {
  id: string
  name: string
  region: string
}

// Catalog (public)
export interface ServiceCard {
  serviceId: string
  name: string
  description: string | null
  provider: { id: string; name: string; category: string }
  location: { id: string; name: string; city: string; region: string }
  durationMinutes: number
  price: number
  currency: string
  customerCapacity: number
  group: boolean
}
export interface CatalogResponse extends Page<ServiceCard> {
  message: string | null
}
export interface CatalogQuery {
  q?: string
  categoryId?: string
  cityId?: string
  providerId?: string
  minPrice?: string
  maxPrice?: string
  sort?: 'name' | 'price' | 'duration' | 'provider'
  direction?: 'asc' | 'desc'
  page?: number
  size?: number
}

// Registrations
export interface RegisterClientBody {
  fullName: string
  document: string
  birthDate: string
  email: string
  phone: string
  city: string
  notificationChannel: NotificationChannel
  password: string
}
export interface RegisterClientResponse {
  clientId: string
  email: string
  status: ClientStatus
  message: string
}
export interface ConfirmEmailResponse {
  clientId: string | null
  email: string
  status: ClientStatus | null
  message: string
}
export interface LocationBody {
  name: string
  address: string
  cityId: string
  timezone?: string
}
export interface RegisterProviderBody {
  email: string
  password: string
  name: string
  categoryId: string
  identificationType: IdentificationType
  identificationNumber: string
  contactEmail?: string
  contactPhone: string
  timezone?: string
  locations: LocationBody[]
  bookingNoticeMinutesMin: number
  bookingNoticeMinutesMax: number
  freeCancellationWindowMinutes: number
}
export interface ProviderRegistrationResponse {
  organizationId: string
  ownerUserId: string
  email: string
  status: OrganizationStatus
  message: string
}

// Organizations
export interface Organization {
  id: string
  name: string
  categoryId: string
  identificationType: IdentificationType
  identificationNumber: string
  contactEmail: string
  contactPhone: string
  timezone: string
  status: OrganizationStatus
  approvedAt: string | null
  locations: { id: string; name: string; address: string; cityId: string; timezone: string }[]
  operationPolicy: {
    version: number
    bookingNoticeMinutesMin: number
    bookingNoticeMinutesMax: number
    freeCancellationWindowMinutes: number
    manualConfirmation: boolean
  }
}
export interface PolicyBody {
  bookingNoticeMinutesMin: number
  bookingNoticeMinutesMax: number
  freeCancellationWindowMinutes: number
  manualConfirmation?: boolean
}

// Resources
export interface ResourceType {
  id: string
  name: string
  description: string | null
}
export interface Maintenance {
  id: string
  startsAt: string
  endsAt: string
  reason: string | null
}
export interface Resource {
  id: string
  resourceTypeId: string
  locationId: string
  name: string
  capacity: number
  condition: ResourceCondition
  maintenances: Maintenance[]
}

// Services
export interface ServiceDetails {
  name: string
  description?: string
  durationMinutes: number
  preparationMinutes?: number
  cleanupMinutes?: number
  price: number
  customerCapacity?: number
}
export interface CreateServiceBody {
  details: ServiceDetails
  locationIds: string[]
  requirements: { resourceTypeId: string; quantity?: number }[]
}
export interface OfferedService {
  id: string
  name: string
  description: string | null
  durationMinutes: number
  preparationMinutes: number
  cleanupMinutes: number
  price: number
  currency: string
  customerCapacity: number
  group: boolean
  status: ServiceStatus
  locationIds: string[]
  requirements: { resourceTypeId: string; quantity: number }[]
}

// Agendas
export interface Block {
  dayOfWeek: DayOfWeek
  startTime: string
  endTime: string
}
export interface Agenda {
  id: string
  name: string
  locationId: string
  resourceId: string | null
  slotMinutes: number
  validFrom: string
  validUntil: string | null
  serviceIds: string[]
  blocks: Block[]
}
export interface CreateAgendaBody {
  name: string
  locationId: string
  resourceId?: string
  slotMinutes: number
  validFrom: string
  validUntil?: string
  serviceIds: string[]
  blocks: Block[]
}

// Admin
export interface ProviderRow {
  id: string
  name: string
  identificationType: IdentificationType
  identificationNumber: string
  category: string
  contactEmail: string
  contactPhone: string
  cities: string | null
  status: OrganizationStatus
  registeredAt: string
  approvedAt: string | null
}
export interface ClientRow {
  id: string
  fullName: string
  document: string
  email: string
  phone: string
  city: string
  notificationChannel: NotificationChannel
  status: ClientStatus
  registeredAt: string
}
export interface AdminListQuery {
  status?: string
  categoryId?: string
  city?: string
  registeredFrom?: string
  registeredTo?: string
  sort?: string
  direction?: 'asc' | 'desc'
  page?: number
  size?: number
}

export interface InboxEmail {
  id: string
  subject: string
  body: string
  sentAt: string
}

// Bookings: creation and cancellation (HU-012, HU-013)
export type BookingStatus = 'RESERVED' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW'
export interface TimeSlot {
  /** OffsetDateTime in the location's zone, e.g. "2026-12-01T09:00:00-05:00" */
  startsAt: string
  endsAt: string
}
export interface BookingPreview {
  service: string
  organization: string
  location: string
  slot: TimeSlot
  price: number
  currency: string
  policyVersion: number
  freeCancellationWindowMinutes: number
  manualConfirmation: boolean
  termsVersion: string
}
export interface CreateBookingBody {
  serviceId: string
  locationId: string
  startsAt: string
  termsVersion: string
}
export interface BookingReceipt {
  id: string
  code: string
  status: BookingStatus
}
export interface CancellationReceipt extends BookingReceipt {
  lateCancellation: boolean
}

// Booking search (HU-018)
export interface BookingRow {
  id: string
  code: string
  clientId: string
  clientName: string
  serviceId: string
  serviceName: string
  locationId: string
  locationName: string
  startsAt: string
  endsAt: string
  attendees: number
  totalPrice: number
  currency: string
  status: BookingStatus
}
export interface BookingSummary {
  total: number
  byStatus: Partial<Record<BookingStatus, number>>
  amountByCurrency: Record<string, number>
  cancellationRatePercent: number | null
}
export interface BookingPage {
  items: BookingRow[]
  summary: BookingSummary
  page: number
  size: number
}
export interface BookingQuery {
  from?: string
  to?: string
  status?: BookingStatus
  serviceId?: string
  locationId?: string
  resourceId?: string
  clientId?: string
  code?: string
  page?: number
  size?: number
}

// Service demand report (HU-020)
export interface DemandMetrics {
  created: number
  completed: number
  cancelled: number
  noShow: number
  completionRatePercent: number | null
  cancellationRatePercent: number | null
  noShowRatePercent: number | null
  estimatedRevenueByCurrency: Record<string, number>
}
export interface DemandVariation {
  createdPercent: number | null
  completedPercent: number | null
  cancelledPercent: number | null
  noShowPercent: number | null
  completionRatePercent: number | null
  cancellationRatePercent: number | null
  noShowRatePercent: number | null
  estimatedRevenuePercent: Record<string, number | null>
}
export interface DemandService {
  id: string
  name: string
  current: DemandMetrics
  previous: DemandMetrics
  variation: DemandVariation
  highCancellation: boolean
}
export interface DemandBucket {
  /** ISO day of week, 1 = Monday … 7 = Sunday */
  dayOfWeek: number
  hour: number
  bookings: number
}
export interface DemandReport {
  period: { from: string; to: string }
  previousPeriod: { from: string; to: string }
  timezone: string
  services: DemandService[]
  demand: DemandBucket[]
  previousDemand: DemandBucket[]
}

// Reminders (HU-015)
export interface LeadTimes {
  leadMinutes: number[]
}
export interface ReminderPreferences {
  remindersEnabled: boolean
  /** "HH:mm" or "HH:mm:ss" */
  quietFrom: string
  quietUntil: string
  timezone: string | null
}

// Audit (HU-023)
export interface AuditEvent {
  id: string
  occurredAt: string
  actorId: string | null
  actorRole: string | null
  action: string
  resourceType: string
  resourceId: string
  organizationId: string | null
  result: string
  relatedResourceId: string | null
}
export interface AuditQuery {
  from?: string
  to?: string
  actorId?: string
  action?: string
  page?: number
  size?: number
}

// Agenda exceptions, availability calendar and occupancy (HU-007, HU-008, HU-011, HU-019)
export type AgendaExceptionType = 'CLOSED' | 'SPECIAL_HOURS'
export interface AgendaException {
  id: string
  agendaId: string
  date: string
  type: AgendaExceptionType
  /** "HH:mm:ss"; only for SPECIAL_HOURS */
  specialStart: string | null
  specialEnd: string | null
  reason: string | null
}
export interface DayAvailability {
  date: string
  /** Instants (UTC) */
  slots: { startsAt: string; endsAt: string }[]
}
export interface AgendaOccupancy {
  agendaId: string
  agendaName: string
  from: string
  to: string
  totalSlots: number
  occupiedSlots: number
  /** 0..1 */
  occupancyRate: number
}
