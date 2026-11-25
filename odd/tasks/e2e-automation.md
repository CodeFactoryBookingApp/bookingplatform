# e2e-automation

Automated end-to-end tests for the booking platform: Playwright UI suite over `front/` and an API e2e suite,
both against a local stack (Supabase CLI: GoTrue + Postgres + Mailpit, backend on :8080, Vite on :3000), plus a separate CI workflow.
Branch: `feature/qa-unit-tests`.

## Specs

- S1 Type: "Ambos" — UI e2e with Playwright and an API e2e suite.
- S2 Environment: "Stack local (Recomendado)" — backend + Vite on :3000 locally, reproducible, CI-ready.
- S3 Identity: "Supabase CLI local (Recomendado)" — real GoTrue + Mailpit in Docker; first validate asymmetric JWKS support.
- S4 Scope "Cliente: registro, login, catálogo y reserva" — client happy path including My bookings and cancellation.
- S5 Scope "Proveedor: alta, servicios, recursos, agenda" — provider onboarding and catalog setup.
- S6 Scope "Admin: aprobar proveedores, usuarios, auditoría" — admin panel.
- S7 Scope "Auth: recuperación, bloqueo, logout" — authentication cases and their errors.
- S8 CI: "Sí, workflow aparte" — new `e2e.yml` workflow; does not gate `build.yml`.
- S9 Defect L5 (password reset does not change the password): "Test del comportamiento correcto, en rojo (Recomendado)" — the test asserts the new password logs in and the old one is rejected; it fails until the defect is fixed. Do not fix the defect, do not disable the test.

## Tasks

- T1 S2,S3 — Local stack: `supabase/` config with asymmetric signing key, backend wired to it, scripts to start/stop; spike proves login JWT validates via JWKS. route: inline. commit: d4c0d3d
- T2 S1,S4-S7,S9 — API e2e suite (Java, failsafe profile `e2e`, Mailpit helper, admin bootstrap). route: worker (context backstop, parallel with T3). commit: 415a987
- T3 S1,S4-S7,S9 — Playwright UI suite in `front/e2e/` (fixtures, Mailpit helper, role logins). route: worker (parallel with T2). commit: 809b855
- T4 S8 — `.github/workflows/e2e.yml` + docs (runbook, front README, docs/qa). route: inline. commit: "ci(e2e): separate workflow for the end-to-end suites, with docs"

## Log

