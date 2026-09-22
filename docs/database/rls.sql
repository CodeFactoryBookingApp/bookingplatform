-- =============================================================
-- Seguridad Supabase — Row Level Security
-- Ejecutar DESPUÉS de crear las tablas (schema.sql o ddl-auto).
--
-- Contexto: el anon key de Supabase es público y PostgREST
-- expone el esquema `public`. Sin RLS, cualquier persona con
-- el anon key podría leer las tablas vía Data API. El backend
-- se conecta por JDBC como dueño de las tablas (rol postgres),
-- que bypasea RLS, por lo que la aplicación sigue funcionando
-- igual.
--
-- La política del ADR-0002 es RLS en TODAS las tablas de
-- negocio, así que este script cubre las diecinueve. Con RLS
-- habilitado y sin políticas, el modo por defecto es denegar.
--
-- Los bloques DO permiten ejecutarlo también contra un
-- PostgreSQL local, donde los roles `anon` y `authenticated`
-- no existen: allí basta con habilitar RLS y omitir el REVOKE.
-- =============================================================

do $$
declare
    t text;
    tablas text[] := array[
        'cities', 'organization_categories', 'currencies',
        'clients', 'login_attempts',
        'organizations', 'organization_members', 'organization_policies', 'locations',
        'services', 'service_locations', 'resource_types',
        'service_resource_requirements', 'resources', 'schedules',
        'bookings', 'booking_resources', 'booking_status_changes',
        'account_status_changes'
    ];
    hay_roles boolean := exists (select 1 from pg_roles where rolname = 'anon');
begin
    foreach t in array tablas loop
        if to_regclass('public.' || t) is not null then
            execute format('alter table public.%I enable row level security', t);
            if hay_roles then
                execute format('revoke all on public.%I from anon, authenticated', t);
            end if;
        end if;
    end loop;

    if hay_roles then
        raise notice 'RLS habilitado y acceso Data API revocado en % tablas', array_length(tablas, 1);
    else
        raise notice 'RLS habilitado en % tablas. Los roles anon/authenticated no existen en esta base (PostgreSQL local): se omite el REVOKE', array_length(tablas, 1);
    end if;
end $$;


-- Catálogos de lectura pública (opcional, Sprint 2+).
-- Ciudades, categorías y monedas no son datos sensibles: si el
-- frontend los necesita para poblar listas desplegables sin pasar
-- por el backend, se otorgan con una política de solo lectura.
--
-- grant select on cities, organization_categories, currencies to anon, authenticated;
-- create policy "cities_select_all" on cities for select to anon, authenticated using (true);


-- Propiedad del propio perfil (opcional, Sprint 2+).
-- Para que un cliente autenticado lea SU perfil vía PostgREST.
-- Nunca usar user_metadata en una política: es editable por el usuario.
--
-- grant select on clients to authenticated;
-- create policy "clients_select_own" on clients
--     for select to authenticated
--     using ( (select auth.uid()) = id );


-- Reservas del propio cliente (opcional, Sprint 2+).
--
-- grant select on bookings to authenticated;
-- create policy "bookings_select_own" on bookings
--     for select to authenticated
--     using ( (select auth.uid()) = client_id );
