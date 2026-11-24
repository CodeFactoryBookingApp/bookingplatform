# Genera bookingplatform-sprint3.postman_collection.json (fuente de la colección; editar aquí y regenerar).
# Uso: python tools/generar_coleccion.py bookingplatform-sprint3.postman_collection.json
import json, sys, uuid

OUT = sys.argv[1]

TIME = """pm.test("Tiempo de respuesta < " + pm.environment.get("maxResponseMs") + " ms", function () {
    pm.expect(pm.response.responseTime).to.be.below(Number(pm.environment.get("maxResponseMs")));
});"""


def status(code, label=None):
    return f"""pm.test("Código de estado {code}", function () {{
    pm.response.to.have.status({code});
}});"""


def problem(code, error_code):
    """Contrato de error RFC 7807 del proyecto (ADR-0004)."""
    return status(code) + f"""
pm.test("Contrato de error: problem+json con errorCode {error_code}", function () {{
    pm.expect(pm.response.headers.get("Content-Type")).to.include("application/problem+json");
    const body = pm.response.json();
    pm.expect(body.errorCode).to.eql("{error_code}");
    pm.expect(body.status).to.eql({code});
    pm.expect(body).to.have.property("traceId");
    pm.expect(body).to.have.property("timestamp");
}});"""


def mint(var, claims_js):
    """Pide un JWT al simulador de Supabase Auth (POST /_ctl/mint)."""
    return f"""pm.sendRequest({{
    url: pm.environment.get("stubUrl") + "/_ctl/mint",
    method: "POST",
    header: {{ "Content-Type": "application/json" }},
    body: {{ mode: "raw", raw: JSON.stringify({claims_js}) }}
}}, function (err, res) {{
    pm.expect(err, "simulador de Supabase Auth disponible").to.be.null;
    pm.environment.set("{var}", res.json().token);
}});"""


def auth(var):
    return [{"key": "Authorization", "value": "Bearer {{" + var + "}}"}]


JSON_HDR = [{"key": "Content-Type", "value": "application/json"}]

UUID_RE = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"

SCHEMA = {
    "receipt": {"type": "object", "required": ["id", "code", "status"],
                "properties": {"id": {"type": "string", "pattern": UUID_RE}, "code": {"type": "string", "minLength": 4},
                               "status": {"enum": ["RESERVED", "CONFIRMED"]}}},
    "slots": {"type": "array", "minItems": 1, "items": {"type": "object", "required": ["startsAt", "endsAt"],
              "properties": {"startsAt": {"type": "string"}, "endsAt": {"type": "string"}}}},
    "organization": {"type": "object", "required": ["id", "name", "status", "locations", "operationPolicy"],
                     "properties": {"id": {"type": "string", "pattern": UUID_RE},
                                    "status": {"enum": ["PENDING_APPROVAL", "ACTIVE", "SUSPENDED", "REJECTED"]},
                                    "locations": {"type": "array", "minItems": 1},
                                    "operationPolicy": {"type": "object",
                                                        "required": ["version", "bookingNoticeMinutesMin",
                                                                     "bookingNoticeMinutesMax", "maxReschedules"]}}},
    "clientBookings": {"type": "object", "required": ["upcoming", "history", "empty"],
                       "properties": {"upcoming": {"type": "array", "items": {"type": "object",
                                      "required": ["id", "code", "status", "startsAt", "endsAt", "rescheduleCount",
                                                   "maxReschedules", "actions"]}},
                                      "history": {"type": "array"}, "empty": {"type": "boolean"}}},
    "searchPage": {"type": "object", "required": ["items", "summary", "page", "size"],
                   "properties": {"items": {"type": "array", "items": {"type": "object",
                                  "required": ["id", "code", "clientName", "serviceName", "startsAt", "status"]}},
                                  "summary": {"type": "object", "required": ["total", "byStatus"]}}},
    "occupancy": {"type": "array", "items": {"type": "object",
                  "required": ["agendaId", "agendaName", "from", "to", "totalSlots", "occupiedSlots", "occupancyRate"],
                  "properties": {"totalSlots": {"type": "integer", "minimum": 0},
                                 "occupiedSlots": {"type": "integer", "minimum": 0},
                                 "occupancyRate": {"type": "number", "minimum": 0}}}},
    "demand": {"type": "object", "required": ["period", "previousPeriod", "timezone", "services", "demand"],
               "properties": {"services": {"type": "array", "items": {"type": "object",
                              "required": ["id", "name", "current", "previous", "variation"]}}}},
    "auditPage": {"type": "object", "required": ["items", "total", "page", "size"],
                  "properties": {"items": {"type": "array", "items": {"type": "object",
                                 "required": ["id", "occurredAt", "actorRole", "action", "resourceType", "result"]}}}},
}


def schema(name):
    return f"""pm.test("Cumple el esquema JSON ({name})", function () {{
    pm.response.to.have.jsonSchema({json.dumps(SCHEMA[name])});
}});"""


def body(obj):
    return {"mode": "raw", "raw": json.dumps(obj, indent=2, ensure_ascii=False),
            "options": {"raw": {"language": "json"}}}


ITEMS = []  # (folder, name, method, path, headers, body, prerequest, tests, description)


def add(folder, name, method, path, headers=None, req_body=None, pre=None, tests="", desc=""):
    ITEMS.append((folder, name, method, path, headers or [], req_body, pre, tests, desc))


