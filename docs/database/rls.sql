-- =============================================================
-- Seguridad Supabase — Sprint 1
-- Ejecutar DESPUÉS de que Hibernate cree las tablas (ddl-auto)
-- o junto con schema.sql en un proyecto nuevo.
--
-- Contexto: el anon key de Supabase es público y PostgREST
-- expone el esquema `public`. Sin RLS, cualquier persona con
-- el anon key podría leer clients/login_attempts vía Data API.
-- El backend se conecta por JDBC como dueño de las tablas
-- (rol postgres), que bypasea RLS, por lo que la aplicación
-- sigue funcionando igual.
-- =============================================================

alter table if exists clients enable row level security;
alter table if exists login_attempts enable row level security;

-- Negar explícitamente el acceso Data API a los roles públicos.
-- Con RLS habilitado y sin políticas, tampoco ven filas.
revoke all on clients from anon, authenticated;
revoke all on login_attempts from anon, authenticated;

-- Opcional (Sprint 2+): si se desea que un cliente autenticado
-- lea SU propio perfil directamente vía PostgREST, otorgar y
-- crear política de propiedad (nunca user_metadata en políticas):
--
-- grant select on clients to authenticated;
-- create policy "clients_select_own" on clients
--     for select to authenticated
--     using ( (select auth.uid()) = id );
