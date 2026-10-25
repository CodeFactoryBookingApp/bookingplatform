-- =====================================================================
--  V8: agendas (HU-006)
--
--  HU-006: "Cada agenda se asocia a una sede y opcionalmente a un
--  recurso, define bloques horarios por día, la granularidad de las
--  franjas (15, 30 o 60 minutos), su vigencia y los servicios
--  reservables en ella."
--
--  Hasta ahora schedules eran franjas semanales sueltas por sede o
--  recurso, sin agenda que las agrupara. Pasan a ser los bloques de una
--  agenda: la sede y el recurso salen de la agenda, y las filas que
--  hubiera se borran porque no tienen a cuál pertenecer. Ningún código
--  escribía en schedules; sólo seed.sql, que se actualiza.
-- =====================================================================

create table agendas (
    id              uuid         primary key default gen_random_uuid(),
    organization_id uuid         not null,
    location_id     uuid         not null,
    resource_id     uuid,                       -- nulo = toda la sede
    name            varchar(120) not null,
    slot_minutes    smallint     not null,
    valid_from      date         not null,
    valid_until     date,                       -- nulo = sin fecha de fin
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),

    constraint uk_agendas_org_name    unique (organization_id, name),
    constraint uk_agendas_id_location unique (id, location_id),   -- clave alterna para CF compuesta
    constraint fk_agendas_location_org foreign key (location_id, organization_id)
        references locations (id, organization_id) on delete restrict,
    -- El recurso tiene que estar en la sede de la agenda
    constraint fk_agendas_resource foreign key (resource_id, location_id)
        references resources (id, location_id) on delete restrict,

    constraint ck_agendas_slot     check (slot_minutes in (15, 30, 60)),
    constraint ck_agendas_vigencia check (valid_until is null or valid_until >= valid_from),
    constraint ck_agendas_name     check (length(btrim(name)) > 0),

    -- Una sede, o un recurso, no tiene dos agendas vigentes a la vez:
    -- sumarían sus bloques y duplicarían la disponibilidad.
    constraint ex_agendas_solape exclude using gist (
        location_id with =,
        coalesce(resource_id, '00000000-0000-0000-0000-000000000000'::uuid) with =,
        daterange(valid_from, valid_until, '[]') with &&
    )
);

-- Servicios reservables en cada agenda. La CF contra service_locations
-- exige que el servicio se preste en la sede de la agenda (HU-004).
create table agenda_services (
    agenda_id   uuid not null,
    service_id  uuid not null,
    location_id uuid not null,
    constraint pk_agenda_services         primary key (agenda_id, service_id),
    constraint fk_agenda_services_agenda  foreign key (agenda_id, location_id)
        references agendas (id, location_id) on delete cascade,
    constraint fk_agenda_services_service foreign key (service_id, location_id)
        references service_locations (service_id, location_id) on delete cascade
);

create index ix_agenda_services_service on agenda_services (service_id);
create index ix_agendas_resource        on agendas (resource_id);

drop trigger if exists tg_agendas_updated_at on agendas;
create trigger tg_agendas_updated_at before update on agendas for each row execute function fn_set_updated_at();


-- schedules: de franjas sueltas a bloques de una agenda
delete from schedules;
alter table schedules drop column location_id;   -- arrastra su CF, su unique, el EXCLUDE y sus índices
alter table schedules drop column resource_id;
alter table schedules add column agenda_id uuid not null;
alter table schedules
    add constraint fk_schedules_agenda foreign key (agenda_id) references agendas (id) on delete cascade;
-- HU-006 "Bloques solapados en el mismo día": dos bloques activos de una agenda no se solapan
alter table schedules
    add constraint ex_schedules_solape exclude using gist (
        agenda_id with =,
        day_of_week with =,
        timerange(start_time, end_time) with &&
    ) where (active);

comment on table  agendas          is 'Agenda de una sede o de un recurso: granularidad, vigencia y servicios (HU-006)';
comment on column agendas.slot_minutes is 'Granularidad de las franjas: 15, 30 o 60 minutos (HU-006)';
comment on table  agenda_services  is 'Servicios reservables en una agenda; deben prestarse en su sede (HU-006)';
comment on table  schedules        is 'Bloques horarios semanales de una agenda (HU-006)';

alter table agendas         enable row level security;
alter table agenda_services enable row level security;
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on public.agendas, public.agenda_services from anon, authenticated;
    end if;
end $$;
