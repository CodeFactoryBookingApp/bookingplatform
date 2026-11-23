import { api, download } from './client'
import type {
  AdminListQuery,
  Agenda,
  Block,
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
}
