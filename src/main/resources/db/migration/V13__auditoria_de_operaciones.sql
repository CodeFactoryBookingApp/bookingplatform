alter table audit_events alter column actor_id drop not null;
alter table audit_events add column related_resource_id uuid;

-- Los valores locales a la transacción vienen de la autenticación de la
-- API. Las escrituras directas o automáticas se identifican como sistema.
create function fn_agregar_evento_auditoria(
    p_action text, p_type text, p_resource uuid, p_organization uuid, p_related uuid default null,
    p_actor uuid default null, p_role text default null
) returns void language sql as $$
    insert into audit_events (occurred_at, actor_id, actor_role, action, resource_type, resource_id,
                              organization_id, related_resource_id, result)
    values (
        statement_timestamp(), coalesce(p_actor, nullif(current_setting('booking.audit_user', true), '')::uuid),
        coalesce(p_role, nullif(current_setting('booking.audit_role', true), ''), 'SYSTEM'),
        p_action, p_type, p_resource, p_organization, p_related, 'SUCCESS'
    )
$$;

create function fn_auditar_operacion() returns trigger language plpgsql as $$
declare
    row_data jsonb;
    scope_id uuid;
    resource_id uuid;
    related_id uuid;
    resource_type text := tg_argv[0];
    action_name text;
begin
    if tg_op = 'UPDATE' and (to_jsonb(new) - 'updated_at') = (to_jsonb(old) - 'updated_at') then
        return null;
    end if;

    row_data := case when tg_op = 'DELETE' then to_jsonb(old) else to_jsonb(new) end;
    scope_id := (row_data ->> 'organization_id')::uuid;
    resource_id := (row_data ->> 'id')::uuid;
    action_name := resource_type || case tg_op
        when 'INSERT' then '_CREATED' when 'DELETE' then '_DELETED' else '_UPDATED' end;

    case tg_table_name
        when 'organizations' then
            scope_id := resource_id;
            -- Aprobaciones, suspensiones y reactivaciones vienen del historial HU-003.
            if tg_op = 'UPDATE' and
               (to_jsonb(new) - array['updated_at', 'status', 'approved_at']) =
               (to_jsonb(old) - array['updated_at', 'status', 'approved_at']) then
                return null;
            end if;
        when 'clients' then
            if tg_op = 'UPDATE' then
                if old.status = 'PENDING_VERIFICATION' and new.status = 'ACTIVE' then
                    action_name := 'USER_EMAIL_VERIFIED';
                elsif (to_jsonb(new) - array['updated_at', 'status']) =
                      (to_jsonb(old) - array['updated_at', 'status']) then
                    return null;
                end if;
            end if;
        when 'service_locations' then
            resource_id := (row_data ->> 'service_id')::uuid;
            related_id := (row_data ->> 'location_id')::uuid;
        when 'service_resource_requirements' then
            related_id := (row_data ->> 'service_id')::uuid;
        when 'schedules' then
            related_id := (row_data ->> 'agenda_id')::uuid;
            select organization_id into scope_id from agendas where id = related_id;
        when 'agenda_services' then
            resource_id := (row_data ->> 'agenda_id')::uuid;
            related_id := (row_data ->> 'service_id')::uuid;
            select organization_id into scope_id from locations where id = (row_data ->> 'location_id')::uuid;
        when 'resource_maintenances' then
            related_id := (row_data ->> 'resource_id')::uuid;
            select organization_id into scope_id from resources where id = related_id;
        when 'bookings' then
            if tg_op = 'UPDATE' then
                if new.status is distinct from old.status then
                    perform fn_agregar_evento_auditoria('BOOKING_' || new.status, resource_type,
                                                        resource_id, scope_id);
                    if (new.starts_at, new.ends_at, new.location_id, new.session_id)
                       is not distinct from (old.starts_at, old.ends_at, old.location_id, old.session_id) then
                        return null;
                    end if;
                end if;
                if (new.starts_at, new.ends_at, new.location_id, new.session_id)
                   is distinct from (old.starts_at, old.ends_at, old.location_id, old.session_id) then
                    action_name := 'BOOKING_RESCHEDULED';
                end if;
            end if;
        else
            null;
    end case;

    perform fn_agregar_evento_auditoria(action_name, resource_type, resource_id, scope_id, related_id);
    return null;
