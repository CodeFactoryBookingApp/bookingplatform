import { api, download } from './client'
import type {
  AdminListQuery,
  Agenda,
  AgendaException,
  AgendaOccupancy,
  AuditEvent,
  DayAvailability,
  AuditQuery,
  Block,
  BookingPage,
  BookingPreview,
  BookingQuery,
  BookingReceipt,
  BookingRow,
  CancellationReceipt,
  CreateBookingBody,
  DemandReport,
  LeadTimes,
  ReminderPreferences,
  Role,
  TimeSlot,
  CatalogQuery,
  CatalogResponse,
  Category,
  City,
  ClientRow,
  ConfirmEmailResponse,
  CreateAgendaBody,
  CreateServiceBody,
  DayOfWeek,
  InboxEmail,
  LoginResponse,
  Me,
  OfferedService,
  Organization,
  Page,
  PolicyBody,
  ProviderRegistrationResponse,
  ProviderRow,
  RegisterClientBody,
  RegisterClientResponse,
  RegisterProviderBody,
  Resource,
  ResourceType,
  ServiceDetails,
} from './types'

const org = (id: string) => `/organizations/${id}`

export const authApi = {
  login: (email: string, password: string) =>
    api<LoginResponse>('/auth/login', { method: 'POST', body: { email, password }, anonymous: true }),
  logout: () => api<void>('/auth/logout', { method: 'POST' }),
  me: () => api<Me>('/auth/me'),
  requestRecovery: (email: string) =>
    api<void>('/auth/password-recovery-requests', { method: 'POST', body: { email }, anonymous: true }),
  resetPassword: (tokenHash: string, newPassword: string) =>
    api<void>('/auth/password-resets', { method: 'POST', body: { tokenHash, newPassword }, anonymous: true }),
}

export const registrationApi = {
  client: (body: RegisterClientBody) =>
    api<RegisterClientResponse>('/registrations', { method: 'POST', body, anonymous: true }),
  resend: (email: string) =>
    api<void>('/registrations/verification-resends', { method: 'POST', body: { email }, anonymous: true }),
  confirm: (tokenHash: string) =>
    api<ConfirmEmailResponse>('/registrations/email-verifications', {
      method: 'POST',
      body: { tokenHash },
      anonymous: true,
    }),
  provider: (body: RegisterProviderBody) =>
    api<ProviderRegistrationResponse>('/provider-registrations', { method: 'POST', body, anonymous: true }),
}

export const referenceApi = {
  categories: () => api<Category[]>('/organization-categories'),
  cities: () => api<City[]>('/cities'),
}

export const catalogApi = {
  search: (query: CatalogQuery, signal?: AbortSignal) =>
    api<CatalogResponse>('/catalog/services', { query: { ...query }, signal }),
}

export const inboxApi = {
  emails: () => api<InboxEmail[]>('/notifications/emails'),
}

/** Booking flow of a CLIENT (HU-012, HU-013). SecurityConfig limits /booking/** and /bookings/** to CLIENT. */
export const clientBookingApi = {
  availability: (serviceId: string, locationId: string, date: string) =>
    api<TimeSlot[]>('/booking/availability', { query: { serviceId, locationId, date } }),
  preview: (serviceId: string, locationId: string, startsAt: string) =>
    api<BookingPreview>('/booking/preview', { query: { serviceId, locationId, startsAt } }),
  create: (body: CreateBookingBody) => api<BookingReceipt>('/bookings', { method: 'POST', body }),
  /** Without `confirm` the backend answers 409 CONFIRMATION_REQUIRED with the penalty details. */
  cancel: (bookingId: string, body: { confirm?: boolean; reason?: string }) =>
    api<CancellationReceipt>(`/bookings/${bookingId}/cancel`, { method: 'POST', body }),
}

/** Personal reminder preferences of any signed-in user (HU-015). */
export const preferencesApi = {
  get: () => api<ReminderPreferences>('/notifications/booking-preferences'),
  save: (body: ReminderPreferences) =>
    api<ReminderPreferences>('/notifications/booking-preferences', { method: 'PUT', body }),
}

