/** Pasos compartidos por varias características: clientas de prueba, ingreso y reservas previas. */
import { bookViaApi, createVerifiedClient } from '../support/data.ts';
import { Given, need } from './fixtures.ts';

Given('que existe una clienta verificada', async ({ world }) => {
  world.client = await createVerifiedClient();
});

Given('la clienta inició sesión', async ({ world, loginPage }) => {
  const client = need(world.client, 'una clienta');
  await loginPage.signInSuccessfully(client.email, client.password);
});

Given(
  '(que )la clienta tiene una reserva confirmada hecha desde otro dispositivo dentro de {int} días',
  async ({ world }, days: number) => {
    world.booking = await bookViaApi(need(world.client, 'una clienta'), world.seed, days);
  },
);

Given('el proveedor del negocio de prueba inició sesión', async ({ world, loginPage }) => {
  await loginPage.signInSuccessfully(world.seed.providerEmail, 'ClaveSegura2026!');
});

Given('que la administradora inició sesión', async ({ world, loginPage }) => {
  await loginPage.signInSuccessfully(world.seed.adminEmail, 'ClaveSegura2026!');
});
