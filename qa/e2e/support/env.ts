/** Direcciones del entorno bajo prueba. Por defecto, las que levanta support/start-env.sh. */
export const env = {
  baseUrl: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5181',
  apiUrl: process.env.E2E_API_URL ?? 'http://localhost:18311',
  stubUrl: process.env.E2E_STUB_URL ?? 'http://127.0.0.1:19911',
};
