-- =============================================================
-- Callback de Flyway: corre al final de cada `migrate`, cuando
-- ya soltó el bloqueo de su tabla de historial.
--
-- Flyway crea `flyway_schema_history` en `public`, y en Supabase
-- toda tabla nueva de `public` hereda permisos para anon y
-- authenticated: sin esto, cualquiera con el anon key podría leer
-- o borrar el historial de migraciones por el Data API (ADR-0002).
--
-- Corre en cada arranque, así que tiene que ser idempotente. Como
-- en V2, en un PostgreSQL local sin los roles de Supabase solo se
-- habilita RLS.
-- =============================================================

alter table if exists flyway_schema_history enable row level security;

do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon')
       and to_regclass('public.flyway_schema_history') is not null then
        revoke all on public.flyway_schema_history from anon, authenticated;
    end if;
end $$;