F0 = "00 Preparación (proveedor, servicio, agenda y cliente)"
F1 = "01 HU-008/011 Disponibilidad"
F2 = "02 HU-012 Crear reserva"
F3 = "03 HU-014 Reprogramar reserva"
F4 = "04 HU-017 Historial y comprobante PDF"
F5 = "05 HU-018 Búsqueda y exportación CSV"
F6 = "06 HU-016 Asistencia y HU-007 Excepción de agenda"
F7 = "07 HU-013 Cancelar reserva"
F8 = "08 HU-015 Bandeja, HU-019 Ocupación y HU-020 Demanda"
F9 = "09 HU-022 Cambio de rol y HU-023 Auditoría"

# ---------------------------------------------------------------- 00 setup
add(F0, "Registrar proveedor (queda pendiente de aprobación)", "POST", "/provider-registrations",
    JSON_HDR,
    {"email": "qa.prov.{{runId}}@example.com", "password": "{{password}}", "name": "Spa QA {{runId}}",
     "categoryId": "{{categoryId}}", "identificationType": "NIT", "identificationNumber": "QA{{runId}}",
     "contactPhone": "+573001112233", "timezone": "America/Bogota",
     "locations": [{"name": "Sede Centro", "address": "Calle 10 # 20-30", "cityId": "{{cityId}}",
                    "timezone": "America/Bogota"}],
     "bookingNoticeMinutesMin": 60, "bookingNoticeMinutesMax": 43200, "freeCancellationWindowMinutes": 120},
    pre="""// Arrange: datos únicos por corrida, fechas en la zona del proveedor y token de administrador
const runId = String(Date.now()).slice(-9);
pm.environment.set("runId", runId);
const bogota = (days) => new Date(Date.now() - 5 * 3600e3 + days * 864e5).toISOString().slice(0, 10);
pm.environment.set("today", bogota(0));
pm.environment.set("weekEnd", bogota(6));
pm.environment.set("bookingDate", bogota(3));
pm.environment.set("closedDate", bogota(4));
pm.environment.set("farDate", bogota(45));
""" + mint("adminToken", '{ _email: "qa.admin@example.com", app_metadata: { provider: "email", role: "ADMIN" } }') + """
pm.sendRequest(pm.environment.get("baseUrl") + "/organization-categories", function (err, res) {
    pm.environment.set("categoryId", res.json()[0].id);
});
pm.sendRequest(pm.environment.get("baseUrl") + "/cities", function (err, res) {
    const city = res.json().find(c => c.name.startsWith("Medell")) || res.json()[0];
    pm.environment.set("cityId", city.id);
});""",
    tests=status(201) + """
pm.test("El proveedor queda PENDING_APPROVAL con su organización y su usuario dueño", function () {
    const b = pm.response.json();
    pm.expect(b.status).to.eql("PENDING_APPROVAL");
    pm.expect(b.organizationId).to.match(/^[0-9a-f-]{36}$/);
    pm.expect(b.ownerUserId).to.match(/^[0-9a-f-]{36}$/);
    pm.expect(b.email).to.eql("qa.prov." + pm.environment.get("runId") + "@example.com");
    pm.environment.set("orgId", b.organizationId);
});
""" + TIME,
    desc="HU-002 (precondición). Registro público del proveedor con una sede y su política inicial.")

add(F0, "Aprobar proveedor como administrador", "POST", "/admin/organizations/{{orgId}}/approval",
    JSON_HDR + auth("adminToken"), {"reason": "Documentación verificada por QA"},
    tests=status(200) + "\n" + schema("organization") + """
pm.test("La organización pasa a ACTIVE con fecha de aprobación", function () {
    const b = pm.response.json();
    pm.expect(b.id).to.eql(pm.environment.get("orgId"));
    pm.expect(b.status).to.eql("ACTIVE");
    pm.expect(b.approvedAt).to.be.a("string");
    pm.environment.set("locationId", b.locations[0].id);
});
""" + TIME,
    desc="HU-002 (precondición). Solo un ADMIN aprueba; deja la sede disponible para el resto del flujo.")

add(F0, "Ajustar política de operación (máx. 1 reprogramación)", "PUT", "/organizations/{{orgId}}/operation-policy",
    JSON_HDR + auth("providerToken"),
    {"bookingNoticeMinutesMin": 60, "bookingNoticeMinutesMax": 43200, "freeCancellationWindowMinutes": 120,
     "manualConfirmation": False, "maxReschedules": 1, "defaultClosureStatus": "COMPLETED",
     "autoCloseDelayHours": 24},
    pre="// Arrange: el dueño del proveedor obtiene su token (rol PROVIDER, sub = ownerUserId)\n"
        + mint("providerToken", '{ _email: "qa.prov." + pm.environment.get("runId") + "@example.com" }'),
    tests=status(200) + """
pm.test("La política nueva queda vigente: anticipación 60 min a 30 días, 1 reprogramación", function () {
    const p = pm.response.json().operationPolicy;
    pm.expect(p.bookingNoticeMinutesMin).to.eql(60);
    pm.expect(p.bookingNoticeMinutesMax).to.eql(43200);
    pm.expect(p.freeCancellationWindowMinutes).to.eql(120);
    pm.expect(p.maxReschedules).to.eql(1);
    pm.expect(p.version).to.be.above(1);
});
""" + TIME,
    desc="HU-002 (precondición). Fija las reglas que luego verifican HU-012 y HU-014.")

