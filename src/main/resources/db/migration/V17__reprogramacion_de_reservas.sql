alter table organization_policies
    add column max_reschedules integer not null default 1,
    add constraint ck_organization_policies_reschedules check (max_reschedules >= 0);

alter table bookings
    add column reschedule_count integer not null default 0,
    add column late_reschedule boolean not null default false,
    add constraint ck_bookings_reschedule_count check (reschedule_count >= 0);

create function fn_forbid_booking_service_change() returns trigger language plpgsql as $$
begin
    if new.service_id is distinct from old.service_id or new.organization_id is distinct from old.organization_id then
        raise exception using errcode = '23514', constraint = 'booking_service_immutable',
            message = 'HU-014: booking service and organization cannot be changed when rescheduling';
    end if;
    return new;
end;
$$;

create trigger tg_bookings_forbid_service_change
    before update of service_id, organization_id on bookings
    for each row execute function fn_forbid_booking_service_change();
