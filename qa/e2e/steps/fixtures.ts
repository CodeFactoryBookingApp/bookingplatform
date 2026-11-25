/**
 * Fixtures de playwright-bdd: cada escenario recibe sus Page Objects y un "mundo" con los datos que
 * van creando los pasos (clienta, reserva, proveedor pendiente). Los Page Objects son los únicos
 * que conocen localizadores; los steps solo los orquestan.
 */
import { test as base, createBdd } from 'playwright-bdd';
import { AdminPage } from '../pages/AdminPage.ts';
import { BookingPage } from '../pages/BookingPage.ts';
import { CatalogPage } from '../pages/CatalogPage.ts';
import { InboxPage } from '../pages/InboxPage.ts';
import { LoginPage } from '../pages/LoginPage.ts';
import { MyBookingsPage } from '../pages/MyBookingsPage.ts';
import { ProviderBookingsPage } from '../pages/ProviderBookingsPage.ts';
import { ProviderHomePage } from '../pages/ProviderHomePage.ts';
import { RegisterClientPage } from '../pages/RegisterClientPage.ts';
import { VerifyEmailPage } from '../pages/VerifyEmailPage.ts';
import { readSeed, type Booking, type Client, type Seed } from '../support/data.ts';
import type { ClientForm } from '../pages/RegisterClientPage.ts';

export interface World {
  seed: Seed;
  client?: Client;
  form?: ClientForm;
  booking?: Booking;
  pendingProvider?: { name: string; email: string; organizationId: string };
}

type Fixtures = {
  world: World;
  loginPage: LoginPage;
  registerPage: RegisterClientPage;
  verifyPage: VerifyEmailPage;
  catalogPage: CatalogPage;
  bookingPage: BookingPage;
  myBookingsPage: MyBookingsPage;
  inboxPage: InboxPage;
  providerBookingsPage: ProviderBookingsPage;
  providerHomePage: ProviderHomePage;
  adminPage: AdminPage;
};

export const test = base.extend<Fixtures>({
  world: async ({}, use) => use({ seed: readSeed() }),
  loginPage: async ({ page }, use) => use(new LoginPage(page)),
  registerPage: async ({ page }, use) => use(new RegisterClientPage(page)),
  verifyPage: async ({ page }, use) => use(new VerifyEmailPage(page)),
  catalogPage: async ({ page }, use) => use(new CatalogPage(page)),
  bookingPage: async ({ page }, use) => use(new BookingPage(page)),
  myBookingsPage: async ({ page }, use) => use(new MyBookingsPage(page)),
  inboxPage: async ({ page }, use) => use(new InboxPage(page)),
  providerBookingsPage: async ({ page }, use) => use(new ProviderBookingsPage(page)),
  providerHomePage: async ({ page }, use) => use(new ProviderHomePage(page)),
  adminPage: async ({ page }, use) => use(new AdminPage(page)),
});

export const { Given, When, Then } = createBdd(test);

/** Devuelve el valor o falla con un mensaje claro si un paso previo no lo preparó. */
export function need<T>(value: T | undefined, what: string): T {
  if (value === undefined) throw new Error(`El escenario no preparó ${what}`);
  return value;
}
