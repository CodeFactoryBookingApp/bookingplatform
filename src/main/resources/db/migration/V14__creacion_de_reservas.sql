alter table organization_policies add column manual_confirmation boolean not null default false;

-- Enforce the rule for new allocations while retaining any overlapping legacy rows for audit.
create function fn_booking_client_overlap() returns trigger language plpgsql as $$
begin
    if tg_op = 'UPDATE' and old.status in ('RESERVED','CONFIRMED')
       and (old.client_id, old.starts_at, old.ends_at) = (new.client_id, new.starts_at, new.ends_at) then
        return new;
    end if;
    perform pg_advisory_xact_lock(hashtextextended(new.client_id::text, 12));
    if exists (select 1 from bookings b where b.client_id = new.client_id and b.id <> new.id
               and b.status in ('RESERVED','CONFIRMED') and b.starts_at < new.ends_at and b.ends_at > new.starts_at) then
        raise exception using errcode = '23P12', constraint = 'booking_client_overlap',
            message = 'Client already has an active booking at this time';
    end if;
    return new;
end;
$$;
create trigger trg_booking_client_overlap before insert or update of status, client_id, starts_at, ends_at on bookings
    for each row when (new.status in ('RESERVED','CONFIRMED')) execute function fn_booking_client_overlap();
