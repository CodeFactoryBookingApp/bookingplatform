import { randomUUID } from 'node:crypto'
import { API_URL, SUPABASE_SECRET_KEY, SUPABASE_URL } from './env'
import { SIGNUP_SUBJECT, waitForEmailToken } from './mailpit'

/** Satisfies PasswordPolicy: upper, lower, digit and a special character. */
export const PASSWORD = 'Xxxx123!x'

export interface Account {
  email: string
  password: string
}

export interface SeededService {
  name: string
  serviceId: string
  locationId: string
}

export interface SeededProvider extends Account {
  organizationId: string
  organizationName: string
}

const short = () => randomUUID().replaceAll('-', '').slice(0, 12)

/** Every test owns its users: no test depends on data another one created. */
export const uniqueEmail = (prefix: string) => `${prefix}-${randomUUID()}@e2e.test`
export const uniqueName = (prefix: string) => `${prefix} ${short()}`

interface RequestOptions {
  token?: string
  body?: unknown
  /** Exact status the call must answer; by default any 2xx. Anything else fails the seeding with the response body. */
  expect?: number
}

async function request<T>(method: string, url: string, headers: Record<string, string>, { body, expect }: RequestOptions): Promise<T> {
  const response = await fetch(url, {
    method,
    headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  if (expect === undefined ? !response.ok : response.status !== expect) {
    throw new Error(`${method} ${url} answered ${response.status} (expected ${expect ?? '2xx'}): ${text}`)
  }
  return (text ? JSON.parse(text) : undefined) as T
}

/** Call to the backend API (`/api/v1` is added). */
export function api<T = void>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const auth: Record<string, string> = options.token ? { Authorization: `Bearer ${options.token}` } : {}
  return request<T>(method, `${API_URL}/api/v1${path}`, auth, options)
}

/** Call to the GoTrue admin API with the service key. */
function gotrueAdmin<T>(method: string, path: string, options: RequestOptions): Promise<T> {
  return request<T>(method, `${SUPABASE_URL}/auth/v1/admin${path}`, { apikey: SUPABASE_SECRET_KEY, Authorization: `Bearer ${SUPABASE_SECRET_KEY}` }, options)
}

export async function login({ email, password }: Account): Promise<string> {
  const { accessToken } = await api<{ accessToken: string }>('POST', '/auth/login', { body: { email, password } })
  return accessToken
}

/** An administrator exists only in GoTrue (app_metadata.role); there is no sign-up for it. */
export async function createAdmin(): Promise<Account> {
  const account = { email: uniqueEmail('admin'), password: PASSWORD }
  await gotrueAdmin('POST', '/users', {
   
    body: { ...account, email_confirm: true, app_metadata: { role: 'ADMIN' } },
  })
  return account
}

/** Opens the link of the "Confirm Your Email" message the way the verification page does. */
export async function verifyEmail(email: string): Promise<void> {
  const tokenHash = await waitForEmailToken(email, SIGNUP_SUBJECT)
  await api('POST', '/registrations/email-verifications', { body: { tokenHash } })
}

export async function createVerifiedClient(): Promise<Account> {
  const account = { email: uniqueEmail('client'), password: PASSWORD }
  await api('POST', '/registrations', {
   
    body: {
      fullName: 'Cliente E2E',
      document: short(),
      birthDate: '1990-01-01',
      email: account.email,
      phone: '3001234567',
      city: 'Bogotá',
      notificationChannel: 'EMAIL',
      password: account.password,
    },
  })
  await verifyEmail(account.email)
  return account
}

/** Registers a provider through the API and confirms its email; its business stays PENDING_APPROVAL. */
export async function createPendingProvider(): Promise<SeededProvider> {
  const [categories, cities] = await Promise.all([
    api<{ id: string }[]>('GET', '/organization-categories'),
    api<{ id: string }[]>('GET', '/cities'),
  ])
  const account = { email: uniqueEmail('provider'), password: PASSWORD }
  const organizationName = uniqueName('Negocio E2E')
  const { organizationId } = await api<{ organizationId: string }>('POST', '/provider-registrations', {
   
    body: {
      ...account,
      name: organizationName,
      categoryId: categories[0].id,
      identificationType: 'NIT',
      identificationNumber: short(),
      contactPhone: '3001112233',
      locations: [{ name: 'Sede Central', address: 'Calle 1 # 2-3', cityId: cities[0].id }],
      bookingNoticeMinutesMin: 0,
      bookingNoticeMinutesMax: 43200,
      freeCancellationWindowMinutes: 60,
    },
  })
  await verifyEmail(account.email)
  return { ...account, organizationId, organizationName }
}

export async function approveOrganization(organizationId: string): Promise<void> {
  const admin = await createAdmin()
  const token = await login(admin)
  await api('POST', `/admin/organizations/${organizationId}/approval`, { token, body: { reason: 'Approved by the e2e suite' } })
}

/** Id of the business the provider registered; the UI registration does not hand it back. */
export async function organizationOf(provider: Account): Promise<string> {
  const token = await login(provider)
  const [organization] = await api<{ id: string }[]>('GET', '/organizations', { token })
  return organization.id
}

/**
 * An ACTIVE service with a weekly agenda (08:00-18:00 every day, one-hour slots) so a client can book
 * any day: resource type, resource, service, activation and agenda, the same order the provider UI needs.
 */
export async function createService(provider: Account, organizationId: string): Promise<SeededService> {
  const token = await login(provider)
  const base = `/organizations/${organizationId}`
  const { locations } = await api<{ locations: { id: string }[] }>('GET', base, { token })
  const locationId = locations[0].id
  const name = uniqueName('Masaje E2E')

  const type = await api<{ id: string }>('POST', `${base}/resource-types`, { token, body: { name: uniqueName('Sala'), description: 'Seeded by e2e' } })
  await api('POST', `${base}/resources`, { token, body: { resourceTypeId: type.id, locationId, name: uniqueName('Sala 1'), capacity: 1 } })
  const service = await api<{ id: string }>('POST', `${base}/services`, {
    token,
   
    body: {
      details: { name, description: 'Seeded by e2e', durationMinutes: 60, price: 80000 },
      locationIds: [locationId],
      requirements: [{ resourceTypeId: type.id, quantity: 1 }],
    },
  })
  await api('POST', `${base}/services/${service.id}/activation`, { token })
  const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
  await api('POST', `${base}/agendas`, {
    token,
   
    body: {
      name: uniqueName('Agenda'),
      locationId,
      slotMinutes: 60,
      validFrom: new Date().toISOString().slice(0, 10),
      serviceIds: [service.id],
      blocks: days.map((dayOfWeek) => ({ dayOfWeek, startTime: '08:00', endTime: '18:00' })),
    },
  })
  return { name, serviceId: service.id, locationId }
}

/** Approved provider whose service is bookable: the starting point of the client scenarios. */
export async function createApprovedProviderWithService(): Promise<SeededProvider & { service: SeededService }> {
  const provider = await createPendingProvider()
  await approveOrganization(provider.organizationId)
  const service = await createService(provider, provider.organizationId)
  return { ...provider, service }
}
