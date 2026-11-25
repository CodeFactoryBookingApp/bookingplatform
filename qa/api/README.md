# Pruebas de API automatizadas (Sprint 3)

Pruebas funcionales de API de caja negra sobre el flujo de valor del Sprint 3, escritas como colección de Postman (v2.1) y ejecutadas con Newman. Cada petición comprueba el código de estado, el cuerpo (campos y valores; esquema JSON en las principales), el contrato de error del proyecto (`application/problem+json` con `errorCode`, `traceId` y `timestamp`) y que el tiempo de respuesta quede por debajo de 2000 ms.

| Aspecto | Valor |
|---|---|
| Tipo de prueba | Funcional de API (REST), caja negra, extremo a extremo sobre la API |
| Herramienta | Postman (colección v2.1) + Newman 6.2.3 + newman-reporter-htmlextra 1.23.1 |
| Ruta | `qa/api/` (colección, entorno, `run.sh`, simulador de Auth en `stub/`) |
| Ambiente | Local: PostgreSQL 16 en Docker, backend Spring Boot (perfil `cloud`), simulador de Supabase Auth |
| CI | Propuesta de job más abajo (no activada) |

## Contenido

| Archivo | Para qué |
|---|---|
| `bookingplatform-sprint3.postman_collection.json` | Los 40 casos `AT-01 … AT-40`, agrupados por historia de usuario |
| `local.postman_environment.json` | `baseUrl`, `stubUrl`, contraseña de prueba y umbral `maxResponseMs` |
| `run.sh` | Verifica que API y simulador respondan y corre Newman con reportes CLI, htmlextra, JSON y JUnit |
| `stub/gotrue_stub.py` | Simulador de Supabase Auth (GoTrue): firma JWT RS256 y publica JWKS |
| `tools/generar_coleccion.py` | Fuente de la colección; si se cambia un caso, se edita aquí y se regenera el JSON |
| `reports/` | Salida de cada corrida (ignorada por git) |

## Ambiente de pruebas

La API valida los JWT contra el JWKS de Supabase, así que en local se reemplaza Supabase Auth por un simulador. Es una copia extendida del stub de QA del equipo: además de `/token`, `/verify`, `/admin/users` y el JWKS, atiende `GET/PUT /admin/users/{id}` y `GET /admin/users` (los usa el cambio de rol de HU-022) y emite el claim `amr` con la hora de autenticación. Sus endpoints de control, que usan los pre-request de la colección:

- `POST /_ctl/mint` con claims → `{ "token": … }`. Así se obtienen los tokens de ADMIN (`app_metadata.role = ADMIN`) y del dueño del proveedor (su correo, rol PROVIDER).
- `POST /_ctl/confirm` con `{ "email" }` → `token_hash` del enlace de verificación, que la colección envía a `/registrations/email-verifications`.

Los clientes no usan tokens fabricados: se registran, verifican su correo e inician sesión por la API real (`/auth/login`).

Levantar el ambiente (Git Bash, desde la raíz del repo):

```bash
# 1. Base de datos nueva (Flyway aplica V1–V18 al arrancar la app)
docker run -d --name s3qa-api-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=reservas -p 15721:5432 postgres:16

# 2. Simulador de Supabase Auth (requiere: pip install cryptography)
python qa/api/stub/gotrue_stub.py 19921 http://127.0.0.1:19921 &

# 3. Backend (jar construido con ./mvnw -DskipTests package)
export SPRING_PROFILES_ACTIVE=cloud PORT=18321 \
  DATABASE_URL=jdbc:postgresql://localhost:15721/reservas DATABASE_USER=postgres DATABASE_PASSWORD=postgres \
  SUPABASE_URL=http://127.0.0.1:19921 SUPABASE_SECRET_KEY=sb_secret_qa \
  APP_BOOKING_NOTICES_POLL_MS=1000
java -jar target/bookingplatform-0.0.1-SNAPSHOT.jar &
curl -s http://localhost:18321/actuator/health   # {"status":"UP"}
```

`APP_BOOKING_NOTICES_POLL_MS=1000` acorta el intervalo con el que salen los avisos de reserva (15 s por defecto) para que AT-33 encuentre los correos en la bandeja. Si se reinicia el simulador, hay que reiniciar también la app: el simulador genera una clave RSA nueva en cada arranque y la app guarda el JWKS en caché.