- L1 User: "En esta rama hay que implementar los test e2e de la app (automation test)"
- L2 Decisions: Ambos; Stack local; Scope = cliente, proveedor, admin, auth; CI workflow aparte; Identity = Supabase CLI local.
- L3 Evidence: Docker Desktop not reachable from WSL (no /var/run/docker.sock, npipe engine missing); supabase CLI 2.101.0 installed. Local runs blocked until Docker is up with WSL integration.
- L4 Evidence T1: Supabase CLI local signs RS256 (kid in /auth/v1/.well-known/jwks.json), issuer http://127.0.0.1:54321/auth/v1 matches SUPABASE_URL/auth/v1; sb_secret key works on GoTrue admin API. Full curl scenario passed: client register -> Mailpit "Confirm Your Email" link token -> /registrations/email-verifications -> login -> /me CLIENT; provider registration needs email verification too (403 EMAIL_NOT_CONFIRMED before); admin = GoTrue admin user with app_metadata.role ADMIN; approval -> resource type -> resource -> service -> activation -> agenda -> catalog lists it; availability needs auth; booking CONFIRMED, duplicate 409 SLOT_UNAVAILABLE, cancel CANCELLED; lockout: 5th wrong password -> 429 ACCOUNT_LOCKED retryAfterMinutes 15; logout 204 but access token still 200 on /me until expiry (ADR-0003).
- L5 DEFECT found by e2e spike: POST /api/v1/auth/password-resets returns 204 but password is NOT changed (old password still 200, new one 401). GoTrueClient.resetPasswordWithToken sends password inside /verify, which GoTrue ignores. Not in docs/qa. Reused token -> 400 VERIFICATION_TOKEN_INVALID.
- L6 User decision on L5: "Test del comportamiento correcto, en rojo (Recomendado)".
- L7 Verified contracts (curl against the local stack, use as the source of truth):
  - Stack: `scripts/e2e/stack.sh up|down|env`; env prints SUPABASE_URL=http://127.0.0.1:54321, SUPABASE_SECRET_KEY, MAILPIT_URL=http://127.0.0.1:54324, E2E_API_URL=http://localhost:8080. Suites read these env vars with those defaults (secret key has no safe default: read it from env; fail fast with a clear message if missing).
  - Mailpit: GET $MAILPIT_URL/api/v1/search?query=to:<email> -> {messages:[{ID,Subject,...}]} newest first; GET /api/v1/message/<ID> -> {Text,...}. Subjects: "Confirm Your Email" (signup), "Reset Your Password" (recovery). Link: http://127.0.0.1:54321/auth/v1/verify?token=<hex>&type=signup|recovery&redirect_to=http://localhost:3000 ; the `token` value is the tokenHash the API takes. Emails arrive within ~1s; poll up to ~15s.
  - Admin bootstrap: POST $SUPABASE_URL/auth/v1/admin/users headers apikey+Authorization Bearer <secret>, body {email,password,email_confirm:true,app_metadata:{role:"ADMIN"}}; then normal POST /api/v1/auth/login.
  - Client: POST /api/v1/registrations {fullName,document,birthDate:"1990-01-01",email,phone:"3001234567",city:"Bogotá",notificationChannel:"EMAIL",password:"Xxxx123!x"} -> 201 status PENDING_VERIFICATION; login before verifying -> 403 errorCode EMAIL_NOT_CONFIRMED; POST /api/v1/registrations/email-verifications {tokenHash} -> 200 status ACTIVE; POST /api/v1/auth/login {email,password} -> {accessToken,refreshToken,tokenType,expiresIn}; GET /api/v1/auth/me -> {id,email,role:"CLIENT"|"PROVIDER"|"ADMIN"}.
  - Reference data: GET /api/v1/organization-categories and GET /api/v1/cities -> arrays with `id`.
  - Provider: POST /api/v1/provider-registrations {email,password,name,categoryId,identificationType:"NIT",identificationNumber (5-20 [A-Za-z0-9-]),contactPhone:"3001112233",locations:[{name,address,cityId}],bookingNoticeMinutesMin:0,bookingNoticeMinutesMax:43200,freeCancellationWindowMinutes:60} -> {organizationId,ownerUserId,email,status:"PENDING_APPROVAL"}; sends "Confirm Your Email"; verify with the same /registrations/email-verifications (200, status null); /me role PROVIDER.
  - Admin: POST /api/v1/admin/organizations/{orgId}/approval {reason} -> status ACTIVE; GET /api/v1/admin/organizations?status=ACTIVE -> {items,total,page,size}; GET /api/v1/admin/clients -> items with email,status; GET /api/v1/admin/audit-events?size=N -> {items:[{action,actorRole,organizationId,resourceType,...}]} (seen actions: BOOKING_CANCEL..., BOOKING_RESOURCE_RELEASED); PATCH /api/v1/admin/users/{id}/role {role}.
  - Provider setup (Bearer provider): GET /api/v1/organizations/{orgId} -> locations[0].id; POST .../resource-types {name,description}; POST .../resources {resourceTypeId,locationId,name,capacity:1}; POST .../services {details:{name,description,durationMinutes:60,price:80000},locationIds:[loc],requirements:[{resourceTypeId,quantity:1}]} -> status INACTIVE; POST .../services/{id}/activation -> ACTIVE; POST .../agendas {name,locationId,slotMinutes:60,validFrom:today,serviceIds:[svc],blocks:[{dayOfWeek:"MONDAY".."SUNDAY",startTime:"08:00",endTime:"18:00"}]}; then GET /api/v1/catalog/services?q=<name> lists it (public).
  - Booking (Bearer client): GET /api/v1/booking/availability?serviceId&locationId&date=YYYY-MM-DD (401 without auth) -> [{startsAt,endsAt}] (offset -05:00); GET /api/v1/booking/preview?serviceId&locationId&startsAt -> {termsVersion,freeCancellationWindowMinutes,price,...}; POST /api/v1/bookings {serviceId,locationId,startsAt,termsVersion} -> {id,code,status:"CONFIRMED"}; same slot again -> 409 SLOT_UNAVAILABLE; GET /api/v1/bookings -> {upcoming:[{id,code,status,serviceName,...}],history:[...]}; POST /api/v1/bookings/{id}/cancel {confirm:true,reason} -> status CANCELLED, lateCancellation false (book >= 1 day ahead).
  - Auth: POST /api/v1/auth/password-recovery-requests {email} -> 202; POST /api/v1/auth/password-resets {tokenHash,newPassword} -> 204 (DEFECT L5); reused token -> 400 VERIFICATION_TOKEN_INVALID; wrong password -> 401 INVALID_CREDENTIALS; the 5th consecutive wrong password -> 429 ACCOUNT_LOCKED with details.retryAfterMinutes "15", and the correct password is then also 429; POST /api/v1/auth/logout -> 204, the access token still returns 200 on /me until it expires (ADR-0003, logout revokes the refresh token).
  - Isolation: every test creates its own users with unique emails (e.g. <prefix>-<uuid>@e2e.test); never depend on data from another test.
- L8 Correction to L4/L7 (workers T2/T3): lockout is 5 wrong passwords -> 401 each, the 6th attempt -> 429 ACCOUNT_LOCKED (LoginUseCase checks the lock before recording); the L4 "5th -> 429" counted an earlier 403 login. tokenType is "bearer". Cancelled bookings move to `history`. Role change invalidates the old token (401) and needs a login strictly after the change (1 s precision). REST Assured 5.5 needs Groovy 4 (Spring Boot 4.1 manages Groovy 5): groovy.version pinned to 4.0.22. Profile e2e writes its jar to target/e2e-jar and skips repackage so `-Pe2e verify` never rewrites the jar the stack runs.
- L9 Verification (parent, stack on :8080): `./mvnw -Pe2e verify` 15 run, 14 pass, only AuthJourneyE2E.resetPasswordChangesThePassword fails ("expected: <200> but was: <401>", defect L5). `cd front && npx playwright test` 7 run, 6 pass, only auth.spec.ts "a new password set from the recovery email replaces the old one" fails (defect L5). Default build: 892 tests, 0 failures (worker T2).
- L10 T4: .github/workflows/e2e.yml (push main + feature/qa-unit-tests, PRs, manual; Supabase CLI 2.101.0 pinned; runs both suites, uploads e2e-reports, stack down always). Docs: docs/qa/e2e.md, README table, runbook §5 pointer, front/README. Verified: YAML parses, `npm run e2e` exists, `stack.sh env` works from front/. Not verified: an actual GitHub Actions run (needs push, user's decision).