add(F0, "Crear tipo de recurso", "POST", "/organizations/{{orgId}}/resource-types",
    JSON_HDR + auth("providerToken"), {"name": "Camilla {{runId}}", "description": "Camilla de masaje"},
    tests=status(201) + """
pm.test("Devuelve el tipo creado con su id", function () {
    const b = pm.response.json();
    pm.expect(b.name).to.eql("Camilla " + pm.environment.get("runId"));
    pm.environment.set("resourceTypeId", b.id);
});
""" + TIME, desc="HU-009 (precondición).")

add(F0, "Registrar recurso con capacidad 1", "POST", "/organizations/{{orgId}}/resources",
    JSON_HDR + auth("providerToken"),
    {"resourceTypeId": "{{resourceTypeId}}", "locationId": "{{locationId}}", "name": "Camilla 1", "capacity": 1},
    tests=status(201) + """
pm.test("El recurso queda AVAILABLE en la sede", function () {
    const b = pm.response.json();
    pm.expect(b.condition).to.eql("AVAILABLE");
    pm.expect(b.locationId).to.eql(pm.environment.get("locationId"));
    pm.expect(b.capacity).to.eql(1);
    pm.environment.set("resourceId", b.id);
});
""" + TIME, desc="HU-009 (precondición). Un único recurso: dos reservas en la misma franja deben chocar.")

add(F0, "Crear servicio de 60 minutos", "POST", "/organizations/{{orgId}}/services",
    JSON_HDR + auth("providerToken"),
    {"details": {"name": "Masaje QA {{runId}}", "description": "Masaje relajante", "durationMinutes": 60,
                 "preparationMinutes": 0, "cleanupMinutes": 0, "price": 80000, "customerCapacity": 1},
     "locationIds": ["{{locationId}}"], "requirements": [{"resourceTypeId": "{{resourceTypeId}}", "quantity": 1}]},
    tests=status(201) + """
pm.test("El servicio nace INACTIVE con su requisito de recurso", function () {
    const b = pm.response.json();
    pm.expect(b.status).to.eql("INACTIVE");
    pm.expect(b.durationMinutes).to.eql(60);
    pm.expect(b.requirements[0].resourceTypeId).to.eql(pm.environment.get("resourceTypeId"));
    pm.environment.set("serviceId", b.id);
});
""" + TIME, desc="HU-004 (precondición).")

add(F0, "Activar servicio", "POST", "/organizations/{{orgId}}/services/{{serviceId}}/activation",
    auth("providerToken"),
    tests=status(200) + """
pm.test("El servicio queda ACTIVE", function () {
    pm.expect(pm.response.json().status).to.eql("ACTIVE");
});
""" + TIME, desc="HU-004 (precondición).")

add(F0, "Crear agenda de la sede (08:00-18:00, franjas de 60 min)", "POST", "/organizations/{{orgId}}/agendas",
    JSON_HDR + auth("providerToken"),
    {"name": "Agenda QA {{runId}}", "locationId": "{{locationId}}", "slotMinutes": 60, "validFrom": "{{today}}",
     "serviceIds": ["{{serviceId}}"],
     "blocks": [{"dayOfWeek": d, "startTime": "08:00", "endTime": "18:00"} for d in
                ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]]},
    tests=status(201) + """
pm.test("La agenda guarda granularidad, servicio y los 7 bloques", function () {
    const b = pm.response.json();
    pm.expect(b.slotMinutes).to.eql(60);
    pm.expect(b.serviceIds).to.include(pm.environment.get("serviceId"));
    pm.expect(b.blocks).to.have.lengthOf(7);
    pm.environment.set("agendaId", b.id);
});
""" + TIME, desc="HU-006 (precondición).")

add(F0, "Registrar cliente A", "POST", "/registrations", JSON_HDR,
    {"fullName": "Cliente QA A", "document": "A{{runId}}", "birthDate": "1995-04-12",
     "email": "qa.cliente.a.{{runId}}@example.com", "phone": "+573001234567", "city": "Medellín",
     "notificationChannel": "EMAIL", "password": "{{password}}"},
    tests=status(201) + """
pm.test("La cuenta queda PENDING_VERIFICATION", function () {
    const b = pm.response.json();
    pm.expect(b.status).to.eql("PENDING_VERIFICATION");
    pm.environment.set("clientAId", b.clientId);
});
""" + TIME, desc="HU-001 (precondición).")

add(F0, "Verificar correo e iniciar sesión como cliente A", "POST", "/auth/login", JSON_HDR,
    {"email": "qa.cliente.a.{{runId}}@example.com", "password": "{{password}}"},
    pre="""// Arrange: el cliente A confirma su correo con el token_hash que emite el simulador
const emailA = "qa.cliente.a." + pm.environment.get("runId") + "@example.com";
pm.sendRequest({
    url: pm.environment.get("stubUrl") + "/_ctl/confirm", method: "POST",
    header: { "Content-Type": "application/json" },
    body: { mode: "raw", raw: JSON.stringify({ email: emailA }) }
}, function (err, res) {
    pm.sendRequest({
        url: pm.environment.get("baseUrl") + "/registrations/email-verifications", method: "POST",
        header: { "Content-Type": "application/json" },
        body: { mode: "raw", raw: JSON.stringify({ tokenHash: res.json().token_hash }) }
    }, function (e2, r2) {
        pm.expect(r2.json().status, "cuenta verificada").to.eql("ACTIVE");
    });
});""",
    tests=status(200) + """
pm.test("Entrega accessToken bearer", function () {
    const b = pm.response.json();
    pm.expect(b.accessToken).to.be.a("string").and.not.empty;
    pm.expect(b.tokenType.toLowerCase()).to.eql("bearer");
    pm.environment.set("clientToken", b.accessToken);
});
""" + TIME, desc="HU-021 (precondición).")

