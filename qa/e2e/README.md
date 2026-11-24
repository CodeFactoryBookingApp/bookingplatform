# Pruebas funcionales automatizadas (E2E) — bookingplatform

Suite de pruebas de extremo a extremo sobre el front (React/Vite) y el backend reales, escrita en
Gherkin en español y ejecutada con Playwright en Chromium, Firefox y Microsoft Edge.

| Aspecto | Elección |
|---|---|
| Herramienta | Playwright (`@playwright/test` 1.65.1) |
| BDD | Gherkin (`# language: es`) con `playwright-bdd` 9.2.1, que traduce los `.feature` a pruebas de Playwright |
| Lenguaje | TypeScript |
| Patrón | Page Object Model: los step definitions no tienen localizadores; toda interacción con la UI pasa por `pages/` |
| Navegadores | `chromium`, `firefox` y `msedge` (Edge instalado, `channel: 'msedge'`), headless |
| Evidencias | captura, video y traza de cada prueba; reporte HTML de Playwright y reporte HTML/JSON de Cucumber |

## Estructura

```
qa/e2e/
  features/   escenarios Gherkin; cada uno lleva @E2E-nn, la HU (@HU-012) y, si existe, el caso de API (@CP14-01)
  steps/      step definitions (fixtures.ts crea los Page Objects y el "mundo" de cada escenario)
  pages/      Page Objects (únicos que conocen roles, etiquetas y textos de la UI)
  support/    entorno (start-env.sh, stop-env.sh), datos por la API (data.ts, api.ts), global-setup.ts
              y el simulador de Supabase Auth (stub/gotrue_stub.py)
  reports/    salida: playwright-html/, cucumber/, test-results/ (capturas, videos, trazas); no se versiona
```

## Entorno

La suite corre contra un entorno local aislado:

| Pieza | Puerto | Cómo |
|---|---|---|
| PostgreSQL 16 | 15711 | contenedor Docker `s3qa-e2e-pg` (Flyway crea el esquema al arrancar el backend) |
| Simulador de Supabase Auth | 19911 | `support/stub/gotrue_stub.py` (Python 3 + `cryptography`): firma JWT RS256, publica JWKS, `/admin/users`, `/token` (clave `ClaveSegura2026!`), `/verify` y control en `/_ctl/*` |
| Backend | 18311 | jar ya construido, perfil `cloud`, avisos de reserva cada 2 s (`-Dapp.booking-notices.poll-ms`) |
| Front | 5181 | `npx vite` con `VITE_API_TARGET=http://localhost:18311` (proxy de `/api`) |

La copia del simulador añade lo que necesita el cambio de rol (HU-022): `GET /admin/users`,
`GET`/`PUT /admin/users/{id}` y los claims `booking_role_revision` y `amr` en los tokens.

Requisitos: Docker, JDK 21+ (por defecto `C:/Program Files/Java/jdk-22.0.2`, cambiable con `E2E_JAVA_HOME`),
Python 3 con `cryptography`, Node 20+ y Git Bash en Windows.

```bash
# 1. Construir el backend si no hay jar (desde la raíz del repo)
./mvnw -DskipTests package

# 2. Levantar todo (desde qa/e2e). JAR apunta al jar si no está en ../../target
JAR=../../target/bookingplatform-0.0.1-SNAPSHOT.jar ./support/start-env.sh

# 3. Al terminar
./support/stop-env.sh
```

Logs y PIDs quedan en `qa/e2e/.env-run/`. Los puertos se cambian con `PG_PORT`, `STUB_PORT`,
`APP_PORT` y `FRONT_PORT` (y, para la suite, `E2E_BASE_URL`, `E2E_API_URL`, `E2E_STUB_URL`).

## Instalar y ejecutar

```bash
cd qa/e2e
npm ci                                  # o npm i la primera vez
npx playwright install chromium firefox # Edge se usa el instalado en el equipo

npm test                  # bddgen + los tres navegadores
npm run test:chromium     # un navegador: test:chromium, test:firefox, test:edge
npx bddgen && npx playwright test --grep @E2E-05     # un escenario por su ID
npx bddgen && npx playwright test --grep @HU-013     # por historia de usuario
npm run report            # abre reports/playwright-html
```

Reportes: `reports/playwright-html/index.html` (Playwright, con captura, video y traza por prueba),
`reports/cucumber/cucumber-report.html` y `.json` (vista por característica y paso) y
`reports/playwright-results.json`.

## Datos de prueba

`support/global-setup.ts` crea por la API, una vez por ejecución, un negocio aprobado
("Barbería La Esquina XXXXX") con sede en Medellín, un recurso, el servicio activo
"Corte de cabello clásico" (30 min, $35.000) y una agenda de 08:00 a 18:00 todos los días; la
administradora `admin.e2e@e2e.test` se crea en el simulador. Cada escenario crea sus propias
clientas, reservas y proveedores pendientes (`support/data.ts`), así que no dependen del orden.
Las pruebas corren con un worker porque comparten una agenda de un solo recurso.

## Escenarios

| ID | Historia | Caso API | Escenario | Tipo |
|---|---|---|---|---|
| E2E-01 | HU-001, HU-021 | | Registro, verificación con el enlace e ingreso como cliente | Positivo |
| E2E-02 | HU-001 | | Un menor de edad no puede registrarse | Excepcional |
| E2E-03 | HU-021 | | Ingreso con contraseña errada | Excepcional |
| E2E-04 | HU-005 | | Catálogo público: búsqueda con resultados y sin coincidencias | Positivo |
| E2E-05 | HU-012 | | Reserva de una franja disponible desde el catálogo | Positivo |
| E2E-06 | HU-013 | | Cancelación dentro de la ventana gratuita | Positivo |
| E2E-07 | HU-014 | CP14-01 | Reprogramación desde "Mis reservas" (`@defecto @fixme`) | Positivo |
| E2E-08 | HU-017 | CP17-01, CP17-04 | Historial desde el servidor y comprobante PDF (`@defecto @fixme`) | Positivo |
| E2E-09 | HU-018 | | El proveedor encuentra por código la reserva de una clienta | Positivo |
| E2E-10 | HU-002 | | La administradora aprueba un proveedor pendiente | Positivo |
| E2E-11 | HU-022 | CP22-01 | La administradora cambia el rol de una clienta a proveedora | Positivo |
| E2E-12 | HU-015 | | La clienta recibe en su bandeja el aviso de la reserva creada | Positivo |

E2E-07 (DEF-E2E-01) y E2E-08 (DEF-E2E-02) describen el comportamiento esperado de HU-014 y HU-017, que el backend ya ofrece
pero el front no expone; están marcados `@defecto @fixme` para que no rompan la ejecución. Al
corregir el front se quita `@fixme` y deben pasar.

Para convertir un video de una prueba en GIF: `FFMPEG=<ffmpeg completo> support/video-a-gif.sh reports/test-results/<prueba>/video.webm salida.gif`.
Para el resumen por navegador (total, pasaron, fallaron, omitidas, tiempo): `node support/resumen.mjs`.
