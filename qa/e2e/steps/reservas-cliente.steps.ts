/** Steps de features/03-reservas-cliente.feature (E2E-05 a E2E-08). */
import { expect } from '@playwright/test';
import { dateIn } from '../support/data.ts';
import { Given, Then, When, need } from './fixtures.ts';

When('busca en el catálogo el servicio del negocio de prueba y elige reservarlo', async ({ world, catalogPage }) => {
  await catalogPage.open();
  await catalogPage.search(world.seed.serviceName);
  await catalogPage.startBooking(world.seed.serviceName, world.seed.organizationName);
});

When('elige el día dentro de {int} días y la primera franja libre', async ({ bookingPage }, days: number) => {
  await bookingPage.chooseDay(dateIn(days));
  await bookingPage.chooseFirstFreeSlot();
});

Then(
  'el resumen muestra el negocio, el precio y la cancelación gratuita hasta {int} día antes',
  async ({ world, bookingPage }, days: number) => {
    await bookingPage.expectSummary([
      world.seed.organizationName,
      world.seed.servicePrice.toLocaleString('es-CO'),
      `Gratis hasta ${days} ${days === 1 ? 'día' : 'días'} antes`,
      'Inmediata',
    ]);
  },
);

When('confirma la reserva', async ({ bookingPage }) => {
  await bookingPage.confirm();
});

Then('ve el comprobante {string} con el código de la reserva', async ({ world, bookingPage }, title: string) => {
  const code = await bookingPage.expectReceipt(title as 'Reserva confirmada');
  world.booking = { id: '', code, startsAt: '' };
});

Then('la reserva aparece en {string} como {string}', async ({ world, bookingPage, myBookingsPage }, _page: string, status: string) => {
  await bookingPage.goToMyBookings();
  await myBookingsPage.expectBooking(need(world.booking, 'una reserva').code, [world.seed.serviceName, status]);
});

/** Reserva hecha por la UI en este navegador: "Mis reservas" solo lista lo reservado aquí (lib/myBookings.ts). */
Given('que la clienta reservó por la web una franja dentro de {int} días', async ({ world, catalogPage, bookingPage }, days: number) => {
  await catalogPage.open();
  await catalogPage.search(world.seed.serviceName);
  await catalogPage.startBooking(world.seed.serviceName, world.seed.organizationName);
  await bookingPage.chooseDay(dateIn(days));
  await bookingPage.chooseFirstFreeSlot();
  await bookingPage.expectSummary([world.seed.organizationName]);
  await bookingPage.confirm();
  world.booking = { id: '', code: await bookingPage.expectReceipt('Reserva confirmada'), startsAt: '' };
});

When('abre {string} y pide cancelar esa reserva con el motivo {string}', async ({ world, myBookingsPage }, _p: string, reason: string) => {
  const { code } = need(world.booking, 'una reserva');
  await myBookingsPage.open();
  await myBookingsPage.startCancellation(code);
  await myBookingsPage.requestCancellation(reason);
});

Then('se le informa {string}', async ({ myBookingsPage }, title: string) => {
  await myBookingsPage.expectCancellationTerms(title as 'Cancelación sin penalidad');
});

When('confirma la cancelación', async ({ myBookingsPage }) => {
  await myBookingsPage.confirmCancellation();
});

Then('ve el aviso {string} y la reserva queda {string}', async ({ world, myBookingsPage }, toast: string, status: string) => {
  await myBookingsPage.expectToast(toast);
  await myBookingsPage.expectStatus(need(world.booking, 'una reserva').code, status);
});

When('abre {string} y elige reprogramar esa reserva', async ({ world, myBookingsPage }, _page: string) => {
  await myBookingsPage.open();
  await myBookingsPage.startReschedule(need(world.booking, 'una reserva').code);
});

Then('puede elegir una nueva franja para la misma reserva', async ({ bookingPage }) => {
  await bookingPage.chooseFirstFreeSlot();
});

When('abre {string}', async ({ myBookingsPage }, _page: string) => {
  await myBookingsPage.open();
});

Then('ve esa reserva entre las próximas', async ({ world, myBookingsPage }) => {
  await myBookingsPage.expectSection('Próximas', need(world.booking, 'una reserva').code);
});

Then('puede descargar el comprobante PDF de la reserva', async ({ world, myBookingsPage }) => {
  const download = await myBookingsPage.downloadReceipt(need(world.booking, 'una reserva').code);
  expect(download.suggestedFilename()).toMatch(/\.pdf$/i);
});