# ---------------------------------------------------------------- 01 availability
add(F1, "Consultar franjas libres de un día (cliente)", "GET",
    "/booking/availability?serviceId={{serviceId}}&locationId={{locationId}}&date={{bookingDate}}",
    auth("clientToken"),
    tests=status(200) + "\n" + schema("slots") + """
pm.test("Ofrece las 10 franjas de 60 min de 08:00 a 18:00 en hora de Bogotá", function () {
    const slots = pm.response.json();
    pm.expect(slots).to.have.lengthOf(10);
    slots.forEach(s => {
        pm.expect(new Date(s.endsAt) - new Date(s.startsAt)).to.eql(60 * 60 * 1000);
        pm.expect(s.startsAt).to.include(pm.environment.get("bookingDate"));
        pm.expect(s.startsAt).to.match(/-05:00$/);
    });
    pm.expect(slots[0].startsAt).to.include("T08:00");
    // Se guardan como instantes UTC para el cuerpo de las reservas
    pm.environment.set("slot0", new Date(slots[0].startsAt).toISOString());
    pm.environment.set("slot1", new Date(slots[1].startsAt).toISOString());
    pm.environment.set("slot2", new Date(slots[2].startsAt).toISOString());
});
""" + TIME, desc="HU-011. Positivo: la disponibilidad del día respeta bloques y granularidad.")

add(F1, "Calendario de disponibilidad de la agenda (proveedor)", "GET",
    "/organizations/{{orgId}}/agendas/{{agendaId}}/availability?from={{today}}&to={{weekEnd}}",
    auth("providerToken"),
    tests=status(200) + """
pm.test("Devuelve un día por fecha del rango y el día objetivo tiene 10 franjas", function () {
    const days = pm.response.json();
    pm.expect(days).to.have.lengthOf(7);
    const target = days.find(d => d.date === pm.environment.get("bookingDate"));
    pm.expect(target, "día " + pm.environment.get("bookingDate")).to.exist;
    pm.expect(target.slots).to.have.lengthOf(10);
});
""" + TIME, desc="HU-008. Positivo: vista del proveedor por rango de fechas.")

# ---------------------------------------------------------------- 02 create booking
add(F2, "Vista previa de la reserva con términos vigentes", "GET",
    "/booking/preview?serviceId={{serviceId}}&locationId={{locationId}}&startsAt={{slot0}}",
    auth("clientToken"),
    tests=status(200) + """
pm.test("Resume servicio, precio y política, y entrega la versión de términos", function () {
    const b = pm.response.json();
    pm.expect(b.service).to.eql("Masaje QA " + pm.environment.get("runId"));
    pm.expect(Number(b.price)).to.eql(80000);
    pm.expect(b.freeCancellationWindowMinutes).to.eql(120);
    pm.expect(b.termsVersion).to.be.a("string").and.not.empty;
    pm.expect(new Date(b.slot.startsAt).toISOString()).to.eql(pm.environment.get("slot0"));
    pm.environment.set("termsVersion", b.termsVersion);
});
""" + TIME, desc="HU-012. Paso previo a reservar: el cliente acepta una versión concreta de términos.")

add(F2, "Crear reserva en franja libre", "POST", "/bookings", JSON_HDR + auth("clientToken"),
    {"serviceId": "{{serviceId}}", "locationId": "{{locationId}}", "startsAt": "{{slot0}}",
     "termsVersion": "{{termsVersion}}"},
    tests=status(201) + "\n" + schema("receipt") + """
const created = pm.response.json();
pm.environment.set("bookingId", created.id);
pm.environment.set("bookingCode", created.code);
pm.test("Sin confirmación manual en la política, la reserva nace CONFIRMED", function () {
    pm.expect(created.status).to.eql("CONFIRMED");
});
""" + TIME, desc="HU-012. Positivo.")

add(F2, "Reservar franja ya ocupada (otro cliente)", "POST", "/bookings", JSON_HDR + auth("clientBToken"),
    {"serviceId": "{{serviceId}}", "locationId": "{{locationId}}", "startsAt": "{{slot0}}",
     "termsVersion": "{{termsVersion}}"},
    pre="""// Arrange: un segundo cliente se registra, verifica su correo e inicia sesión
const base = pm.environment.get("baseUrl"), stub = pm.environment.get("stubUrl");
const email = "qa.cliente.b." + pm.environment.get("runId") + "@example.com";
const json = (url, payload, cb) => pm.sendRequest({ url, method: "POST",
    header: { "Content-Type": "application/json" }, body: { mode: "raw", raw: JSON.stringify(payload) } }, cb);
json(base + "/registrations", { fullName: "Cliente QA B", document: "B" + pm.environment.get("runId"),
    birthDate: "1990-08-20", email, phone: "+573007654321", city: "Medellín", notificationChannel: "EMAIL",
    password: pm.environment.get("password") }, function (e1, r1) {
    pm.environment.set("clientBId", r1.json().clientId);
    json(stub + "/_ctl/confirm", { email }, function (e2, r2) {
        json(base + "/registrations/email-verifications", { tokenHash: r2.json().token_hash }, function () {
            json(base + "/auth/login", { email, password: pm.environment.get("password") }, function (e4, r4) {
                pm.environment.set("clientBToken", r4.json().accessToken);
            });
        });
    });
});""",
    tests=problem(409, "SLOT_UNAVAILABLE") + "\n" + TIME,
    desc="HU-012. Excepcional: el único recurso ya está asignado en esa franja.")

