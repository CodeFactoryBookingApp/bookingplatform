import { ApiError } from '@/api/client'

/** Backend messages are in English; users see these. Keyed by ErrorCode.java. */
const MESSAGES: Record<string, string> = {
  VALIDATION_ERROR: 'Revisa los campos marcados.',
  MINOR_NOT_ALLOWED: 'El registro solo está permitido para mayores de 18 años.',
  PASSWORD_TOO_WEAK: 'La contraseña no cumple la política de seguridad.',
  VERIFICATION_TOKEN_INVALID: 'El enlace de verificación no es válido o ya se usó.',
  AUTH_REQUIRED: 'Inicia sesión para continuar.',
  INVALID_CREDENTIALS: 'Correo o contraseña incorrectos.',
  AUTH_TOKEN_INVALID: 'Tu sesión no es válida. Inicia sesión de nuevo.',
  RESOURCE_NOT_FOUND: 'No encontramos lo que buscas.',
  METHOD_NOT_ALLOWED: 'Operación no permitida.',
  EMAIL_NOT_CONFIRMED: 'Aún no confirmas tu correo. Revisa tu bandeja o solicita un nuevo enlace.',
  ACCESS_DENIED: 'No tienes permisos para hacer esto. Solo el propietario o un gerente puede modificarlo.',
  DUPLICATE_EMAIL: 'Ese correo ya está registrado.',
  DUPLICATE_DOCUMENT: 'Ese documento ya está registrado.',
  DUPLICATE_IDENTIFICATION: 'Ya existe un proveedor con esa identificación.',
  INVALID_STATUS_TRANSITION: 'El estado actual no permite esta operación.',
  DUPLICATE_NAME: 'Ese nombre ya está en uso.',
  OVERLAPPING_PERIOD: 'El periodo se cruza con uno existente.',
  HAS_FUTURE_BOOKINGS: 'Hay reservas futuras que debes resolver primero.',
  HAS_BOOKINGS: 'Tiene reservas, así que solo puede desactivarse.',
  INSUFFICIENT_RESOURCES: 'No hay recursos activos suficientes para ofrecer este servicio.',
  CONFIRMATION_REQUIRED: 'Esta operación tiene consecuencias y requiere confirmación.',
  RATE_LIMITED: 'Demasiadas solicitudes. Intenta de nuevo en un momento.',
  ACCOUNT_LOCKED: 'Cuenta bloqueada temporalmente por intentos fallidos.',
  UPSTREAM_AUTH_ERROR: 'El servicio de identidad no está disponible. Intenta más tarde.',
  INTERNAL_ERROR: 'Ocurrió un error inesperado.',
  NETWORK_ERROR: 'No pudimos conectar con el servidor. Revisa tu conexión.',
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.errorCode === 'ACCOUNT_LOCKED' && error.details.retryAfterMinutes) {
      return `Cuenta bloqueada por intentos fallidos. Vuelve a intentarlo en ${error.details.retryAfterMinutes} minutos.`
    }
    return MESSAGES[error.errorCode] ?? 'No pudimos completar la operación.'
  }
  return 'Ocurrió un error inesperado.'
}

/** Support reference for failures the user cannot fix. */
export function traceOf(error: unknown): string | undefined {
  return error instanceof ApiError && error.status >= 500 ? error.traceId : undefined
}

/** Server-side field errors (details = field → message) to map onto form fields. */
export function fieldErrors(error: unknown): [string, string][] {
  if (!(error instanceof ApiError) || error.errorCode !== 'VALIDATION_ERROR') return []
  return Object.entries(error.details)
}
