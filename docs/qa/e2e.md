# Pruebas end-to-end

Dos suites automatizadas recorren la aplicación de punta a punta contra un stack local:

| Suite | Herramienta | Dónde | Cómo se corre |
|---|---|---|---|
| API | JUnit 5 + REST Assured | `src/test/java/com/codefactory/bookingplatform/e2e/` (clases `*E2E`) | `./mvnw -Pe2e verify` |
| UI | Playwright (Chromium) | `front/e2e/` | `cd front && npm run e2e` |

Las dos cubren los mismos cuatro recorridos: **cliente** (registro, verificación, catálogo, reserva,
Mis reservas, cancelación), **proveedor** (alta, verificación, tipos de recurso, recursos, servicio,
agenda, publicación en el catálogo), **administrador** (aprobación de proveedores, clientes, auditoría,
cambio de rol) y **autenticación** (recuperación, bloqueo por intentos, logout).

## El stack local

Nada se simula: el backend habla con un GoTrue real y el navegador con el backend real.

| Pieza | Qué es | Dirección |
|---|---|---|
| Supabase Auth (GoTrue) | `supabase start`, firma los tokens con RS256 | `http://127.0.0.1:54321` |
| PostgreSQL | la base de Supabase local; Flyway crea el esquema al arrancar el backend | `127.0.0.1:54322` |
| Mailpit | atrapa los correos; las suites leen de su API los enlaces de verificación y recuperación | `http://127.0.0.1:54324` |
| Backend | el jar del proyecto | `http://localhost:8080` |
| Front | Vite, lo arranca Playwright con `VITE_API_TARGET` apuntando al backend | `http://localhost:3000` |

**Por qué RS256.** El backend valida los tokens contra el JWKS de Supabase (ADR-0003). Por defecto la CLI
firma con un secreto compartido (HS256), que el backend no puede validar por JWKS. `scripts/e2e/stack.sh` genera la primera vez
`supabase/signing_keys.json` (ignorado por git) y `supabase/config.toml` lo usa.

`supabase/config.toml` además exige confirmar el correo, sube los límites de envío de correos para que
las suites no choquen con ellos y apaga los servicios que no se usan (Studio, Storage, Realtime, Edge
Functions, Analytics).

## Correrlas en local

Requisitos: Docker (en WSL, Docker Desktop con la integración de la distro activada), Supabase CLI,
JDK 21 y Node 24.

```bash
scripts/e2e/stack.sh up             # Supabase + backend en :8080 (empaqueta el jar si hay cambios)
eval "$(scripts/e2e/stack.sh env)"  # SUPABASE_URL, SUPABASE_SECRET_KEY, MAILPIT_URL, E2E_API_URL

./mvnw -Pe2e verify                 # suite de API

cd front
npm install
npx playwright install chromium     # una sola vez
npm run e2e                         # suite de UI; el informe queda en front/playwright-report/

scripts/e2e/stack.sh down           # apaga todo y borra los datos locales
```

Cada prueba crea sus propios usuarios con correos únicos (`...@e2e.test`), así que se pueden repetir
sin limpiar la base. El administrador se crea con la API de administración de GoTrue
(`app_metadata.role = ADMIN`), igual que el arranque manual descrito en el runbook.

El perfil `e2e` de Maven corre solo las clases `*E2E`, sin pruebas unitarias, sin ITs y sin el piso de
cobertura. Tampoco reempaqueta `target/bookingplatform-*.jar`, que es el que está corriendo el stack:
reescribirlo con la JVM viva rompe la carga de clases del backend. El build normal (`./mvnw verify`)
nunca corre las clases `*E2E`.

## En CI

`.github/workflows/e2e.yml` levanta el mismo stack en `ubuntu-latest`, corre las dos suites y sube como
artefacto `e2e-reports` (informes de failsafe, log del backend, informe y trazas de Playwright). Es un
workflow aparte: no condiciona `build.yml` ni el análisis de Sonar.

## Defecto conocido: el restablecimiento de contraseña no cambia la contraseña

`POST /api/v1/auth/password-resets` responde **204**, pero la contraseña no cambia: la anterior sigue
entrando y la nueva da 401. `GoTrueClient.resetPasswordWithToken` manda la contraseña dentro de
`/verify`, y GoTrue ignora ese campo en ese endpoint.

Las dos suites prueban el comportamiento **correcto** y hoy fallan a propósito:

- API: `AuthJourneyE2E.resetPasswordChangesThePassword`
- UI: `auth.spec.ts`, "a new password set from the recovery email replaces the old one"

No se desactivan ni se reescriben para que pasen. Se ponen en verde cuando se corrija el defecto.

## Comportamientos que las pruebas fijan

- El bloqueo llega en el **sexto** intento: los cinco primeros intentos fallidos dan 401
  `INVALID_CREDENTIALS` y el siguiente da 429 `ACCOUNT_LOCKED` (`retryAfterMinutes` = 15), aunque la
  contraseña sea la correcta.
- El logout responde 204 y revoca el refresh token. El access token sigue valiendo en `/auth/me` hasta
  que vence (ADR-0003).
- Un cambio de rol invalida el token anterior (401). Hay que volver a iniciar sesión después del cambio.
- El proveedor también tiene que verificar su correo antes de iniciar sesión (403 `EMAIL_NOT_CONFIRMED`).
