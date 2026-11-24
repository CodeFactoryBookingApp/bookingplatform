import { z } from 'zod'

// Same rules as the backend DTOs and PasswordPolicy, so most mistakes show before a round trip.

export const email = z.string().trim().min(1, 'Ingresa tu correo').email('Correo no válido').max(160, 'Máximo 160 caracteres')

export const password = z
  .string()
  .min(8, 'Mínimo 8 caracteres')
  .max(72, 'Máximo 72 caracteres')
  .regex(/[A-Z]/, 'Incluye una mayúscula')
  .regex(/[a-z]/, 'Incluye una minúscula')
  .regex(/[0-9]/, 'Incluye un número')
  .regex(/[^A-Za-z0-9]/, 'Incluye un carácter especial')

export const phone = z.string().trim().regex(/^\+?[0-9]{7,15}$/, 'Entre 7 y 15 dígitos, con + opcional al inicio')

function isAdult(iso: string): boolean {
  const birth = new Date(`${iso}T00:00:00`)
  const limit = new Date()
  limit.setFullYear(limit.getFullYear() - 18)
  return birth <= limit
}

export const clientSchema = z.object({
  fullName: z.string().trim().min(1, 'Ingresa tu nombre completo').max(120, 'Máximo 120 caracteres'),
  document: z.string().trim().regex(/^[A-Za-z0-9-]{5,20}$/, '5 a 20 letras, números o guiones'),
  birthDate: z
    .string()
    .min(1, 'Ingresa tu fecha de nacimiento')
    .refine((v) => new Date(v) < new Date(), 'Debe ser una fecha pasada')
    .refine(isAdult, 'Debes ser mayor de 18 años'),
  email,
  phone,
  city: z.string().trim().min(1, 'Ingresa tu ciudad').max(80, 'Máximo 80 caracteres'),
  notificationChannel: z.enum(['EMAIL', 'SMS', 'WHATSAPP']),
  password,
})
export type ClientForm = z.infer<typeof clientSchema>

export const loginSchema = z.object({
  email: z.string().trim().min(1, 'Ingresa tu correo').email('Correo no válido'),
  password: z.string().min(1, 'Ingresa tu contraseña'),
})

export const resetSchema = z
  .object({ newPassword: password, confirm: z.string() })
  .refine((v) => v.newPassword === v.confirm, { path: ['confirm'], message: 'Las contraseñas no coinciden' })

const minutes = (label: string) =>
  z.coerce.number({ message: `${label}: ingresa un número` }).int('Debe ser entero').min(0, 'No puede ser negativo')

const policyFields = {
  bookingNoticeMinutesMin: minutes('Anticipación mínima'),
  bookingNoticeMinutesMax: minutes('Anticipación máxima'),
  freeCancellationWindowMinutes: minutes('Cancelación gratuita'),
}
const noticeOrder = {
  check: (v: { bookingNoticeMinutesMin: number; bookingNoticeMinutesMax: number }) =>
    v.bookingNoticeMinutesMin <= v.bookingNoticeMinutesMax,
  params: { path: ['bookingNoticeMinutesMax'], message: 'Debe ser mayor o igual a la mínima' },
}

/** Rules sent with the provider registration (RegisterProviderRequest). */
export const policySchema = z.object(policyFields).refine(noticeOrder.check, noticeOrder.params)

/** Rules a provider edits later (OperationPolicyRequest), which also choose manual confirmation. */
export const policyEditSchema = z
  .object({ ...policyFields, manualConfirmation: z.boolean() })
  .refine(noticeOrder.check, noticeOrder.params)

export const providerSchema = z.object({
  email,
  password,
  name: z.string().trim().min(1, 'Ingresa el nombre del negocio').max(160, 'Máximo 160 caracteres'),
  categoryId: z.string().min(1, 'Elige una categoría'),
  identificationType: z.enum(['NIT', 'CC', 'CE', 'PASAPORTE']),
  identificationNumber: z.string().trim().regex(/^(?=.*[A-Za-z0-9])[A-Za-z0-9-]{5,20}$/, '5 a 20 letras, números o guiones'),
  contactEmail: z.union([z.literal(''), email]),
  contactPhone: phone,
  locations: z
    .array(
      z.object({
        name: z.string().trim().min(1, 'Ingresa el nombre de la sede').max(120, 'Máximo 120 caracteres'),
        address: z.string().trim().min(1, 'Ingresa la dirección').max(200, 'Máximo 200 caracteres'),
        cityId: z.string().min(1, 'Elige una ciudad'),
      }),
    )
    .min(1, 'Agrega al menos una sede')
    .max(20, 'Máximo 20 sedes'),
  policy: policySchema,
})
export type ProviderForm = z.input<typeof providerSchema>

