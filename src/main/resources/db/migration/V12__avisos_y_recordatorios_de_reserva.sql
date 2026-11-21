-- Los avisos de reserva se abren por el UUID autenticado del destinatario.
-- Los correos anteriores conservan el criterio de dirección de correo.
alter table sent_emails add column recipient_user_id uuid;
create index ix_sent_emails_user on sent_emails (recipient_user_id, sent_at desc)
    where recipient_user_id is not null;

create table booking_notification_preferences (
    user_id uuid primary key,
    reminders_enabled boolean not null,
    quiet_from time not null,
    quiet_until time not null,
    timezone varchar(80)
);

create table organization_booking_reminders (
    organization_id uuid primary key references organizations(id) on delete cascade,
    lead_minutes integer[] not null,
    check (cardinality(lead_minutes) <= 10 and array_position(lead_minutes, null) is null
           and 1 <= all(lead_minutes) and 10080 >= all(lead_minutes))
);

create table booking_notices (
    id uuid primary key default gen_random_uuid(),
    booking_id uuid not null references bookings(id) on delete cascade,
    kind varchar(16) not null check (kind in ('CREATED', 'CONFIRMED', 'CANCELLED', 'RESCHEDULED', 'REMINDER')),
    recipient varchar(10) not null check (recipient in ('CLIENT', 'PROVIDER')),
    appointment_at timestamptz not null,
    location_id uuid not null,
    appointment_status varchar(20) not null,
    lead_minutes integer,
    due_at timestamptz not null,
    processed_at timestamptz,
    check ((kind = 'REMINDER') = (lead_minutes is not null))
);

create index ix_booking_notices_pending on booking_notices (due_at, id) where processed_at is null;
create index ix_booking_notices_booking on booking_notices (booking_id);
create unique index uk_booking_reminder_slot
    on booking_notices (booking_id, appointment_at, location_id, lead_minutes, recipient) where kind = 'REMINDER';

alter table booking_notification_preferences enable row level security;
alter table organization_booking_reminders enable row level security;
alter table booking_notices enable row level security;

do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on booking_notification_preferences, organization_booking_reminders, booking_notices from anon;
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        revoke all on booking_notification_preferences, organization_booking_reminders, booking_notices from authenticated;
    end if;
end
$$;

-- Conserva los avisos entregados para evitar repetir una misma anticipación
-- al editar la configuración. Sólo reconstruye avisos todavía futuros.
create function fn_planificar_recordatorios(p_booking bookings, p_reset boolean default true) returns void
language plpgsql as $$
declare
    lead_times integer[];
begin
    lead_times := coalesce(
        (select lead_minutes from organization_booking_reminders where organization_id = p_booking.organization_id),
        array[1440, 120]);
    delete from booking_notices
     where booking_id = p_booking.id and kind = 'REMINDER' and processed_at is null
           and (p_reset or lead_minutes <> all(lead_times));

    if p_booking.status = 'CONFIRMED' then
        insert into booking_notices (booking_id, kind, recipient, appointment_at, location_id,
                                     appointment_status, lead_minutes, due_at)
        select p_booking.id, 'REMINDER', recipient, p_booking.starts_at, p_booking.location_id,
               p_booking.status, minutes, p_booking.starts_at - make_interval(mins => minutes)
          from unnest(lead_times) lead(minutes)
         cross join (values ('CLIENT'), ('PROVIDER')) recipients(recipient)
         where p_booking.starts_at - make_interval(mins => minutes) >= statement_timestamp()
        on conflict do nothing;
    end if;
end
$$;

create function fn_registrar_aviso_de_reserva() returns trigger language plpgsql as $$
declare
    event_kind varchar(16);
    status_changed boolean;
    schedule_changed boolean;
begin
    if tg_op = 'INSERT' then
        event_kind := 'CREATED';
    else
        status_changed := new.status is distinct from old.status;
        schedule_changed := (new.starts_at, new.ends_at, new.location_id)
                            is distinct from (old.starts_at, old.ends_at, old.location_id);
        if not status_changed and not schedule_changed then
            return new;
        end if;
        if status_changed and new.status = 'CANCELLED' then
            event_kind := 'CANCELLED';
        elsif status_changed and new.status = 'CONFIRMED' then
            event_kind := 'CONFIRMED';
        elsif schedule_changed and new.status in ('RESERVED', 'CONFIRMED') then
            event_kind := 'RESCHEDULED';
        end if;
    end if;

    perform pg_advisory_xact_lock(hashtextextended(new.organization_id::text, 15));
    if event_kind is not null then
        insert into booking_notices (booking_id, kind, recipient, appointment_at, location_id,
                                     appointment_status, due_at)
        select new.id, event_kind, recipient, new.starts_at, new.location_id,
               new.status, statement_timestamp()
          from (values ('CLIENT'), ('PROVIDER')) recipients(recipient);
    end if;
    perform fn_planificar_recordatorios(new);
    return new;
end
$$;

create trigger trg_avisos_de_reserva
    after insert or update of status, starts_at, ends_at, location_id on bookings
    for each row execute function fn_registrar_aviso_de_reserva();

create function fn_replanificar_recordatorios_del_proveedor() returns trigger
language plpgsql as $$
declare
    appointment bookings;
begin
    perform pg_advisory_xact_lock(hashtextextended(new.organization_id::text, 15));
    for appointment in
        select * from bookings where organization_id = new.organization_id
            and status = 'CONFIRMED' and starts_at > statement_timestamp()
    loop
        perform fn_planificar_recordatorios(appointment, false);
    end loop;
    return new;
end
$$;

create trigger trg_replanificar_recordatorios_del_proveedor
    after insert or update on organization_booking_reminders
    for each row execute function fn_replanificar_recordatorios_del_proveedor();

-- Activa recordatorios futuros para reservas anteriores sin volver a
-- enviar sus avisos de creación.
select fn_planificar_recordatorios(b)
  from bookings b where b.status = 'CONFIRMED' and b.starts_at > statement_timestamp();