add(F2, "Reservar fuera de la anticipación máxima de la política", "POST", "/bookings",
    JSON_HDR + auth("clientToken"),
    {"serviceId": "{{serviceId}}", "locationId": "{{locationId}}", "startsAt": "{{farStart}}",
     "termsVersion": "{{termsVersion}}"},
    pre="""// Arrange: 45 días adelante a las 10:00 de Bogotá; la política admite hasta 30 días (43200 min)
pm.environment.set("farStart", pm.environment.get("farDate") + "T15:00:00Z");""",
    tests="""pm.test("Se rechaza con un 4xx", function () {
    pm.expect(pm.response.code).to.be.within(400, 499);
});
pm.test("Contrato de error: la franja no es reservable (SLOT_UNAVAILABLE)", function () {
    pm.expect(pm.response.json().errorCode).to.eql("SLOT_UNAVAILABLE");
});
""" + TIME, desc="HU-012. Excepcional: anticipación fuera de política.")

add(F2, "Reservar sin token", "POST", "/bookings", JSON_HDR,
    {"serviceId": "{{serviceId}}", "locationId": "{{locationId}}", "startsAt": "{{slot2}}",
     "termsVersion": "{{termsVersion}}"},
    tests=problem(401, "AUTH_REQUIRED") + "\n" + TIME, desc="HU-012. Excepcional: sin autenticación.")

add(F2, "Reservar con rol PROVIDER", "POST", "/bookings", JSON_HDR + auth("providerToken"),
    {"serviceId": "{{serviceId}}", "locationId": "{{locationId}}", "startsAt": "{{slot2}}",
     "termsVersion": "{{termsVersion}}"},
    tests=problem(403, "ACCESS_DENIED") + "\n" + TIME, desc="HU-012. Excepcional: rol incorrecto.")

add(F2, "Reservar con términos desactualizados", "POST", "/bookings", JSON_HDR + auth("clientToken"),
    {"serviceId": "{{serviceId}}", "locationId": "{{locationId}}", "startsAt": "{{slot2}}",
     "termsVersion": "terminos-obsoletos"},
    tests=problem(409, "BOOKING_TERMS_CHANGED") + "\n" + TIME,
    desc="HU-012. Excepcional: la versión aceptada no coincide con la vigente.")

# ---------------------------------------------------------------- 03 reschedule
add(F3, "Reprogramar a otra franja del mismo día", "POST", "/bookings/{{bookingId}}/reschedule",
    JSON_HDR + auth("clientToken"), {"startsAt": "{{slot1}}"},
    tests=status(200) + """
pm.test("Conserva el código, mueve el inicio y cuenta 1 reprogramación", function () {
    const b = pm.response.json();
    pm.expect(b.id).to.eql(pm.environment.get("bookingId"));
    pm.expect(b.code).to.eql(pm.environment.get("bookingCode"));
    pm.expect(new Date(b.startsAt).toISOString()).to.eql(pm.environment.get("slot1"));
    pm.expect(b.rescheduleCount).to.eql(1);
    pm.expect(b.lateReschedule).to.eql(false);
});
""" + TIME, desc="HU-014. Positivo.")

add(F3, "Superar el límite de reprogramaciones", "POST", "/bookings/{{bookingId}}/reschedule",
    JSON_HDR + auth("clientToken"), {"startsAt": "{{slot2}}"},
    tests=problem(409, "RESCHEDULE_LIMIT_REACHED") + """
pm.test("Indica el límite y sugiere cancelar y crear una nueva", function () {
    const d = pm.response.json().details;
    pm.expect(d.maxReschedules).to.eql("1");
    pm.expect(d.rescheduleCount).to.eql("1");
    pm.expect(d.suggestedAction).to.eql("CANCEL_AND_CREATE_NEW");
});
""" + TIME, desc="HU-014. Excepcional: política con máximo 1 reprogramación.")

add(F3, "La franja liberada por la reprogramación vuelve a ser reservable", "POST", "/bookings",
    JSON_HDR + auth("clientBToken"),
    {"serviceId": "{{serviceId}}", "locationId": "{{locationId}}", "startsAt": "{{slot0}}",
     "termsVersion": "{{termsVersion}}"},
    tests=status(201) + "\n" + schema("receipt") + """
pm.environment.set("bookingBId", pm.response.json().id);
pm.test("El cliente B obtiene la franja que antes estaba ocupada", function () {
    pm.expect(pm.response.json().id).to.not.eql(pm.environment.get("bookingId"));
});
""" + TIME, desc="HU-012/HU-014. Positivo: reprogramar libera el recurso de la franja original.")

