/**
 * Datos de prueba creados por la API. El global-setup crea una vez por ejecución un negocio
 * aprobado con su servicio, recurso y agenda; cada escenario crea sus propios clientes, reservas
 * y proveedores pendientes con las fábricas de abajo, para no depender del orden de ejecución.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { api, loginToken, PASSWORD, stubConfirmHash, stubUser, unique } from './api.ts';

const here = path.dirname(fileURLToPath(import.meta.url));
const SEED_FILE = path.join(here, '.data', 'seed.json');

export interface Seed {
  runId: string;
  adminEmail: string;
  providerEmail: string;
  organizationId: string;
  organizationName: string;
  locationId: string;
  locationName: string;
  serviceId: string;
  serviceName: string;
  servicePrice: number;
}

export interface Client {
  id: string;
  fullName: string;
  email: string;
  password: string;
}

export interface Booking {
  id: string;
  code: string;
  startsAt: string;
}

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const FIRST_NAMES = ['Laura', 'Camila', 'Valentina', 'Andrés', 'Santiago', 'Mateo', 'Isabela', 'Julián'];
const LAST_NAMES = ['Gómez', 'Restrepo', 'Ospina', 'Zapata', 'Cardona', 'Muñoz', 'Henao', 'Arango'];

const pick = <T>(xs: T[]) => xs[Math.floor(Math.random() * xs.length)];
/** Documento numérico de 10 dígitos, distinto en cada llamada. */
const documentNumber = () => `1${String(Date.now()).slice(-6)}${String(Math.floor(Math.random() * 1000)).padStart(3, '0')}`;