export const resourceTypeSchema = z.object({
  name: z.string().trim().min(1, 'Ingresa un nombre').max(80, 'Máximo 80 caracteres'),
  description: z.string().trim().max(300, 'Máximo 300 caracteres').optional(),
})

export const resourceSchema = z.object({
  resourceTypeId: z.string().min(1, 'Elige un tipo'),
  locationId: z.string().min(1, 'Elige una sede'),
  name: z.string().trim().min(1, 'Ingresa un nombre').max(120, 'Máximo 120 caracteres'),
  capacity: z.coerce.number({ message: 'Ingresa un número' }).int().min(1, 'Mínimo 1').max(1000, 'Máximo 1000'),
})

export const maintenanceSchema = z
  .object({
    startsAt: z.string().min(1, 'Elige el inicio'),
    endsAt: z.string().min(1, 'Elige el fin'),
    reason: z.string().trim().max(300, 'Máximo 300 caracteres').optional(),
  })
  .refine((v) => !v.startsAt || !v.endsAt || new Date(v.endsAt) > new Date(v.startsAt), {
    path: ['endsAt'],
    message: 'El fin debe ser posterior al inicio',
  })

/** An empty input means "not sent": the backend applies its default (ServiceRequests). */
const blankAsUndefined = (value: unknown) => (value === '' || value === null ? undefined : value)

const optionalMinutes = (max: number) =>
  z.preprocess(
    blankAsUndefined,
    z.coerce.number({ message: 'Ingresa un número' }).int().min(0, 'No puede ser negativo').max(max, `Máximo ${max}`).optional(),
  )

/** @Digits(integer = 10, fraction = 2) in ServiceRequests. */
const price = z.coerce
  .number({ message: 'Ingresa un número' })
  .min(0, 'No puede ser negativo')
  .refine((v) => Math.abs(v * 100 - Math.round(v * 100)) < 1e-6, 'Máximo 2 decimales')
  .refine((v) => Math.trunc(v).toString().length <= 10, 'Máximo 10 dígitos enteros')

export const serviceSchema = z.object({
  name: z.string().trim().min(1, 'Ingresa un nombre').max(120, 'Máximo 120 caracteres'),
  description: z.string().trim().max(500, 'Máximo 500 caracteres').optional(),
  durationMinutes: z.coerce.number({ message: 'Ingresa un número' }).int().min(1, 'Mínimo 1').max(1440, 'Máximo 1440'),
  preparationMinutes: optionalMinutes(1440),
  cleanupMinutes: optionalMinutes(1440),
  price,
  customerCapacity: z.preprocess(
    blankAsUndefined,
    z.coerce.number({ message: 'Ingresa un número' }).int().min(1, 'Mínimo 1').max(500, 'Máximo 500').optional(),
  ),
})
export type ServiceForm = z.input<typeof serviceSchema>

/** HU-015: OrganizationReminderController.LeadTimes, up to 10 values of 1..10080 minutes. */
export const MAX_LEAD_MINUTES = 10080

/** HU-015: BookingReminderSettingsController.PreferencesRequest. */
export const preferencesSchema = z.object({
  remindersEnabled: z.boolean(),
  quietFrom: z.string().regex(/^\d{2}:\d{2}/, 'Elige una hora'),
  quietUntil: z.string().regex(/^\d{2}:\d{2}/, 'Elige una hora'),
  timezone: z.string().trim().max(80, 'Máximo 80 caracteres'),
})
export type PreferencesForm = z.infer<typeof preferencesSchema>

/** Reason a provider gives when cancelling a booking (ProviderCancellationRequest, max 300). */
export const CANCEL_REASON_MAX = 300
