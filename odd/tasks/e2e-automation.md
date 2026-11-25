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

## Tasks

- T1 S2,S3 — Local stack: `supabase/` config with asymmetric signing key, backend wired to it, scripts to start/stop; spike proves login JWT validates via JWKS. route: inline. commit: —
- T2 S1,S4-S7 — API e2e suite (Java, failsafe profile `e2e`, Mailpit helper, admin bootstrap). route: inline. commit: —
- T3 S1,S4-S7 — Playwright UI suite in `front/e2e/` (fixtures, Mailpit helper, role logins). route: inline. commit: —
- T4 S8 — `.github/workflows/e2e.yml` + docs (runbook, front README, docs/qa). route: inline. commit: —

## Log

- L1 User: "En esta rama hay que implementar los test e2e de la app (automation test)"
- L2 Decisions: Ambos; Stack local; Scope = cliente, proveedor, admin, auth; CI workflow aparte; Identity = Supabase CLI local.
- L3 Evidence: Docker Desktop not reachable from WSL (no /var/run/docker.sock, npipe engine missing); supabase CLI 2.101.0 installed. Local runs blocked until Docker is up with WSL integration.
- L4 Evidence T1: Supabase CLI local signs RS256 (kid in /auth/v1/.well-known/jwks.json), issuer http://127.0.0.1:54321/auth/v1 matches SUPABASE_URL/auth/v1; sb_secret key works on GoTrue admin API. Full curl scenario passed: client register -> Mailpit "Confirm Your Email" link token -> /registrations/email-verifications -> login -> /me CLIENT; provider registration needs email verification too (403 EMAIL_NOT_CONFIRMED before); admin = GoTrue admin user with app_metadata.role ADMIN; approval -> resource type -> resource -> service -> activation -> agenda -> catalog lists it; availability needs auth; booking CONFIRMED, duplicate 409 SLOT_UNAVAILABLE, cancel CANCELLED; lockout: 5th wrong password -> 429 ACCOUNT_LOCKED retryAfterMinutes 15; logout 204 but access token still 200 on /me until expiry (ADR-0003).
- L5 DEFECT found by e2e spike: POST /api/v1/auth/password-resets returns 204 but password is NOT changed (old password still 200, new one 401). GoTrueClient.resetPasswordWithToken sends password inside /verify, which GoTrue ignores. Not in docs/qa. Reused token -> 400 VERIFICATION_TOKEN_INVALID.