## Ejecución y reportes

```bash
cd qa/api
npm ci                 # instala newman y newman-reporter-htmlextra (versiones fijas)
./run.sh 3             # 3 iteraciones; o: npm run test:api
npm run test:api:once  # una iteración, solo CLI + htmlextra
```

Cada iteración genera un `runId` propio (correos, documento e identificación únicos), de modo que la suite se puede repetir sobre la misma base sin limpiar datos. Las fechas se calculan en la zona del proveedor (America/Bogota): la reserva se hace a 3 días, el cierre de agenda a 4 y el caso fuera de política a 45.

`run.sh` deja en `reports/`: `api-report-<fecha>.html` (htmlextra), `api-report-<fecha>.json` y `junit-<fecha>.xml`.

## Casos automatizados

Los casos están encadenados: el setup crea el proveedor, su servicio y agenda, y dos clientes; las variables (`orgId`, `serviceId`, `slot0`, `bookingId`, tokens…) pasan de una petición a la siguiente. En todos se valida además el tiempo `< 2000 ms`.

| ID | Endpoint | Escenario | Validaciones |
|---|---|---|---|
| AT-01 | POST /provider-registrations | Positivo (setup HU-002) | 201, `PENDING_APPROVAL`, ids |
| AT-02 | POST /admin/organizations/{id}/approval | Positivo (setup) | 200, esquema, `ACTIVE`, `approvedAt` |
| AT-03 | PUT /organizations/{id}/operation-policy | Positivo (setup) | 200, anticipación 60 min–30 días, máx. 1 reprogramación, versión nueva |
| AT-04 | POST /organizations/{id}/resource-types | Positivo (setup HU-009) | 201, nombre |
| AT-05 | POST /organizations/{id}/resources | Positivo (setup HU-009) | 201, `AVAILABLE`, capacidad 1 |
| AT-06 | POST /organizations/{id}/services | Positivo (setup HU-004) | 201, `INACTIVE`, requisito de recurso |
| AT-07 | POST /services/{id}/activation | Positivo (setup HU-004) | 200, `ACTIVE` |
| AT-08 | POST /organizations/{id}/agendas | Positivo (setup HU-006) | 201, granularidad 60, 7 bloques, servicio |
| AT-09 | POST /registrations | Positivo (setup HU-001) | 201, `PENDING_VERIFICATION` |
| AT-10 | POST /auth/login | Positivo (setup HU-001/021) | 200, `accessToken` bearer (pre-request verifica el correo) |
| AT-11 | GET /booking/availability | Positivo HU-011 | 200, esquema, 10 franjas de 60 min, 08:00, offset -05:00 |
| AT-12 | GET /organizations/{id}/agendas/{id}/availability | Positivo HU-008 | 200, 7 días, 10 franjas el día objetivo |
| AT-13 | GET /booking/preview | Positivo HU-012 | 200, servicio, precio, ventana 120 min, `termsVersion` |
| AT-14 | POST /bookings | Positivo HU-012 | 201, esquema, `CONFIRMED` (sin confirmación manual) |
| AT-15 | POST /bookings | Franja ocupada (otro cliente) | 409 `SLOT_UNAVAILABLE` |
| AT-16 | POST /bookings | Anticipación fuera de política (45 días) | 4xx, `SLOT_UNAVAILABLE` |
| AT-17 | POST /bookings | Sin token | 401 `AUTH_REQUIRED` |
| AT-18 | POST /bookings | Rol PROVIDER | 403 `ACCESS_DENIED` |
| AT-19 | POST /bookings | Términos desactualizados | 409 `BOOKING_TERMS_CHANGED` |
| AT-20 | POST /bookings/{id}/reschedule | Positivo HU-014 | 200, mismo código, nuevo inicio, `rescheduleCount` 1 |
| AT-21 | POST /bookings/{id}/reschedule | Límite de reprogramaciones | 409 `RESCHEDULE_LIMIT_REACHED`, `details` con límite y acción sugerida |
| AT-22 | POST /bookings | La franja liberada vuelve a estar libre | 201, esquema |
| AT-23 | GET /bookings | Positivo HU-017 | 200, esquema, reserva en próximas, `canReschedule` false, `receiptPath` |
| AT-24 | GET /bookings/{id}/receipt | Positivo HU-017 | 200, `application/pdf`, adjunto `comprobante-<código>.pdf`, firma `%PDF-` y `%%EOF` |
| AT-25 | GET /bookings/{id} | Reserva de otro cliente | 404 `RESOURCE_NOT_FOUND` |
| AT-26 | GET /organizations/{id}/bookings | Positivo HU-018 | 200, esquema, total 2, ambas `CONFIRMED` |
| AT-27 | GET /organizations/{id}/bookings/export | Positivo HU-018 | 200, `text/csv`, encabezado exacto, una fila por reserva |
| AT-28 | POST /organizations/{id}/bookings/{id}/attendance | Asistencia antes del inicio (HU-016) | 409 `INVALID_STATUS_TRANSITION` |
| AT-29 | POST /agendas/{id}/exceptions/closures | Positivo HU-007 | 201, `CLOSED`, fecha |
| AT-30 | GET /booking/availability | Día cerrado (HU-007) | 200, lista vacía |
| AT-31 | POST /bookings/{id}/cancel | Sin confirmar (HU-013) | 409 `CONFIRMATION_REQUIRED`, sin penalidad |
| AT-32 | POST /bookings/{id}/cancel | Positivo HU-013 | 200, `CANCELLED`, `lateCancellation` false |
| AT-33 | GET /notifications/emails | Positivo HU-015 | 200, correos con el código de la reserva |
| AT-34 | GET /organizations/{id}/reports/occupancy | Positivo HU-019 | 200, esquema, 60 franjas (descuenta el día cerrado), 1 ocupada, tasa |
| AT-35 | GET /organizations/{id}/reports/service-demand | Positivo HU-020 | 200, esquema, 2 creadas, 1 cancelada (50 %), ingreso 80 000 COP |
| AT-36 | PATCH /admin/users/{id}/role | Positivo HU-022 | 200, rol `PROVIDER` |
| AT-37 | GET /bookings | Token anterior al cambio de rol | 401, `errorCode` de autenticación |
| AT-38 | GET /auth/me | Nuevo inicio de sesión tras el cambio | 200, rol `PROVIDER` |
| AT-39 | GET /organizations/{id}/audit-events | Positivo HU-023 | 200, esquema, creada/reprogramada/cancelada con actor CLIENT |
| AT-40 | GET /admin/audit-events | Positivo HU-023 | 200, esquema, `PLATFORM_ROLE_CHANGED` con actor ADMIN |

