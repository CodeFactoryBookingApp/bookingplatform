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
  ACCESS_DENIED: 'No tienes permisos para hacer esto.',
  NOT_ACCEPTABLE: 'El servidor no puede responder en el formato pedido.',
  UNSUPPORTED_MEDIA_TYPE: 'El servidor no acepta el formato enviado.',
  DUPLICATE_EMAIL: 'Ese correo ya está registrado.',
  DUPLICATE_DOCUMENT: 'Ese documento ya está registrado.',
  DUPLICATE_IDENTIFICATION: 'Ya existe un proveedor con esa identificación.',
  INVALID_STATUS_TRANSITION: 'El estado actual no permite esta operación.',
  DUPLICATE_NAME: 'Ese nombre ya está en uso.',
  OVERLAPPING_PERIOD: 'El periodo se cruza con uno existente.',
  SLOT_UNAVAILABLE: 'Ese horario ya no está disponible. Elige otro.',
  BOOKING_TERMS_CHANGED: 'El precio, la duración o las reglas cambiaron. Revisa las condiciones actualizadas.',
  CLIENT_BOOKING_OVERLAP: 'Ya tienes una reserva a esa hora.',
  HAS_FUTURE_BOOKINGS: 'Hay reservas futuras que debes resolver primero.',
  HAS_BOOKINGS: 'Tiene reservas, así que solo puede desactivarse.',
  INSUFFICIENT_RESOURCES: 'No hay recursos activos suficientes para ofrecer este servicio.',
  CONFIRMATION_REQUIRED: 'Esta operación tiene consecuencias y requiere confirmación.',
  LAST_ADMINISTRATOR: 'Es el último administrador de la plataforma; no puede perder ese rol.',
  ADMINISTRATOR_ACCOUNT_PROTECTED: 'Las cuentas de administrador no se pueden suspender.',
  ROLE_CHANGE_PENDING: 'Hay un cambio de rol en curso para esta cuenta. Intenta de nuevo en un momento.',
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
    if (error.errorCode === 'ACCESS_DENIED' && /organization/i.test(error.message)) {
      return 'Tu rol en este negocio no permite esta operación. Solo el propietario o un gerente puede hacerlo.'
    }
    if (error.errorCode === 'ACCESS_DENIED' && /client account/i.test(error.message)) {
      return 'Necesitas una cuenta de cliente activa y con el correo verificado para reservar.'
    }
    // Some state checks answer VALIDATION_ERROR without field details (Client.java); no field is marked then.
    if (error.errorCode === 'VALIDATION_ERROR' && Object.keys(error.details).length === 0) {
      return 'La solicitud no es válida en el estado actual del registro.'
    }
    if (error.errorCode === 'PASSWORD_TOO_WEAK' && error.details.violations) {
      return `La contraseña no cumple la política: ${translateViolations(error.details.violations)}.`
    }
    return MESSAGES[error.errorCode] ?? 'No pudimos completar la operación.'
  }
  return 'Ocurrió un error inesperado.'
}

const VIOLATIONS: [RegExp, string][] = [
  [/at least (\d+) characters/i, 'mínimo $1 caracteres'],
  [/at most (\d+) characters/i, 'máximo $1 caracteres'],
  [/uppercase/i, 'una mayúscula'],
  [/lowercase/i, 'una minúscula'],
  [/digit/i, 'un número'],
  [/special/i, 'un carácter especial'],
]

/** PasswordPolicy sends "a; b; c" in English. */
function translateViolations(text: string): string {
  return text
    .split(';')
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => {
      for (const [pattern, label] of VIOLATIONS) {
        const match = part.match(pattern)
        if (match) return label.replace('$1', match[1] ?? '')
      }
      return part
    })
    .join(', ')
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