# ---------------------------------------------------------------- 04 history
add(F4, "Historial del cliente: próximas reservas y acciones", "GET", "/bookings", auth("clientToken"),
    tests=status(200) + "\n" + schema("clientBookings") + """
pm.test("La reserva aparece en próximas con su estado y acciones coherentes", function () {
    const b = pm.response.json();
    pm.expect(b.empty).to.eql(false);
    const item = b.upcoming.find(i => i.id === pm.environment.get("bookingId"));
    pm.expect(item, "reserva en upcoming").to.exist;
    pm.expect(item.code).to.eql(pm.environment.get("bookingCode"));
    pm.expect(item.rescheduleCount).to.eql(1);
    pm.expect(item.actions.canCancel).to.eql(true);
    pm.expect(item.actions.canReschedule, "límite alcanzado").to.eql(false);
    pm.expect(item.actions.receiptPath).to.eql("/api/v1/bookings/" + item.id + "/receipt");
});
""" + TIME, desc="HU-017. Positivo.")

add(F4, "Descargar comprobante en PDF", "GET", "/bookings/{{bookingId}}/receipt",
    auth("clientToken") + [{"key": "Accept", "value": "application/pdf"}],
    tests=status(200) + """
pm.test("Content-Type application/pdf y adjunto con el código de la reserva", function () {
    pm.expect(pm.response.headers.get("Content-Type")).to.include("application/pdf");
    pm.expect(pm.response.headers.get("Content-Disposition"))
        .to.include("comprobante-" + pm.environment.get("bookingCode") + ".pdf");
});
pm.test("El cuerpo es un PDF válido (firma %PDF y cierre %%EOF)", function () {
    const text = pm.response.text();
    pm.expect(text.substring(0, 5)).to.eql("%PDF-");
    pm.expect(text).to.include("%%EOF");
    pm.expect(pm.response.responseSize).to.be.above(500);
});
""" + TIME, desc="HU-017. Positivo.")

add(F4, "Otro cliente intenta ver una reserva ajena", "GET", "/bookings/{{bookingId}}", auth("clientBToken"),
    tests=problem(404, "RESOURCE_NOT_FOUND") + "\n" + TIME,
    desc="HU-017. Excepcional: aislamiento entre clientes (no revela si existe).")

# ---------------------------------------------------------------- 05 search
add(F5, "Buscar reservas del proveedor por rango", "GET",
    "/organizations/{{orgId}}/bookings?from={{today}}T05:00:00Z&to={{weekEnd}}T05:00:00Z",
    auth("providerToken"),
    tests=status(200) + "\n" + schema("searchPage") + """
pm.test("Encuentra las dos reservas del proveedor con su resumen", function () {
    const b = pm.response.json();
    pm.expect(b.summary.total).to.eql(2);
    pm.expect(b.summary.byStatus.CONFIRMED).to.eql(2);
    const codes = b.items.map(i => i.code);
    pm.expect(codes).to.include(pm.environment.get("bookingCode"));
    pm.expect(b.items.every(i => i.serviceName === "Masaje QA " + pm.environment.get("runId"))).to.eql(true);
});
""" + TIME, desc="HU-018. Positivo.")

add(F5, "Exportar la búsqueda a CSV", "GET",
    "/organizations/{{orgId}}/bookings/export?from={{today}}T05:00:00Z&to={{weekEnd}}T05:00:00Z",
    auth("providerToken"),
    tests=status(200) + """
pm.test("Content-Type text/csv como adjunto reservas.csv", function () {
    pm.expect(pm.response.headers.get("Content-Type")).to.include("text/csv");
    pm.expect(pm.response.headers.get("Content-Disposition")).to.include("reservas.csv");
});
pm.test("Encabezado esperado y una fila por reserva", function () {
    const lines = pm.response.text().trim().split(/\\r?\\n/);
    pm.expect(lines[0].replace(/^\\uFEFF/, "")).to.eql("codigo,cliente,servicio,sede,inicio,fin,asistentes,estado,importe,moneda");
    pm.expect(lines).to.have.lengthOf(3);
    pm.expect(pm.response.text()).to.include(pm.environment.get("bookingCode"));
});
""" + TIME, desc="HU-018. Positivo.")

# ---------------------------------------------------------------- 06 attendance + exceptions
add(F6, "Registrar asistencia antes de la hora de inicio", "POST",
    "/organizations/{{orgId}}/bookings/{{bookingId}}/attendance", JSON_HDR + auth("providerToken"),
    {"status": "COMPLETED", "observation": "Marcado anticipado"},
    tests=problem(409, "INVALID_STATUS_TRANSITION") + "\n" + TIME,
    desc="HU-016. Excepcional: no se cierra una reserva que no ha empezado.")

add(F6, "Cerrar la agenda un día (excepción)", "POST",
    "/organizations/{{orgId}}/agendas/{{agendaId}}/exceptions/closures", JSON_HDR + auth("providerToken"),
    {"date": "{{closedDate}}", "reason": "Mantenimiento general"},
    tests=status(201) + """
pm.test("Queda una excepción CLOSED para la fecha", function () {
    const b = pm.response.json();
    pm.expect(b.type).to.eql("CLOSED");
    pm.expect(b.date).to.eql(pm.environment.get("closedDate"));
    pm.expect(b.agendaId).to.eql(pm.environment.get("agendaId"));
});
""" + TIME, desc="HU-007. Positivo.")