## Ejemplo de script (AAA): AT-15 Reservar franja ya ocupada

Arrange en el pre-request (un segundo cliente se registra, verifica su correo e inicia sesión), Act en la propia petición y Assert en los tests.

```javascript
// Pre-request — Arrange
const base = pm.environment.get("baseUrl"), stub = pm.environment.get("stubUrl");
const email = "qa.cliente.b." + pm.environment.get("runId") + "@example.com";
const json = (url, payload, cb) => pm.sendRequest({ url, method: "POST",
    header: { "Content-Type": "application/json" }, body: { mode: "raw", raw: JSON.stringify(payload) } }, cb);
json(base + "/registrations", { fullName: "Cliente QA B", document: "B" + pm.environment.get("runId"),
    birthDate: "1990-08-20", email, phone: "+573007654321", city: "Medellín", notificationChannel: "EMAIL",
    password: pm.environment.get("password") }, function (e1, r1) {
    pm.environment.set("clientBId", r1.json().clientId);
    json(stub + "/_ctl/confirm", { email }, function (e2, r2) {
        json(base + "/registrations/email-verifications", { tokenHash: r2.json().token_hash }, function () {
            json(base + "/auth/login", { email, password: pm.environment.get("password") }, function (e4, r4) {
                pm.environment.set("clientBToken", r4.json().accessToken);
            });
        });
    });
});
```

```http
### Act
POST {{baseUrl}}/bookings
Authorization: Bearer {{clientBToken}}
Content-Type: application/json

{ "serviceId": "{{serviceId}}", "locationId": "{{locationId}}", "startsAt": "{{slot0}}", "termsVersion": "{{termsVersion}}" }
```