export const organizationApi = {
  mine: () => api<Organization[]>('/organizations'),
  one: (id: string) => api<Organization>(org(id)),
  changePolicy: (id: string, body: PolicyBody) =>
    api<Organization>(`${org(id)}/operation-policy`, { method: 'PUT', body }),

  resourceTypes: (id: string) => api<ResourceType[]>(`${org(id)}/resource-types`),
  createResourceType: (id: string, body: { name: string; description?: string }) =>
    api<ResourceType>(`${org(id)}/resource-types`, { method: 'POST', body }),

  resources: (id: string) => api<Resource[]>(`${org(id)}/resources`),
  createResource: (
    id: string,
    body: { resourceTypeId: string; locationId: string; name: string; capacity: number },
  ) => api<Resource>(`${org(id)}/resources`, { method: 'POST', body }),
  scheduleMaintenance: (id: string, resourceId: string, body: { startsAt: string; endsAt: string; reason?: string }) =>
    api<Resource>(`${org(id)}/resources/${resourceId}/maintenances`, { method: 'POST', body }),
  retireResource: (id: string, resourceId: string) =>
    api<Resource>(`${org(id)}/resources/${resourceId}/retirement`, { method: 'POST' }),

  services: (id: string) => api<OfferedService[]>(`${org(id)}/services`),
  createService: (id: string, body: CreateServiceBody) =>
    api<OfferedService>(`${org(id)}/services`, { method: 'POST', body }),
  updateService: (id: string, serviceId: string, body: ServiceDetails) =>
    api<OfferedService>(`${org(id)}/services/${serviceId}`, { method: 'PUT', body }),
  activateService: (id: string, serviceId: string) =>
    api<OfferedService>(`${org(id)}/services/${serviceId}/activation`, { method: 'POST' }),
  deactivateService: (id: string, serviceId: string) =>
    api<OfferedService>(`${org(id)}/services/${serviceId}/deactivation`, { method: 'POST' }),
  deleteService: (id: string, serviceId: string) =>
    api<void>(`${org(id)}/services/${serviceId}`, { method: 'DELETE' }),

  agendas: (id: string) => api<Agenda[]>(`${org(id)}/agendas`),
  createAgenda: (id: string, body: CreateAgendaBody) =>
    api<Agenda>(`${org(id)}/agendas`, { method: 'POST', body }),
  replaceBlocks: (id: string, agendaId: string, blocks: Block[]) =>
    api<Agenda>(`${org(id)}/agendas/${agendaId}/blocks`, { method: 'PUT', body: { blocks } }),
  changeGranularity: (id: string, agendaId: string, slotMinutes: number) =>
    api<Agenda>(`${org(id)}/agendas/${agendaId}/granularity`, { method: 'PUT', body: { slotMinutes } }),
  copyDay: (id: string, agendaId: string, fromDay: DayOfWeek, toDays: DayOfWeek[]) =>
    api<Agenda>(`${org(id)}/agendas/${agendaId}/day-copies`, { method: 'POST', body: { fromDay, toDays } }),
  agenda: (id: string, agendaId: string) => api<Agenda>(`${org(id)}/agendas/${agendaId}`),

  // Exceptions of an agenda (HU-007): one per date, 409 OVERLAPPING_PERIOD otherwise
  agendaExceptions: (id: string, agendaId: string) =>
    api<AgendaException[]>(`${org(id)}/agendas/${agendaId}/exceptions`),
  closeAgendaDate: (id: string, agendaId: string, body: { date: string; reason?: string }) =>
    api<AgendaException>(`${org(id)}/agendas/${agendaId}/exceptions/closures`, { method: 'POST', body }),
  specialHours: (
    id: string,
    agendaId: string,
    body: { date: string; startTime: string; endTime: string; reason?: string },
  ) => api<AgendaException>(`${org(id)}/agendas/${agendaId}/exceptions/special-hours`, { method: 'POST', body }),
  deleteAgendaException: (id: string, agendaId: string, exceptionId: string) =>
    api<void>(`${org(id)}/agendas/${agendaId}/exceptions/${exceptionId}`, { method: 'DELETE' }),

  // Free slots of an agenda (HU-008, HU-011): from and to included, at most 62 days apart
  agendaAvailability: (id: string, agendaId: string, from: string, to: string) =>
    api<DayAvailability[]>(`${org(id)}/agendas/${agendaId}/availability`, { query: { from, to } }),

  // Occupancy per agenda (HU-019): from and to included, OWNER or MANAGER
  occupancy: (id: string, from: string, to: string) =>
    api<AgendaOccupancy[]>(`${org(id)}/reports/occupancy`, { query: { from, to } }),

  // Bookings of the organization (HU-018) and their lifecycle (HU-012, HU-013)
  bookings: (id: string, query: BookingQuery) => api<BookingPage>(`${org(id)}/bookings`, { query: { ...query } }),
  bookingByCode: (id: string, code: string) =>
    api<BookingRow>(`${org(id)}/bookings/by-code/${encodeURIComponent(code)}`),
  exportBookings: (id: string, query: BookingQuery) =>
    download(`${org(id)}/bookings/export`, { ...query, page: undefined, size: undefined }, 'reservas.csv'),
  confirmBooking: (id: string, bookingId: string) =>
    api<BookingReceipt>(`${org(id)}/bookings/${bookingId}/confirm`, { method: 'POST' }),
  cancelBooking: (id: string, bookingId: string, reason: string) =>
    api<CancellationReceipt>(`${org(id)}/bookings/${bookingId}/cancel`, { method: 'POST', body: { reason } }),

  // Service demand report (HU-020); from and to are dates in the organization's zone, to excluded
  demand: (id: string, from: string, to: string) =>
    api<DemandReport>(`${org(id)}/reports/service-demand`, { query: { from, to } }),
  exportDemand: (id: string, from: string, to: string) =>
    download(`${org(id)}/reports/service-demand/export`, { from, to }, 'demanda-servicios.csv'),

  // Reminder lead times (HU-015)
  reminders: (id: string) => api<LeadTimes>(`${org(id)}/booking-reminders`),
  saveReminders: (id: string, leadMinutes: number[]) =>
    api<LeadTimes>(`${org(id)}/booking-reminders`, { method: 'PUT', body: { leadMinutes } }),

  // Audit of the organization (HU-023)
  audit: (id: string, query: AuditQuery) => api<Page<AuditEvent>>(`${org(id)}/audit-events`, { query: { ...query } }),
  exportAudit: (id: string, query: AuditQuery) =>
    download(`${org(id)}/audit-events/export`, { ...query, page: undefined, size: undefined }, 'auditoria.csv'),
}

