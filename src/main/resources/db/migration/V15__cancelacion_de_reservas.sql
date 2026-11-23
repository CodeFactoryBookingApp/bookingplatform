alter table bookings
    add column late_cancellation boolean not null default false,
    add constraint ck_bookings_late_cancellation check (not late_cancellation or status = 'CANCELLED');

create function fn_forbid_booking_reactivation() returns trigger language plpgsql as $$
begin
    if old.status = 'CANCELLED' and new.status <> 'CANCELLED' then
        raise exception using errcode = '23514', constraint = 'booking_cancelled_irreversible',
            message = 'HU-013: a cancelled booking cannot be reactivated';
    end if;
    return new;
end;
$$;

create trigger tg_bookings_forbid_reactivation
    before update of status on bookings
    for each row execute function fn_forbid_booking_reactivation();

