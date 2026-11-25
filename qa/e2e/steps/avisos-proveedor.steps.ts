/** Steps de features/04-avisos.feature (E2E-12) y features/05-proveedor.feature (E2E-09). */
import { Then, When, need } from './fixtures.ts';

When('abre su bandeja de avisos', async ({ inboxPage }) => {
  await inboxPage.goToNav('Bandeja');
  await inboxPage.open();
});

Then(
  'encuentra el aviso {string} con el código, el servicio y el estado {string}',
  async ({ world, inboxPage }, subject: string, status: string) => {
    const { code } = need(world.booking, 'una reserva');
    await inboxPage.waitForMessage(subject, code);
    await inboxPage.openMessage(subject, code);
    await inboxPage.expectMessageBody(subject, code, [`Servicio: ${world.seed.serviceName}`, `Estado: ${status}`]);
  },
);

When('abre las reservas de su negocio y filtra por el código de esa reserva', async ({ world, providerBookingsPage }) => {
  await providerBookingsPage.openBusiness(world.seed.organizationName);
  await providerBookingsPage.openBookings();
  await providerBookingsPage.filterByCode(need(world.booking, 'una reserva').code);
});

Then(
  've la reserva con el nombre de la clienta, el servicio, el importe y el estado {string}',
  async ({ world, providerBookingsPage }, status: string) => {
    await providerBookingsPage.expectBooking(need(world.booking, 'una reserva').code, [
      need(world.client, 'una clienta').fullName,
      world.seed.serviceName,
      world.seed.servicePrice.toLocaleString('es-CO'),
      status,
    ]);
    await providerBookingsPage.expectTotal(1);
  },
);
