/** Steps de features/01-registro-e-ingreso.feature (E2E-01 a E2E-03). */
import { PASSWORD, stubConfirmHash, unique } from '../support/api.ts';
import { birthDateForAge } from '../support/data.ts';
import { Given, Then, When, need } from './fixtures.ts';

const newForm = (age: number) => ({
  fullName: `Mariana Restrepo ${unique('').slice(-4).toUpperCase()}`,
  document: `10${String(Date.now()).slice(-8)}`,
  birthDate: birthDateForAge(age),
  email: `${unique('registro')}@e2e.test`,
  phone: '+573104445566',
  city: 'Medellín',
  password: PASSWORD,
});

Given('que una persona adulta abre el formulario de registro', async ({ world, registerPage }) => {
  world.form = newForm(30);
  await registerPage.open();
});

Given('que una persona de {int} años abre el formulario de registro', async ({ world, registerPage }, age: number) => {
  world.form = newForm(age);
  await registerPage.open();
});

When(
  /^completa el formulario (?:con datos válidos|con su fecha de nacimiento) y envía el registro$/,
  async ({ world, registerPage }) => {
    await registerPage.fill(need(world.form, 'los datos del registro'));
    await registerPage.submit();
  },
);

Then('ve que debe revisar su correo para verificar la cuenta', async ({ world, registerPage }) => {
  await registerPage.expectCheckYourEmail(need(world.form, 'los datos del registro').email);
});

When('abre el enlace de verificación que recibió', async ({ world, verifyPage }) => {
  // El simulador de Supabase entrega el token_hash que llevaría el enlace del correo.
  const tokenHash = await stubConfirmHash(need(world.form, 'los datos del registro').email);
  await verifyPage.openLink(tokenHash);
});

Then('ve que su correo quedó confirmado', async ({ verifyPage }) => {
  await verifyPage.expectConfirmed();
});

Then('al ingresar con su correo y contraseña entra a la plataforma como cliente', async ({ world, verifyPage, loginPage }) => {
  const form = need(world.form, 'los datos del registro');
  await verifyPage.continueToLogin();
  await loginPage.signIn(form.email, form.password);
  await loginPage.expectNavLink('Mis reservas');
  await loginPage.expectNoNavLink('Mi negocio');
});

Then('ve el error {string} y sigue en el formulario', async ({ registerPage }, message: string) => {
  await registerPage.expectFieldError(message);
});

When('intenta ingresar con su correo y la contraseña {string}', async ({ world, loginPage }, password: string) => {
  await loginPage.open();
  await loginPage.signIn(need(world.client, 'una clienta').email, password);
});

Then('ve el mensaje {string} y no inicia sesión', async ({ loginPage }, message: string) => {
  await loginPage.expectError(message);
});
