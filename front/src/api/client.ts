const BASE = '/api/v1'
// Render's free plan sleeps after ~15 min; the first call was measured at ~100 s.
const TIMEOUT_MS = 150_000
const STORAGE_KEY = 'bp.session'

export interface StoredSession {
  accessToken: string
  expiresAt: number
}

/** The RFC 9457 body the backend returns for every error (GlobalExceptionHandler). */
export class ApiError extends Error {
  readonly status: number
  readonly errorCode: string
  readonly details: Record<string, string>
  readonly traceId?: string

  constructor(status: number, errorCode: string, detail: string, details: Record<string, string> = {}, traceId?: string) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.errorCode = errorCode
    this.details = details
    this.traceId = traceId
  }
}

let session: StoredSession | null = readSession()
let onUnauthorized: () => void = () => {}

function readSession(): StoredSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as StoredSession
    return parsed.expiresAt > Date.now() ? parsed : null
  } catch {
    return null
  }
}

export function saveSession(next: StoredSession | null) {
  session = next
  try {
    if (next) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    else sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    /* storage can be blocked; the session then lives in memory only */
  }
}

export const getSession = () => session
export const setUnauthorizedHandler = (fn: () => void) => {
  onUnauthorized = fn
}

type Query = Record<string, string | number | boolean | undefined | null>

export function toQuery(params?: Query): string {
  if (!params) return ''
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

interface Options {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  query?: Query
  signal?: AbortSignal
  /** Do not treat a 401 as an expired session (used by login). */
  anonymous?: boolean
}

async function send(path: string, { method = 'GET', body, query, signal, anonymous }: Options = {}): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json, text/csv' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (session && !anonymous) headers.Authorization = `Bearer ${session.accessToken}`

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  signal?.addEventListener('abort', () => controller.abort())

  let response: Response
  try {
    response = await fetch(`${BASE}${path}${toQuery(query)}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    })
  } catch (error) {
    if (signal?.aborted) throw error
    throw new ApiError(0, 'NETWORK_ERROR', 'No se pudo conectar con el servidor')
  } finally {
    clearTimeout(timer)
  }

  if (response.ok) return response

  let problem: Record<string, unknown> = {}
  try {
    problem = await response.json()
  } catch {
    /* non-JSON error (proxy, gateway) */
  }
  const details =
    problem.details && typeof problem.details === 'object' ? (problem.details as Record<string, string>) : {}
  const error = new ApiError(
    response.status,
    typeof problem.errorCode === 'string' ? problem.errorCode : `HTTP_${response.status}`,
    typeof problem.detail === 'string' ? problem.detail : response.statusText,
    details,
    typeof problem.traceId === 'string' ? problem.traceId : undefined,
  )
  if (response.status === 401 && session && !anonymous) {
    saveSession(null)
    onUnauthorized()
  }
  throw error
}

export async function api<T>(path: string, options?: Options): Promise<T> {
  const response = await send(path, options)
  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** Authenticated download: the CSV exports need the Bearer header, so a plain link would not work. */
export async function download(path: string, query: Query, filename: string): Promise<void> {
  const response = await send(path, { query })
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