```javascript
// Tests — Assert
pm.test("Código de estado 409", function () {
    pm.response.to.have.status(409);
});
pm.test("Contrato de error: problem+json con errorCode SLOT_UNAVAILABLE", function () {
    pm.expect(pm.response.headers.get("Content-Type")).to.include("application/problem+json");
    const body = pm.response.json();
    pm.expect(body.errorCode).to.eql("SLOT_UNAVAILABLE");
    pm.expect(body.status).to.eql(409);
    pm.expect(body).to.have.property("traceId");
    pm.expect(body).to.have.property("timestamp");
});
pm.test("Tiempo de respuesta < " + pm.environment.get("maxResponseMs") + " ms", function () {
    pm.expect(pm.response.responseTime).to.be.below(Number(pm.environment.get("maxResponseMs")));
});
```

## Resultados (2026-11-24, commit 803b50b de `main`, base nueva, 3 iteraciones)

| Suite | Casos | Ejecuciones | Aserciones | Pasaron | Fallaron | Tiempo promedio (ms) |
|---|---|---|---|---|---|---|
| 00 Preparación | 10 | 30 | 93 | 93 | 0 | 55 |
| 01 HU-008/011 Disponibilidad | 2 | 6 | 21 | 21 | 0 | 25 |
| 02 HU-012 Crear reserva | 7 | 21 | 66 | 66 | 0 | 20 |
| 03 HU-014 Reprogramar | 3 | 9 | 33 | 33 | 0 | 27 |
| 04 HU-017 Historial y PDF | 3 | 9 | 33 | 33 | 0 | 13 |
| 05 HU-018 Búsqueda y CSV | 2 | 6 | 24 | 24 | 0 | 22 |
| 06 HU-016 Asistencia y HU-007 Excepción | 3 | 9 | 27 | 27 | 0 | 20 |
| 07 HU-013 Cancelar | 2 | 6 | 21 | 21 | 0 | 19 |
| 08 HU-015, HU-019, HU-020 | 3 | 9 | 33 | 33 | 0 | 25 |
| 09 HU-022 y HU-023 | 5 | 15 | 51 | 51 | 0 | 22 |
| **Total** | **40** | **120** | **402** | **402** | **0** | **30** (máx. 480) |

Newman cuenta 153 peticiones porque incluye las 33 que hacen los pre-request (tokens, verificación de correo, segundo cliente). El promedio de la tabla es solo de las 120 peticiones bajo prueba.

Para comprobar que las aserciones detectan fallos y no pasan por defecto, se corrió una variante con la política en 2 reprogramaciones: fallaron AT-03, AT-21 y AT-23, como se esperaba.

## Límites conocidos

- HU-016 solo se cubre por el lado excepcional (marcar antes del inicio). Cerrar una reserva como `COMPLETED`/`NO_SHOW` exige que ya haya empezado, y la API no permite crear reservas en el pasado ni el reloj de la app es configurable; ese camino queda en las pruebas de integración.
- HU-020 recibe el periodo como `[from, to)` y HU-019 como `[from, to]` (el front ya lo compensa). No es un fallo, pero conviene recordarlo al leer los reportes con las mismas fechas.

## Propuesta de job de CI (no activada)

```yaml
  api-tests:
    runs-on: ubuntu-latest
    needs: build
    services:
      postgres:
        image: postgres:16
        env: { POSTGRES_PASSWORD: postgres, POSTGRES_DB: reservas }
        ports: ["15721:5432"]
        options: --health-cmd pg_isready --health-interval 5s --health-retries 10
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "21", cache: maven }
      - uses: actions/setup-node@v4
        with: { node-version: "20" }
      - run: ./mvnw -B -DskipTests package
      - run: pip install cryptography && (python qa/api/stub/gotrue_stub.py 19921 http://127.0.0.1:19921 &)
      - name: Arrancar la API
        env:
          SPRING_PROFILES_ACTIVE: cloud
          PORT: "18321"
          DATABASE_URL: jdbc:postgresql://localhost:15721/reservas
          DATABASE_USER: postgres
          DATABASE_PASSWORD: postgres
          SUPABASE_URL: http://127.0.0.1:19921
          SUPABASE_SECRET_KEY: sb_secret_ci
          APP_BOOKING_NOTICES_POLL_MS: "1000"
        run: |
          nohup java -jar target/bookingplatform-0.0.1-SNAPSHOT.jar > app.log 2>&1 &
          for i in $(seq 1 90); do curl -fs localhost:18321/actuator/health && break; sleep 2; done
      - run: cd qa/api && npm ci && npm run test:api:ci
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: api-report, path: "qa/api/reports/" }
```
