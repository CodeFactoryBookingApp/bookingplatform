# Booking Platform, frontend

React 19 + Vite + TypeScript + Tailwind 4. Consume la API del backend sin modificarlo y sin ninguna key.

## Ejecutar

```bash
cd front
npm install
npm run dev        # http://localhost:3000
```

El puerto 3000 es intencional: los enlaces de los correos de Supabase apuntan al Site URL (`localhost:3000`).

Por defecto el proxy de Vite envía `/api` al backend de Render. Para usar un backend local:

```bash
cp .env.example .env     # y pon VITE_API_TARGET=http://localhost:8080
```

Render (plan gratis) duerme el servicio; la primera petición puede tardar ~2 minutos y la app lo avisa.

## Por qué hay un proxy

`SecurityConfig` llama a `.cors(withDefaults())`, pero no define ningún `CorsConfigurationSource`, así que en la
práctica el backend no admite peticiones de otro origen. Por eso el navegador llama a `/api` en su propio origen.
En desarrollo lo hace Vite (`vite.config.ts`); en Vercel lo hace `vercel.json` (rewrites hacia Render).

## Despliegue en Vercel

Importa la carpeta `front/` como proyecto (framework Vite). No requiere variables de entorno.

Las reservas (HU-012 y HU-013) vienen de la rama `feature/booking-lifecycle`. Mientras esa rama no llegue a `main`,
el backend de Render no tiene esos endpoints: para probarlas usa un backend local (`VITE_API_TARGET`).

## Rutas

| Ruta | Quién | Pantalla |
|---|---|---|
| `/` | todos | Catálogo con filtros y botón Reservar |
| `/ingresar`, `/registro`, `/registro-negocio`, `/recuperar` | anónimos | Acceso y registro |
| `/verificar-correo`, `/restablecer-clave` | todos | Destino de los enlaces de correo (`token_hash` o `token`) |
| `/bandeja` | autenticados | Correos de la plataforma |
| `/preferencias` | autenticados | Recordatorios y horas de silencio (HU-015) |
| `/reservar/:servicio/:sede` | CLIENT | Disponibilidad, resumen, confirmación y comprobante (HU-012) |
| `/mis-reservas` | CLIENT | Reservas hechas en este navegador y su cancelación (HU-013) |
| `/negocio` | PROVIDER | Mis negocios |
| `/negocio/:id` | PROVIDER, ADMIN | Resumen: datos, reglas (con confirmación manual) y recordatorios (HU-015). El ADMIN solo lo ve, sin editar |
| `/negocio/:id/reservas` | PROVIDER | Búsqueda, resumen, CSV, confirmar y cancelar reservas (HU-018, HU-012, HU-013) |
| `/negocio/:id/reportes` | PROVIDER | Demanda por servicio, comparación con el periodo anterior y mapa día × hora (HU-020) |
| `/negocio/:id/servicios`, `agendas`, `recursos`, `tipos` | PROVIDER | Catálogo propio y horarios |
| `/negocio/:id/auditoria` | PROVIDER | Auditoría del negocio y CSV (HU-023) |
| `/admin/proveedores`, `/admin/clientes` | ADMIN | Listados, acciones con motivo, CSV y cambio de rol (HU-022) |
| `/admin/auditoria` | ADMIN | Auditoría de la plataforma y CSV (HU-023) |

## Límites conocidos del backend

- No hay endpoint de refresh: al vencer el token se pide iniciar sesión de nuevo.
- No hay un endpoint que liste las reservas del cliente. `/mis-reservas` guarda en `localStorage` los comprobantes
  recibidos en este navegador (`src/lib/myBookings.ts`), así que puede estar vacío en otro dispositivo y el estado
  puede estar desactualizado.
- Ver disponibilidad y el resumen también exige el rol CLIENT, así que un visitante debe iniciar sesión antes de ver
  horarios.
- El rol dentro del negocio (OWNER/MANAGER/STAFF) no se expone. Un STAFF ve botones que el backend rechaza con 403;
  el mensaje lo explica.
- Un ADMIN que no es miembro de un negocio recibe 404 en sus secciones (`OrganizationAccess`); por eso solo ve el
  resumen.
- Un token vencido responde `AUTH_REQUIRED` en lugar de `AUTH_TOKEN_INVALID` (BUG-008 del backend).
- Un usuario ADMIN se asigna a mano en Supabase (`app_metadata.role = "admin"`).
