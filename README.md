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
| ADR-0005 Modelo físico escrito a mano + CI de BD | `docs/adr/ADR-0005-*.md` |
| Guía de la carpeta de base de datos | `docs/database/README.md` |
| Modelo lógico completo (ER) | `docs/database/modelo-logico.md` |
| Modelo físico (DDL, 19 tablas) | `docs/database/schema.sql` |
| Datos de prueba | `docs/database/seed.sql` |
| Restricciones de integridad en acción | `docs/database/pruebas-integridad.sql` |
| Seguridad Supabase (RLS) | `docs/database/rls.sql` |
| Consultas clave del negocio | `docs/database/consultas-clave.md` |
| Consultas clave, versión ejecutable | `docs/database/consultas-clave.sql` |
| Diagrama ER editable | `docs/database/diagrama-er.drawio` |
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

## Cómo ejecutar el proyecto

### Prerrequisitos

- JDK 21+ (el Maven wrapper `mvnw` ya viene incluido, no hay que instalar Maven)
- Docker Desktop (solo para los tests de integración con Testcontainers)
- Proyecto Supabase gratuito (BD + Auth)

Notas:

- No hay base de datos local que levantar: la app conecta directo al
  PostgreSQL administrado de Supabase por el session pooler (puerto 5432).
- La aplicación **solo** lee configuración del entorno del proceso
  (variables de entorno del SO, secret store de Render, etc.).

### 1) Configurar variables de entorno

| Variable | Valor de ejemplo (reemplazar) | Descripción |
|---|---|---|
| `SUPABASE_URL` | `https://<ref>.supabase.co` | URL del proyecto (Dashboard → Settings → API Keys) |
| `SUPABASE_SECRET_KEY` | `sb_secret_...` | Secret key, **solo backend**, jamás en frontend ni git |
| `DATABASE_URL` | `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require` | JDBC por session pooler, puerto **5432** (nunca 6543) |
| `DATABASE_USER` | `postgres.<ref>` | Usuario del pooler (con sufijo del proyecto) |
| `DATABASE_PASSWORD` | `...` | Contraseña de la BD (Dashboard → Settings → Database) |

La app **no lee archivos `.env` por sí sola**: las variables deben existir
en el entorno de la terminal donde corres `mvnw`. Cada terminal tiene su
propio entorno, así que cárgalas en la misma sesión antes de arrancar.

**Opción A — una por una (nada persiste, solo esa sesión):**

Windows:

```powershell
$env:SUPABASE_URL="https://<ref>.supabase.co"
$env:SUPABASE_SECRET_KEY="sb_secret_..."
$env:DATABASE_URL="jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require"
$env:DATABASE_USER="postgres.<ref>"
$env:DATABASE_PASSWORD="..."
```

Linux/macOS:

```bash
export SUPABASE_URL="https://<ref>.supabase.co"
export SUPABASE_SECRET_KEY="sb_secret_..."
export DATABASE_URL="jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require"
export DATABASE_USER="postgres.<ref>"
export DATABASE_PASSWORD="..."
```

**Opción B — desde un archivo `.env` (recomendado):**

Crea un archivo `.env` en la raíz del repo con el formato `CLAVE=valor`,
una por línea (ese archivo ya está ignorado en git, nunca se commitea):

```
SUPABASE_URL=https://<ref>.supabase.co
SUPABASE_SECRET_KEY=sb_secret_...
DATABASE_URL=jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
DATABASE_USER=postgres.<ref>
DATABASE_PASSWORD=...
```

Y cárgalo en la misma terminal antes de arrancar:

Windows:

```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[^#\s=]+=' } | ForEach-Object { $k,$v = $_ -split '=', 2; [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim().Trim('"').Trim("'"), 'Process') }
```

Linux/macOS:

```bash
set -a; source .env; set +a
```

Si algo falla al arrancar, verifica que cargaron (p. ej. `$env:DATABASE_URL`
en Windows o `echo $DATABASE_URL` en Linux/macOS).

### 2) Levantar la API

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
./mvnw spring-boot:run
```

Notas:

- El esquema completo vive en `docs/database/schema.sql` y se aplica con
  `psql -f` o desde el SQL Editor de Supabase (ADR-0005). `ddl-auto=update`
  sigue creando en local las tablas que tienen entidad JPA. Después
  ejecuta `docs/database/rls.sql`.
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

### 3) Ejecutar pruebas

Windows:

```powershell
.\mvnw.cmd test      # unitarios de dominio + ArchUnit
.\mvnw.cmd verify    # + integración con Testcontainers (requiere Docker)
```

Linux/macOS:

```bash
./mvnw test          # unitarios de dominio + ArchUnit
./mvnw verify        # + integración con Testcontainers (requiere Docker)
```

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
