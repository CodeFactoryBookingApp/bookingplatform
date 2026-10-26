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
  }
}
export interface PolicyBody {
  bookingNoticeMinutesMin: number
  bookingNoticeMinutesMax: number
  freeCancellationWindowMinutes: number
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
