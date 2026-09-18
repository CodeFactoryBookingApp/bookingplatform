# Booking Platform — Sprint 1

Backend de la **Plataforma de Reservas de Servicios** (caso CodeF@ctory).
Monolito modular con arquitectura limpia sobre Spring Boot 4.1.1 (JDK 21),
PostgreSQL administrado en **Supabase** y autenticación delegada en Supabase
Auth (GoTrue) con validación JWT por JWKS.

**Alcance Sprint 1:** HU-001 (registro de cliente con verificación de correo)
y HU-021 mínima (login, logout, bloqueo por intentos, recuperación de
contraseña). MFA de administradores diferido a Sprint 2.

## Documentación

| Documento | Ruta |
|---|---|
| Diagrama de paquetes y componentes + estilo | `docs/architecture/componentes.md` |
| ADR-0001 Monolito modular + clean architecture | `docs/adr/ADR-0001-*.md` |
| ADR-0002 Supabase (BD + identidad) | `docs/adr/ADR-0002-*.md` |
| ADR-0003 Autenticación JWT/JWKS + bloqueo propio | `docs/adr/ADR-0003-*.md` |
| ADR-0004 API versionada, ProblemDetail, traceId | `docs/adr/ADR-0004-*.md` |
| Modelo lógico completo (ER) | `docs/database/modelo-logico.md` |
| Modelo físico inicial (DDL) | `docs/database/schema.sql` |
| Seguridad Supabase (RLS) | `docs/database/rls.sql` |
| Consultas clave del negocio | `docs/database/consultas-clave.md` |
| Runbook operativo (producción, E2E, troubleshooting) | `docs/operations/runbook.md` |
| Colección REST de Sprint 1 | `docs/api/sprint1.http` |

## Estructura del código

```
com.codefactory.bookingplatform
├── shared/         kernel transversal: error (ProblemDetail+traceId), config
│                   (security, OpenAPI, properties), observability, persistence
├── identity/       HU-001: domain (Client puro + ports) → application (use cases)
│                   → infrastructure (JPA + MapStruct) → api (REST /registrations)
└── auth/           HU-021: domain (policies + ports) → application (fachada
                    UserProvisioning + use cases) → infrastructure (GoTrueClient,
                    login_attempts) → api (REST /auth)
```

Reglas de dependencia verificadas con ArchUnit en `src/test/.../architecture/ArchitectureTest.java`.

## Requisitos

- JDK 21+, Maven wrapper (`mvnw`) incluido
- Docker (solo para correr los tests de integración con Testcontainers)
- Proyecto Supabase gratuito (BD + Auth)

## Variables de entorno

La aplicación **solo** lee configuración del entorno del proceso (variables de
entorno del SO, secret store de Render, etc.). 

Configura estas variables en tu entorno local:

| Variable | Valor de ejemplo (reemplazar) | Descripción |
|---|---|---|
| `SUPABASE_URL` | `https://<ref>.supabase.co` | URL del proyecto (Dashboard → Settings → API Keys) |
| `SUPABASE_SECRET_KEY` | `sb_secret_...` | Secret key, **solo backend**, jamás en frontend ni git |
| `DATABASE_URL` | `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require` | JDBC por session pooler, puerto **5432** (nunca 6543) |
| `DATABASE_USER` | `postgres.<ref>` | Usuario del pooler (con sufijo del proyecto) |
| `DATABASE_PASSWORD` | `...` | Contraseña de la BD (Dashboard → Settings → Database) |
| `SPRING_PROFILES_ACTIVE` | `cloud` | Solo en despliegue (`ddl-auto=validate`); en local se omite (`update`) |

> En Windows (PowerShell) para una prueba local sin persistir nada:
> `$env:DATABASE_URL="..."; $env:DATABASE_USER="..."; ...; .\mvnw.cmd spring-boot:run`
> Las variables viven solo en esa sesión de terminal.

Tras el primer arranque ejecutar `docs/database/rls.sql` en el SQL Editor.

## Ejecución local

1. Copia `.env.example` a `.env` y completa tus credenciales de Supabase.
2. Arranca (el script carga el `.env` y corre la app):

```powershell
.\run-local.ps1    # Windows
./run-local.sh     # Linux / macOS
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

## Endpoints (v1)

| Método | Ruta | Auth | HU |
|---|---|---|---|
| POST | `/api/v1/registrations` | pública | HU-001 |
| POST | `/api/v1/registrations/verification-resends` | pública | HU-001 |
| POST | `/api/v1/registrations/email-verifications` | pública | HU-001 |
| POST | `/api/v1/auth/login` | pública | HU-021 |
| POST | `/api/v1/auth/logout` | Bearer | HU-021 |
| POST | `/api/v1/auth/password-recovery-requests` | pública | HU-021 |
| POST | `/api/v1/auth/password-resets` | pública | HU-021 |
| GET | `/api/v1/auth/me` | Bearer | HU-021 |

Errores: `application/problem+json` con `errorCode`, `traceId` y `details`.

## Tests

```bash
mvnw test          # unitarios de dominio + ArchUnit
mvnw verify        # + integración con Testcontainers (requiere Docker)
```

## Despliegue en Render

Producción: `https://bookingplatform-81wi.onrender.com`
(Web Service Docker desde `main`, perfil `cloud`, `ddl-auto=validate`).

### Crear el servicio

1. En Render: **New → Web Service → Build from Git provider** y conecta el
   repositorio (`render.yaml` ya describe el servicio).
2. En **Settings → Health Check Path** configura
   `/actuator/health/readiness`.
3. En **Environment** define las mismas variables de la tabla anterior
   (perfil `cloud`; `DATABASE_URL` en formato JDBC con `?sslmode=require`).
   Sin Secret Files: la app solo lee variables de entorno.

### Verificar

```bash
curl https://bookingplatform-81wi.onrender.com/actuator/health   # {"status":"UP"}
```

Swagger UI: `/swagger-ui/index.html` · OpenAPI: `/v3/api-docs`.

> Plan Free: la instancia se duerme tras ~15 min sin tráfico; la primera
> petición tarda ~1 min (cold start).

### Restricciones aplicadas (no tocar sin revisar el runbook)

- Render inyecta el puerto en `PORT` → respetar `server.port=${PORT:8080}`.
- El session pooler gratuito de Supabase admite **15 clientes totales**:
  pool Hikari fijo en 5/1 (`application-cloud.yml`) y ninguna otra conexión
  permanente contra Supabase.

Detalle operativo, E2E manual y troubleshooting:
`docs/operations/runbook.md`.

## Convenciones de código

- Clases/interfaces/records/enums: **PascalCase**; métodos y variables: **camelCase**; paquetes: minúsculas.
- Sufijos: `*UseCase` (application), `*Entity` (JPA, infrastructure), `*Request`/`*Response` (DTO api), `*Port`/`*Repository` (domain), `*Adapter`/`*Client` (infrastructure).
- DTOs de entrada/salida como `record` con Bean Validation.
- Dominio sin frameworks (regla verificada por ArchUnit).
- Ramas `feature/*`, PR a `main` con revisión por pares (trunk-based).