export const adminApi = {
  providers: (query: AdminListQuery) => api<Page<ProviderRow>>('/admin/organizations', { query: { ...query } }),
  exportProviders: (query: AdminListQuery) =>
    download('/admin/organizations/export', { ...query, page: undefined, size: undefined }, 'proveedores.csv'),
  providerAction: (
    id: string,
    action: 'approval' | 'rejection' | 'reactivation' | 'suspension',
    body: { reason: string; confirm?: boolean },
  ) => api<Organization>(`/admin/organizations/${id}/${action}`, { method: 'POST', body }),

  clients: (query: AdminListQuery) => api<Page<ClientRow>>('/admin/clients', { query: { ...query } }),
  exportClients: (query: AdminListQuery) =>
    download('/admin/clients/export', { ...query, page: undefined, size: undefined }, 'clientes.csv'),
  clientAction: (id: string, action: 'suspension' | 'reactivation', reason: string) =>
    api<{ id: string; status: string }>(`/admin/clients/${id}/${action}`, { method: 'POST', body: { reason } }),

  /** HU-022: also invalidates the user's earlier sessions. */
  changeRole: (userId: string, role: Role) =>
    api<{ id: string; role: Role }>(`/admin/users/${userId}/role`, { method: 'PATCH', body: { role } }),

  audit: (query: AuditQuery) => api<Page<AuditEvent>>('/admin/audit-events', { query: { ...query } }),
  exportAudit: (query: AuditQuery) =>
    download('/admin/audit-events/export', { ...query, page: undefined, size: undefined }, 'auditoria.csv'),
}
