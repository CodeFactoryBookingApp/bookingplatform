-- Candidate starts follow the agenda grid. Generate instants across the block so both
-- occurrences of a local time can be booked on a daylight-saving fallback day.
-- An agenda exception on that date (HU-007, V16) wins over the weekly blocks: CLOSED leaves the
-- day without windows, and SPECIAL_HOURS replaces them with its own single window.
with candidates as (
    select a.id as agenda, a.resource_id as pinned, g.start, g.start + :duration * interval '1 minute' as finish
      from agenda_services link
      join agendas a on a.id = link.agenda_id
      left join agenda_exceptions x on x.agenda_id = a.id and x.exception_date = cast(:day as date)
      cross join lateral (
          select w.start_time, w.end_time from schedules w
           where x.id is null and w.agenda_id = a.id and w.active
             and w.day_of_week = extract(isodow from cast(:day as date))
          union all
          select x.special_start, x.special_end where x.type = 'SPECIAL_HOURS'
      ) h(start_time, end_time)
      cross join lateral generate_series(
          (cast(:day as date) + h.start_time) at time zone :zone,
          (cast(:day as date) + h.end_time) at time zone :zone,
          a.slot_minutes * interval '1 minute') g(start)
     where link.service_id = :service and a.location_id = :location
       and cast(:day as date) between a.valid_from and coalesce(a.valid_until, cast(:day as date))
       and g.start between :earliest and :latest
       and g.start - :prep * interval '1 minute' >= (cast(:day as date) + h.start_time) at time zone :zone
       and g.start + (:duration + :cleanup) * interval '1 minute' <= (cast(:day as date) + h.end_time) at time zone :zone
), sessions as (
    select c.*, s.session_id, coalesce(s.occupied, 0) as occupied
      from candidates c
      left join lateral (
          select b.session_id, sum(b.attendees) as occupied from bookings b
           where b.location_id = :location and b.service_id = :service
             and (cast(:ignoreBooking as uuid) is null or b.id <> cast(:ignoreBooking as uuid))
             and b.starts_at = c.start and b.ends_at = c.finish and b.status in ('RESERVED','CONFIRMED')
             -- A pinned agenda can share only a session already using that resource.
             and (c.pinned is null or exists (
                 select 1 from booking_resources held where held.session_id = b.session_id
                   and (cast(:ignoreBooking as uuid) is null or held.booking_id <> cast(:ignoreBooking as uuid))
                   and held.resource_id = c.pinned and held.status in ('RESERVED','CONFIRMED')))
           group by b.session_id order by b.session_id limit 1
      ) s on true
), required as (
    select resource_type_id, quantity_required from service_resource_requirements where service_id = :service
), eligible_resources as (
    select s.agenda, s.start, r.id, r.resource_type_id, req.quantity_required, r.id = s.pinned as pinned,
           row_number() over (partition by s.agenda, s.start, r.resource_type_id
                              order by (r.id = s.pinned) desc nulls last,
                                       (cast(:preferredResource as uuid) is not null and r.id = cast(:preferredResource as uuid)) desc,
                                       r.capacity desc, r.id) as priority
      from sessions s
      join resources r on r.location_id = :location and r.status = 'ACTIVE' and r.capacity > s.occupied
      left join required req on req.resource_type_id = r.resource_type_id
     where (req.resource_type_id is not null or r.id = s.pinned)
       and (s.session_id is null or exists (
            select 1 from booking_resources held where held.session_id = s.session_id and held.resource_id = r.id
              and (cast(:ignoreBooking as uuid) is null or held.booking_id <> cast(:ignoreBooking as uuid))
              and held.status in ('RESERVED','CONFIRMED')))
       and not exists (
            select 1 from resource_maintenances m where m.resource_id = r.id
              and m.starts_at < s.finish + :cleanup * interval '1 minute'
              and m.ends_at > s.start - :prep * interval '1 minute')
       and not exists (
            select 1 from booking_resources held join bookings b on b.id = held.booking_id
              join services other on other.id = b.service_id
             where held.resource_id = r.id and held.status in ('RESERVED','CONFIRMED')
               and (cast(:ignoreBooking as uuid) is null or held.booking_id <> cast(:ignoreBooking as uuid))
               and held.session_id is distinct from s.session_id
               and held.starts_at - other.preparation_minutes * interval '1 minute' < s.finish + :cleanup * interval '1 minute'
               and held.ends_at + other.cleanup_minutes * interval '1 minute' > s.start - :prep * interval '1 minute')
), allocations as (
    select agenda, start, array_agg(id order by id) as ids
      from eligible_resources where priority <= quantity_required or pinned group by agenda, start
), ranked_candidates as (
    select s.start, s.finish, s.session_id, coalesce(a.ids, array[]::uuid[]) as ids,
           row_number() over (partition by s.start order by s.agenda) as slot_rank
      from sessions s left join allocations a using (agenda, start)
     where s.occupied < :capacity
       and (s.pinned is null or s.pinned = any(a.ids))
       and not exists (
           select 1 from required req where req.quantity_required > (
               select count(*) from eligible_resources r where r.agenda = s.agenda and r.start = s.start
                 and r.resource_type_id = req.resource_type_id))
)
select start, finish, session_id, ids
  from ranked_candidates
 where cast(:ignoreBooking as uuid) is not null or slot_rank = 1
 order by start, slot_rank

