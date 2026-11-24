/** Steps de features/06-administracion.feature (E2E-10 y E2E-11). */
import { unique } from '../support/api.ts';
import { createPendingProvider } from '../support/data.ts';
import { Given, Then, When, need } from './fixtures.ts';

Given('que un negocio nuevo se registró y está pendiente de aprobación', async ({ world }) => {
  world.pendingProvider = await createPendingProvider(`Spa Montaña Verde ${unique('').slice(-5).toUpperCase()}`);
});

When('la administradora abre la lista de proveedores', async ({ adminPage }) => {
  await adminPage.openProviders();
});

Then('el negocio aparece como {string}', async ({ world, adminPage }, status: string) => {
  await adminPage.expectProviderStatus(need(world.pendingProvider, 'un proveedor pendiente').name, status);
});

When('lo aprueba con el motivo {string}', async ({ world, adminPage }, reason: string) => {
  await adminPage.approveProvider(need(world.pendingProvider, 'un proveedor pendiente').name, reason);
});

Then('ve el aviso {string} y el negocio queda {string}', async ({ world, adminPage }, toast: string, status: string) => {
  await adminPage.expectToast(toast);
  await adminPage.expectProviderStatus(need(world.pendingProvider, 'un proveedor pendiente').name, status);
});

When(
  'la administradora abre la lista de clientes y cambia el rol de la clienta a {string}',
  async ({ world, adminPage }, role: string) => {
    const client = need(world.client, 'una clienta');
    await adminPage.openClients();
    await adminPage.expectClientListed(client.fullName);
    await adminPage.changeRole(client.fullName, role as 'Proveedor');
  },
);

Then('ve el aviso {string}', async ({ adminPage }, toast: string) => {
  await adminPage.expectToast(toast);
});

When('la administradora sale y la clienta ingresa de nuevo', async ({ world, adminPage, loginPage }) => {
  const client = need(world.client, 'una clienta');
  await adminPage.logout();
  await loginPage.signInSuccessfully(client.email, client.password);
});

Then('la usuaria entra al área de negocio como proveedora', async ({ providerHomePage }) => {
  await providerHomePage.expectOpen();
  await providerHomePage.expectNavLink('Mi negocio');
  await providerHomePage.expectNoNavLink('Mis reservas');
});
