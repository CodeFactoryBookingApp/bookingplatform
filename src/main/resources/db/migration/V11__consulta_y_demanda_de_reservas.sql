-- Las reservas anteriores conservan un código reproducible. Las nuevas
-- reciben uno de la base aun si quien escribe omite la columna.
alter table bookings add column code varchar(64);
alter table bookings add column currency_id uuid references currencies(id);
alter table bookings disable trigger tg_bookings_updated_at;
update bookings b set code = b.id::text, currency_id = s.currency_id from services s where s.id = b.service_id;
alter table bookings enable trigger tg_bookings_updated_at;
alter table bookings
    alter column code set default gen_random_uuid()::text,
    alter column code set not null,
    add constraint uk_bookings_code unique (code),
    add constraint ck_bookings_code check (length(btrim(code)) > 0 and btrim(code) = code);

-- El precio de una reserva es histórico. Los cambios de moneda en un
-- servicio no deben reclasificar los importes de reservas ya registradas.
alter table bookings alter column currency_id set not null;

create function fn_reservas_moneda() returns trigger language plpgsql as $$
begin
    if new.currency_id is null then
        select currency_id into new.currency_id from services where id = new.service_id;
    end if;
    return new;
end;
$$;

create trigger trg_reservas_moneda
    before insert on bookings for each row execute function fn_reservas_moneda();

create index ix_bookings_org_created on bookings (organization_id, created_at);
