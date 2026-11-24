/** Steps de features/02-catalogo.feature (E2E-04). */
import { Given, Then, When } from './fixtures.ts';

Given('que un visitante abre el catálogo sin iniciar sesión', async ({ catalogPage }) => {
  await catalogPage.open();
});

When('busca {string}', async ({ catalogPage }, text: string) => {
  await catalogPage.search(text);
});

Then('ve el servicio del negocio de prueba con su sede, duración y precio', async ({ world, catalogPage }) => {
  const { serviceName, organizationName, locationName, servicePrice } = world.seed;
  const price = servicePrice.toLocaleString('es-CO'); // 35.000
  await catalogPage.expectServiceListed(serviceName, organizationName, [locationName, 'Medellín', '30 min', price]);
});

Then('ve el mensaje de que no se encontraron servicios', async ({ catalogPage }) => {
  await catalogPage.expectNoResults();
});