end
$$;

create function fn_auditar_asignacion_de_reserva() returns trigger language plpgsql as $$
begin
    if tg_op = 'UPDATE' and (new.booking_id, new.resource_id) = (old.booking_id, old.resource_id) then
        return null;
    end if;
    if tg_op <> 'INSERT' then
        perform fn_agregar_evento_auditoria('BOOKING_RESOURCE_RELEASED', 'RESOURCE', old.resource_id,
            (select organization_id from locations where id = old.location_id), old.booking_id);
    end if;
    if tg_op <> 'DELETE' then
        perform fn_agregar_evento_auditoria('BOOKING_RESOURCE_ASSIGNED', 'RESOURCE', new.resource_id,
            (select organization_id from locations where id = new.location_id), new.booking_id);
    end if;
    return null;
end
$$;

-- Permite atribuir una transición cuando el escritor registra changed_by
-- en el historial pero aún no pasa el actor de su transacción.
create function fn_auditar_historial_de_reserva() returns trigger language plpgsql as $$
declare
    scope_id uuid;
    client_id uuid;
    actor_role text;
begin
    if new.changed_by is null or nullif(current_setting('booking.audit_user', true), '') is not null then
        return null;
    end if;
    select b.organization_id, b.client_id into scope_id, client_id from bookings b where b.id = new.booking_id;
    select role into actor_role from organization_members where organization_id = scope_id and user_id = new.changed_by;
    perform fn_agregar_evento_auditoria('BOOKING_STATUS_RECORDED_' || new.to_status, 'BOOKING', new.booking_id,
        scope_id, null, new.changed_by, coalesce(actor_role, case when client_id = new.changed_by then 'CLIENT' else 'UNKNOWN' end));
    return null;
end
$$;

create trigger trg_auditoria_clients after insert or update or delete on clients
    for each row execute function fn_auditar_operacion('USER');
create trigger trg_auditoria_organizations after insert or update or delete on organizations
    for each row execute function fn_auditar_operacion('ORGANIZATION');
create trigger trg_auditoria_organization_policies after insert or update or delete on organization_policies
    for each row execute function fn_auditar_operacion('ORGANIZATION_POLICY');
create trigger trg_auditoria_services after insert or update or delete on services
    for each row execute function fn_auditar_operacion('SERVICE');
create trigger trg_auditoria_service_locations after insert or update or delete on service_locations
    for each row execute function fn_auditar_operacion('SERVICE_LOCATION');
create trigger trg_auditoria_service_resources after insert or update or delete on service_resource_requirements
    for each row execute function fn_auditar_operacion('SERVICE_RESOURCE_REQUIREMENT');
create trigger trg_auditoria_agendas after insert or update or delete on agendas
    for each row execute function fn_auditar_operacion('AGENDA');
create trigger trg_auditoria_schedules after insert or update or delete on schedules
    for each row execute function fn_auditar_operacion('AGENDA_BLOCK');
create trigger trg_auditoria_agenda_services after insert or update or delete on agenda_services
    for each row execute function fn_auditar_operacion('AGENDA_SERVICE');
create trigger trg_auditoria_resource_types after insert or update or delete on resource_types
    for each row execute function fn_auditar_operacion('RESOURCE_TYPE');
create trigger trg_auditoria_resources after insert or update or delete on resources
    for each row execute function fn_auditar_operacion('RESOURCE');
create trigger trg_auditoria_maintenances after insert or update or delete on resource_maintenances
    for each row execute function fn_auditar_operacion('RESOURCE_MAINTENANCE');
create trigger trg_auditoria_bookings after insert or update or delete on bookings
    for each row execute function fn_auditar_operacion('BOOKING');
create trigger trg_auditoria_booking_resources after insert or update of booking_id, resource_id or delete on booking_resources
    for each row execute function fn_auditar_asignacion_de_reserva();
create trigger trg_auditoria_booking_status_changes after insert on booking_status_changes
    for each row execute function fn_auditar_historial_de_reserva();
