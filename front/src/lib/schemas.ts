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

export const policySchema = z
  .object({
    bookingNoticeMinutesMin: minutes('Anticipación mínima'),
    bookingNoticeMinutesMax: minutes('Anticipación máxima'),
    freeCancellationWindowMinutes: minutes('Cancelación gratuita'),
  })
  .refine((v) => v.bookingNoticeMinutesMin <= v.bookingNoticeMinutesMax, {
    path: ['bookingNoticeMinutesMax'],
    message: 'Debe ser mayor o igual a la mínima',
  })

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

const optionalMinutes = (max: number) =>
  z.coerce.number({ message: 'Ingresa un número' }).int().min(0, 'No puede ser negativo').max(max, `Máximo ${max}`)

export const serviceSchema = z.object({
  name: z.string().trim().min(1, 'Ingresa un nombre').max(120, 'Máximo 120 caracteres'),
  description: z.string().trim().max(500, 'Máximo 500 caracteres').optional(),
  durationMinutes: z.coerce.number({ message: 'Ingresa un número' }).int().min(1, 'Mínimo 1').max(1440, 'Máximo 1440'),
  preparationMinutes: optionalMinutes(1440),
  cleanupMinutes: optionalMinutes(1440),
  price: z.coerce.number({ message: 'Ingresa un número' }).min(0, 'No puede ser negativo'),
  customerCapacity: z.coerce.number({ message: 'Ingresa un número' }).int().min(1, 'Mínimo 1').max(500, 'Máximo 500'),
})
export type ServiceForm = z.input<typeof serviceSchema>