add(F6, "El día cerrado no ofrece franjas", "GET",
    "/booking/availability?serviceId={{serviceId}}&locationId={{locationId}}&date={{closedDate}}",
    auth("clientToken"),
    tests=status(200) + """
pm.test("La disponibilidad del día cerrado está vacía", function () {
    pm.expect(pm.response.json()).to.be.an("array").that.is.empty;
});
""" + TIME, desc="HU-007/HU-011. Positivo: la excepción se refleja en la disponibilidad.")

# ---------------------------------------------------------------- 07 cancel
add(F7, "Cancelar sin confirmar", "POST", "/bookings/{{bookingId}}/cancel", JSON_HDR + auth("clientToken"),
    {"reason": "Viaje imprevisto"},
    tests=problem(409, "CONFIRMATION_REQUIRED") + """
pm.test("Informa que no hay penalidad dentro de la ventana de 120 min", function () {
    const d = pm.response.json().details;
    pm.expect(d.penaltyApplies).to.eql("false");
    pm.expect(d.freeCancellationWindowMinutes).to.eql("120");
});
""" + TIME, desc="HU-013. Excepcional: la cancelación exige confirmación explícita.")

add(F7, "Cancelar con confirmación", "POST", "/bookings/{{bookingId}}/cancel", JSON_HDR + auth("clientToken"),
    {"confirm": True, "reason": "Viaje imprevisto"},
    tests=status(200) + """
pm.test("La reserva queda CANCELLED sin penalidad", function () {
    const b = pm.response.json();
    pm.expect(b.id).to.eql(pm.environment.get("bookingId"));
    pm.expect(b.status).to.eql("CANCELLED");
    pm.expect(b.lateCancellation).to.eql(false);
});
""" + TIME, desc="HU-013. Positivo.")

# ---------------------------------------------------------------- 08 inbox + reports
add(F8, "Bandeja de correos del cliente", "GET", "/notifications/emails", auth("clientToken"),
    pre="""// Arrange: los avisos salen por un proceso periódico (APP_BOOKING_NOTICES_POLL_MS=1000 en el ambiente)
setTimeout(function () {}, 2500);""",
    tests=status(200) + """
pm.test("Hay avisos de la reserva con su código", function () {
    const mails = pm.response.json();
    pm.expect(mails).to.be.an("array").that.is.not.empty;
    const code = pm.environment.get("bookingCode");
    const related = mails.filter(m => (m.subject + " " + m.body).includes(code));
    pm.expect(related.length, "correos que mencionan " + code).to.be.at.least(1);
    mails.forEach(m => pm.expect(m).to.have.all.keys("id", "subject", "body", "sentAt"));
});
""" + TIME, desc="HU-015. Positivo.")

add(F8, "Reporte de ocupación de la semana", "GET",
    "/organizations/{{orgId}}/reports/occupancy?from={{today}}&to={{weekEnd}}", auth("providerToken"),
    tests=status(200) + "\n" + schema("occupancy") + """
pm.test("La agenda reporta 60 franjas, 1 ocupada y una tasa coherente", function () {
    const row = pm.response.json().find(r => r.agendaId === pm.environment.get("agendaId"));
    pm.expect(row, "fila de la agenda").to.exist;
    // 7 días x 10 franjas, menos el día cerrado por la excepción (HU-007)
    pm.expect(row.totalSlots).to.eql(60);
    pm.expect(row.occupiedSlots).to.eql(1);
    const rate = row.occupancyRate > 1 ? row.occupancyRate / 100 : row.occupancyRate;
    pm.expect(rate).to.be.closeTo(row.occupiedSlots / row.totalSlots, 0.01);
});
""" + TIME, desc="HU-019. Positivo: solo cuenta la reserva vigente del cliente B.")

add(F8, "Reporte de demanda por servicio", "GET",
    "/organizations/{{orgId}}/reports/service-demand?from={{today}}&to={{weekEnd}}", auth("providerToken"),
    tests=status(200) + "\n" + schema("demand") + """
pm.test("El servicio registra 2 reservas creadas, 1 cancelada (50 %) e ingreso de la vigente", function () {
    const svc = pm.response.json().services.find(s => s.id === pm.environment.get("serviceId"));
    pm.expect(svc, "servicio en el reporte").to.exist;
    pm.expect(svc.current.created).to.eql(2);
    pm.expect(svc.current.cancelled).to.eql(1);
    pm.expect(Number(svc.current.cancellationRatePercent)).to.be.closeTo(50, 0.01);
    pm.expect(Number(svc.current.estimatedRevenueByCurrency.COP), "solo la reserva vigente").to.eql(80000);
});
""" + TIME, desc="HU-020. Positivo.")

# ---------------------------------------------------------------- 09 role change + audit
add(F9, "Cambiar el rol del cliente B a PROVIDER", "PATCH", "/admin/users/{{clientBId}}/role",
    JSON_HDR + auth("adminToken"), {"role": "PROVIDER"},
    tests=status(200) + """
pm.test("Confirma el usuario y el nuevo rol", function () {
    const b = pm.response.json();
    pm.expect(b.id).to.eql(pm.environment.get("clientBId"));
    pm.expect(b.role).to.eql("PROVIDER");
});
""" + TIME, desc="HU-022. Positivo.")

