import { expect, type Locator, type Page } from '@playwright/test';

export type NavLinkName = 'Catálogo' | 'Mis reservas' | 'Mi negocio' | 'Administración' | 'Bandeja';

/**
 * Base de todos los Page Objects: la barra superior (Shell.tsx), los avisos emergentes (sonner)
 * y la navegación. Toda interacción con la UI pasa por un Page Object; los steps no ven localizadores.
 * Los localizadores usan roles, etiquetas y textos accesibles porque el front no tiene data-testid.
 */
export abstract class BasePage {
  constructor(protected readonly page: Page) {}

  protected get nav(): Locator {
    return this.page.getByRole('navigation', { name: 'Principal' });
  }

  protected heading(name: string | RegExp): Locator {
    return this.page.getByRole('heading', { level: 1, name });
  }

  async goToNav(link: NavLinkName) {
    await this.nav.getByRole('link', { name: link }).click();
  }

  async logout() {
    await this.page.getByRole('button', { name: 'Salir' }).click();
    await expect(this.page.getByRole('link', { name: 'Ingresar' }).first()).toBeVisible();
  }

  async expectToast(text: string | RegExp) {
    await expect(this.page.getByText(text).first()).toBeVisible();
  }

  async expectNavLink(link: NavLinkName) {
    await expect(this.nav.getByRole('link', { name: link })).toBeVisible();
  }

  async expectNoNavLink(link: NavLinkName) {
    await expect(this.nav.getByRole('link', { name: link })).toHaveCount(0);
  }
}
