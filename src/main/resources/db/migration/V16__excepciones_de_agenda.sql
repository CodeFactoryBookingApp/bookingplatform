-- =====================================================================
--  V16: excepciones y bloqueos de agenda (HU-007)
--  (V14 en feature/disponibilidad-recursos; renumerada al fusionar con
--  feature/booking-lifecycle, que ya usa V14 y V15)
--
--  HU-007: festivos, cierres puntuales y horarios especiales que, en
--  una fecha concreta, anulan los bloques semanales de la agenda
--  (schedules, V8). Es el hueco que el propio modelo lógico señala
--  ("Excepciones de agenda como festivos o mantenimientos" — la parte
--  de mantenimientos ya la resuelve resource_maintenances en V7; esta
--  migración resuelve la parte de agenda).
--
--  Como mucho una excepción por agenda y fecha: no tiene sentido que
--  un mismo día esté cerrado y a la vez tenga horario especial.
-- =====================================================================

create table agenda_exceptions (
    id             uuid         primary key default gen_random_uuid(),
    agenda_id      uuid         not null,
    exception_date date         not null,
    type           varchar(20)  not null,
    special_start  time,
    special_end    time,
    reason         varchar(300),
    created_at     timestamptz  not null default now(),

    constraint uk_agenda_exceptions unique (agenda_id, exception_date),
    constraint fk_agenda_exceptions_agenda foreign key (agenda_id) references agendas (id) on delete cascade,
    constraint ck_agenda_exceptions_type check (type in ('CLOSED', 'SPECIAL_HOURS')),
    constraint ck_agenda_exceptions_horas check (
        (type = 'CLOSED' and special_start is null and special_end is null)
        or
        (type = 'SPECIAL_HOURS' and special_start is not null and special_end is not null and special_end > special_start)
    )
);

create index ix_agenda_exceptions_agenda on agenda_exceptions (agenda_id);

comment on table agenda_exceptions is 'Festivos, cierres y horarios especiales de una agenda en una fecha concreta (HU-007)';

alter table agenda_exceptions enable row level security;
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on public.agenda_exceptions from anon, authenticated;
    end if;
end $$;