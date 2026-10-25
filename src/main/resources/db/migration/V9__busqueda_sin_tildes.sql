-- =====================================================================
--  V9: búsqueda sin distinguir mayúsculas ni tildes (HU-005)
--
--  HU-005: "veo los servicios cuyo nombre, descripción o proveedor
--  coinciden, sin distinguir mayúsculas ni tildes".
--
--  No se usa la extensión unaccent: en Supabase se instala en el esquema
--  `extensions` y en un PostgreSQL normal en `public`, así que la
--  consulta dependería del search_path de cada base. Una función propia
--  con translate() cubre el español y se comporta igual en todas.
--  Es IMMUTABLE, así que más adelante puede indexarse si hace falta.
-- =====================================================================

create or replace function f_sin_tildes(texto text) returns text
language sql immutable strict parallel safe as $$
    select translate(lower(texto),
                     'áàäâãéèëêíìïîóòöôõúùüûñç',
                     'aaaaaeeeeiiiiooooouuuunc')
$$;

comment on function f_sin_tildes(text) is 'Minúsculas y sin tildes, para comparar texto como lo escribe la gente (HU-005)';

-- Supabase publica las funciones de public como RPC del Data API. Esta no
-- expone datos, pero tampoco tiene por qué ser invocable desde fuera.
revoke all on function f_sin_tildes(text) from public;
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on function public.f_sin_tildes(text) from anon, authenticated;
    end if;
end $$;