add(F9, "El token emitido antes del cambio de rol queda rechazado", "GET", "/bookings", auth("clientBToken"),
    tests="""pm.test("Código de estado 401", function () {
    pm.response.to.have.status(401);
});
pm.test("Contrato de error: problem+json con errorCode de autenticación", function () {
    pm.expect(pm.response.headers.get("Content-Type")).to.include("application/problem+json");
    pm.expect(pm.response.json().errorCode).to.be.oneOf(["AUTH_REQUIRED", "AUTH_TOKEN_INVALID"]);
});
""" + TIME, desc="HU-022. Excepcional: la sesión anterior no conserva el rol viejo.")

add(F9, "Tras volver a iniciar sesión, el usuario opera con el rol nuevo", "GET", "/auth/me",
    auth("clientBNewToken"),
    pre="""// Arrange: nuevo inicio de sesión (al menos 1 s después del cambio, la marca amr tiene resolución de segundos)
setTimeout(function () {
    pm.sendRequest({ url: pm.environment.get("baseUrl") + "/auth/login", method: "POST",
        header: { "Content-Type": "application/json" },
        body: { mode: "raw", raw: JSON.stringify({ email: "qa.cliente.b." + pm.environment.get("runId") + "@example.com",
                                                   password: pm.environment.get("password") }) }
    }, function (err, res) { pm.environment.set("clientBNewToken", res.json().accessToken); });
}, 1200);""",
    tests=status(200) + """
pm.test("El perfil refleja el rol PROVIDER", function () {
    const b = pm.response.json();
    pm.expect(b.id).to.eql(pm.environment.get("clientBId"));
    pm.expect(b.role).to.eql("PROVIDER");
});
""" + TIME, desc="HU-022. Positivo.")

add(F9, "Auditoría del proveedor: reservas creadas y reprogramadas", "GET",
    "/organizations/{{orgId}}/audit-events?size=100", auth("providerToken"),
    tests=status(200) + "\n" + schema("auditPage") + """
pm.test("Registra creación, reprogramación y cancelación de la reserva con su actor", function () {
    const items = pm.response.json().items.filter(e => e.resourceId === pm.environment.get("bookingId"));
    const actions = items.map(e => e.action);
    pm.expect(actions).to.include.members(["BOOKING_CREATED", "BOOKING_RESCHEDULED"]);
    pm.expect(actions.some(a => a.includes("CANCELLED")), "acción de cancelación").to.eql(true);
    const created = items.find(e => e.action === "BOOKING_CREATED");
    pm.expect(created.actorId).to.eql(pm.environment.get("clientAId"));
    pm.expect(created.actorRole).to.eql("CLIENT");
    pm.expect(created.result).to.eql("SUCCESS");
});
""" + TIME, desc="HU-023. Positivo: trazabilidad de la reserva dentro del proveedor.")

add(F9, "Auditoría global: cambio de rol registrado", "GET",
    "/admin/audit-events?action=PLATFORM_ROLE_CHANGED", auth("adminToken"),
    tests=status(200) + "\n" + schema("auditPage") + """
pm.test("El cambio de rol aparece con el administrador como actor", function () {
    const ev = pm.response.json().items.find(e => e.resourceId === pm.environment.get("clientBId"));
    pm.expect(ev, "evento del cliente B").to.exist;
    pm.expect(ev.resourceType).to.eql("USER");
    pm.expect(ev.actorRole).to.eql("ADMIN");
});
""" + TIME, desc="HU-023. Positivo.")

# ---------------------------------------------------------------- build
folders = {}
for i, (folder, name, method, path, headers, req_body, pre, tests, desc) in enumerate(ITEMS, 1):
    at = f"AT-{i:02d}"
    url_raw = "{{baseUrl}}" + path
    base, _, query = path.partition("?")
    url = {"raw": url_raw, "host": ["{{baseUrl}}"], "path": [p for p in base.strip("/").split("/")]}
    if query:
        url["query"] = [{"key": kv.split("=", 1)[0], "value": kv.split("=", 1)[1]} for kv in query.split("&")]
    events = []
    if pre:
        events.append({"listen": "prerequest", "script": {"type": "text/javascript", "exec": pre.split("\n")}})
    test_src = ("// Act: Newman envía la petición definida en este ítem (método, URL, cabeceras y cuerpo)\n"
                "// Assert: código de estado, cuerpo o esquema JSON, contrato de error y tiempo de respuesta\n" + tests)
    events.append({"listen": "test", "script": {"type": "text/javascript", "exec": test_src.split("\n")}})
    request = {"method": method, "header": headers, "url": url, "description": desc}
    if req_body is not None:
        request["body"] = body(req_body)
    folders.setdefault(folder, []).append({"name": f"{at} {name}", "event": events, "request": request})

collection = {
    "info": {
        "_postman_id": str(uuid.uuid5(uuid.NAMESPACE_URL, "bookingplatform-sprint3-api")),
        "name": "bookingplatform - Pruebas de API Sprint 3",
        "description": "Pruebas de API automatizadas del flujo de valor del Sprint 3 (HU-007, HU-008/011, HU-012 a "
                       "HU-020, HU-022, HU-023). Las peticiones se encadenan por variables de entorno y deben correr "
                       "en orden sobre una base nueva o con datos de otra corrida (cada corrida usa un runId propio). "
                       "Los tokens los emite el simulador de Supabase Auth (qa/api/stub).",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
    "item": [{"name": f, "item": items} for f, items in folders.items()],
}
json.dump(collection, open(OUT, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
print(len(ITEMS), "casos")
