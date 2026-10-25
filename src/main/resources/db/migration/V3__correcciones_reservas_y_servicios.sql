-- =====================================================================
--  V3: correcciones del esquema que necesita el Sprint 2
--
--  1. Un servicio nuevo nace inactivo (HU-004, HU-009).
--  2. fn_bookings_reglas sólo valida lo que compromete agenda nueva, de
--     modo que suspender un proveedor o desactivar un servicio no deje
--     sus reservas sin poder cancelarse ni cerrarse (HU-003, HU-004,
--     HU-013, HU-016).
--  3. El aforo de una sesión grupal resiste reservas simultáneas (HU-010).
-- =====================================================================


-- HU-004: "el servicio queda disponible para activarse", y HU-009 no deja
-- activarlo sin recursos. Con ACTIVE por defecto, un INSERT que olvide el
-- estado publicaba el servicio en el catálogo sin pasar por esa regla.
-- Los servicios que ya existen conservan su estado.
alter table services alter column status set default 'INACTIVE';


-- Antes, el proveedor activo se exigía en CUALQUIER insert o update de
-- una reserva y el servicio activo en cualquiera que la dejara RESERVED o
-- CONFIRMED. Al suspender un proveedor o desactivar un servicio, sus
-- reservas ya no podían cancelarse, completarse, marcarse como no-show
-- ni confirmarse, aunque HU-004 dice que las reservas existentes se
-- conservan.
--
-- Ahora cada regla mira sólo el cambio que la afecta:
--   · Cancelar, completar o marcar no-show no compromete nada: no se valida.
--   · Proveedor y servicio activos: al crear la reserva, al reactivarla
--     o al moverla de horario o de sesión. Confirmar una reserva
--     pendiente no ocupa agenda nueva: ya la tenía.
--   · Cliente verificado: al pasar a CONFIRMED (HU-001).
--   · Aforo: cuando la reserva entra, se mueve o suma asistentes.
create or replace function fn_bookings_reglas() returns trigger
language plpgsql as $$
declare
    v_cliente  varchar(30);
    v_org      varchar(30);
    v_servicio varchar(20);
    v_cupo     integer;
    v_ocupado  integer;
    v_entra    boolean;   -- pasa a ocupar cupo: se crea o se reactiva
    v_mueve    boolean;   -- cambia de horario o de sesión
    v_crece    boolean;   -- suma asistentes
begin
    if new.status not in ('RESERVED', 'CONFIRMED') then
        return new;
    end if;

    if tg_op = 'INSERT' then
        v_entra := true;
        v_mueve := false;
        v_crece := false;
    else
        v_entra := old.status not in ('RESERVED', 'CONFIRMED');
        v_mueve := (new.starts_at, new.ends_at, new.session_id)
                   is distinct from (old.starts_at, old.ends_at, old.session_id);
        v_crece := new.attendees > old.attendees;
    end if;

    if v_entra or v_mueve then
        select status into v_org from organizations where id = new.organization_id;
        if v_org <> 'ACTIVE' then
            raise exception 'HU-002: el proveedor está en estado %, no es reservable', v_org;
        end if;

        select status into v_servicio from services where id = new.service_id;
        if v_servicio <> 'ACTIVE' then
            raise exception 'HU-004: el servicio está inactivo, no admite reservas nuevas';
        end if;
    end if;

    if new.status = 'CONFIRMED' and (tg_op = 'INSERT' or old.status <> 'CONFIRMED') then
        select status into v_cliente from clients where id = new.client_id;
        if v_cliente <> 'ACTIVE' then
            raise exception 'HU-001: sólo un cliente verificado puede confirmar una reserva (estado %)', v_cliente;
        end if;
    end if;

    if v_entra or v_mueve or v_crece then
        -- Sin este candado, dos reservas simultáneas por el último cupo
        -- sumaban el mismo ocupado y entraban las dos. Con él, la segunda
        -- espera a que la primera termine su transacción, y como en READ
        -- COMMITTED cada sentencia toma una instantánea nueva, su suma ya
        -- incluye la primera. Se libera solo al terminar la transacción.
        perform pg_advisory_xact_lock(hashtextextended(new.session_id::text, 0));

        select customer_capacity into v_cupo from services where id = new.service_id;

        select coalesce(sum(attendees), 0) into v_ocupado
          from bookings
         where session_id = new.session_id
           and status in ('RESERVED', 'CONFIRMED')
           and id <> new.id;

        if v_ocupado + new.attendees > v_cupo then
            raise exception 'HU-004: aforo superado: % ocupados + % solicitados > cupo %',
                  v_ocupado, new.attendees, v_cupo;
        end if;
    end if;

    return new;
end $$;