/** Fecha local (YYYY-MM-DD) dentro de `days` días. */
export function dateIn(days: number): string {
  const d = new Date(Date.now() + days * 86_400_000);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Fecha de nacimiento de alguien que cumple `years` años hoy menos un día (o más un día si es menor). */
export function birthDateForAge(years: number): string {
  const d = new Date();
  d.setFullYear(d.getFullYear() - years);
  d.setDate(d.getDate() + (years >= 18 ? -1 : 1));
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function readSeed(): Seed {
  return JSON.parse(fs.readFileSync(SEED_FILE, 'utf-8')) as Seed;
}

async function referenceData() {
  const cities = await api<{ id: string; name: string }[]>('GET', '/cities');
  const categories = await api<{ id: string; name: string }[]>('GET', '/organization-categories');
  return {
    medellin: cities.find((c) => c.name === 'Medellín') ?? cities[0],
    belleza: categories.find((c) => c.name === 'Belleza') ?? categories[0],
  };
}

/** Registra un proveedor (queda PENDING_APPROVAL) y confirma el correo del dueño. */
export async function createPendingProvider(name: string) {
  const { medellin, belleza } = await referenceData();
  const email = `${unique('proveedor')}@e2e.test`;
  const reg = await api<{ organizationId: string }>('POST', '/provider-registrations', {
    email,
    password: PASSWORD,
    name,
    categoryId: belleza.id,
    identificationType: 'NIT',
    identificationNumber: `900${String(Date.now()).slice(-6)}-${Math.floor(Math.random() * 10)}`,
    contactPhone: '+573001112233',
    locations: [{ name: 'Sede Laureles', address: 'Carrera 76 # 33-20', cityId: medellin.id }],
    bookingNoticeMinutesMin: 0,
    bookingNoticeMinutesMax: 60 * 24 * 60,
    freeCancellationWindowMinutes: 24 * 60,
  });
  await api('POST', '/registrations/email-verifications', { tokenHash: await stubConfirmHash(email) });
  return { email, organizationId: reg.organizationId, name };
}

/** Negocio aprobado con un servicio activo, un recurso y una agenda de 08:00 a 18:00 todos los días. */
export async function createSeed(): Promise<Seed> {
  const runId = unique('run');
  const adminEmail = 'admin.e2e@e2e.test';
  await stubUser(adminEmail, 'ADMIN');
  const admin = await loginToken(adminEmail);

  const organizationName = `Barbería La Esquina ${runId.slice(-5).toUpperCase()}`;
  const provider = await createPendingProvider(organizationName);
  await api('POST', `/admin/organizations/${provider.organizationId}/approval`, { reason: 'Documentos verificados (setup E2E)' }, admin);

  const token = await loginToken(provider.email);
  const orgId = provider.organizationId;
  const org = await api<{ locations: { id: string; name: string }[] }>('GET', `/organizations/${orgId}`, undefined, token);
  const location = org.locations[0];
  const type = await api<{ id: string }>('POST', `/organizations/${orgId}/resource-types`, { name: 'Silla de barbería' }, token);
  const resource = await api<{ id: string }>('POST', `/organizations/${orgId}/resources`,
    { resourceTypeId: type.id, locationId: location.id, name: 'Silla 1', capacity: 1 }, token);
  const serviceName = 'Corte de cabello clásico';
  const servicePrice = 35000;
  const service = await api<{ id: string }>('POST', `/organizations/${orgId}/services`, {
    details: { name: serviceName, description: 'Corte a tijera y máquina con lavado.', durationMinutes: 30, price: servicePrice },
    locationIds: [location.id],
    requirements: [{ resourceTypeId: type.id, quantity: 1 }],
  }, token);
  await api('POST', `/organizations/${orgId}/services/${service.id}/activation`, undefined, token);
  await api('POST', `/organizations/${orgId}/agendas`, {
    name: 'Agenda principal',
    locationId: location.id,
    resourceId: resource.id,
    slotMinutes: 30,
    validFrom: dateIn(0),
    serviceIds: [service.id],
    blocks: DAYS.map((dayOfWeek) => ({ dayOfWeek, startTime: '08:00', endTime: '18:00' })),
  }, token);

  const seed: Seed = {
    runId, adminEmail, providerEmail: provider.email, organizationId: orgId, organizationName,
    locationId: location.id, locationName: location.name, serviceId: service.id, serviceName, servicePrice,
  };
  fs.mkdirSync(path.dirname(SEED_FILE), { recursive: true });
  fs.writeFileSync(SEED_FILE, JSON.stringify(seed, null, 2));
  return seed;
}

/** Cliente registrado por la API con el correo ya verificado (estado ACTIVE). */
export async function createVerifiedClient(): Promise<Client> {
  const fullName = `${pick(FIRST_NAMES)} ${pick(LAST_NAMES)} ${unique('').slice(-4).toUpperCase()}`;
  const email = `${unique('cliente')}@e2e.test`;
  const res = await api<{ clientId: string }>('POST', '/registrations', {
    fullName, document: documentNumber(), birthDate: '1995-05-10', email, phone: '+573005556677',
    city: 'Medellín', notificationChannel: 'EMAIL', password: PASSWORD,
  });
  await api('POST', '/registrations/email-verifications', { tokenHash: await stubConfirmHash(email) });
  return { id: res.clientId, fullName, email, password: PASSWORD };
}

/** Reserva por la API (como si la clienta hubiera reservado desde otro dispositivo). */
export async function bookViaApi(client: Client, seed: Seed, daysAhead: number): Promise<Booking> {
  const token = await loginToken(client.email, client.password);
  const q = `serviceId=${seed.serviceId}&locationId=${seed.locationId}`;
  const slots = await api<{ startsAt: string }[]>('GET', `/booking/availability?${q}&date=${dateIn(daysAhead)}`, undefined, token);
  if (slots.length === 0) throw new Error(`Sin franjas libres en ${dateIn(daysAhead)}`);
  // Desde el final del día, para no chocar con las franjas que los escenarios eligen por la UI.
  const startsAt = new Date(slots[slots.length - 1].startsAt).toISOString();
  const preview = await api<{ termsVersion: string }>('GET', `/booking/preview?${q}&startsAt=${encodeURIComponent(startsAt)}`, undefined, token);
  const booking = await api<{ id: string; code: string }>('POST', '/bookings',
    { serviceId: seed.serviceId, locationId: seed.locationId, startsAt, termsVersion: preview.termsVersion }, token);
  return { id: booking.id, code: booking.code, startsAt };
}
