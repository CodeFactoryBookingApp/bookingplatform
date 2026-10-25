-- =====================================================================
--  V7: recursos con capacidad, estado y mantenimientos (HU-009)
--
--  HU-009: "cada recurso pertenece a una sede, tiene una capacidad
--  simultánea y un estado (disponible, en mantenimiento, retirado)", y
--  "un recurso en mantenimiento en un rango de fechas no genera
--  disponibilidad".
--
--  · capacity: cuántas personas atiende el recurso a la vez (un salón
--    para 12, una silla para 1). No cambia el EXCLUDE de
--    booking_resources: un recurso sigue sirviendo a una sola sesión a
--    la vez; la capacidad dice cuánta gente cabe en esa sesión.
--  · status: ACTIVE o RETIRED. "En mantenimiento" no es un estado fijo
--    sino un periodo, así que va en su propia tabla y se deduce de la
--    fecha. INACTIVE, que no decía si era temporal o definitivo, pasa a
--    RETIRED.
--  · organization_id: hasta ahora nada impedía que el tipo de recurso
--    fuera de un proveedor y la sede de otro. Con la organización en la
--    fila, las dos claves foráneas compuestas lo impiden.
-- =====================================================================

alter table resources add column capacity integer not null default 1;
alter table resources add constraint ck_resources_capacity check (capacity >= 1);

alter table resources drop constraint ck_resources_status;
update resources set status = 'RETIRED' where status = 'INACTIVE';
alter table resources add constraint ck_resources_status check (status in ('ACTIVE', 'RETIRED'));

alter table resources add column organization_id uuid;
update resources r
   set organization_id = t.organization_id
  from resource_types t
 where t.id = r.resource_type_id;
alter table resources alter column organization_id set not null;

alter table resources
    add constraint fk_resources_type_org
        foreign key (resource_type_id, organization_id) references resource_types (id, organization_id) on delete restrict;
alter table resources
    add constraint fk_resources_location_org
        foreign key (location_id, organization_id) references locations (id, organization_id) on delete restrict;

create index ix_resources_organization on resources (organization_id);

comment on column resources.capacity        is 'Personas que el recurso atiende a la vez (HU-009)';
comment on column resources.status          is 'ACTIVE o RETIRED. El mantenimiento es un periodo: resource_maintenances';
comment on column resources.organization_id is 'Proveedor dueño. Las CF compuestas exigen que tipo y sede sean del mismo';


-- Periodos de mantenimiento: el recurso no ofrece franjas mientras dura uno
create table resource_maintenances (
    id          uuid         primary key default gen_random_uuid(),
    resource_id uuid         not null,
    starts_at   timestamptz  not null,
    ends_at     timestamptz  not null,
    reason      varchar(300),
    created_at  timestamptz  not null default now(),

    constraint fk_resource_maintenances_resource foreign key (resource_id) references resources (id) on delete cascade,
    constraint ck_resource_maintenances_rango check (ends_at > starts_at),
    -- Dos mantenimientos del mismo recurso no se solapan
    constraint ex_resource_maintenances_solape exclude using gist (
        resource_id with =,
        tstzrange(starts_at, ends_at) with &&
    )
);

comment on table resource_maintenances is 'Periodos en que un recurso está en mantenimiento y no genera disponibilidad (HU-009)';

alter table resource_maintenances enable row level security;
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on public.resource_maintenances from anon, authenticated;
    end if;
end $$;
