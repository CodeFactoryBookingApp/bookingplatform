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

El backend no configura CORS (`SecurityConfig`), así que el navegador debe llamar a `/api` en su propio origen.
En desarrollo lo hace Vite (`vite.config.ts`); en Vercel lo hace `vercel.json` (rewrites hacia Render).

## Despliegue en Vercel

Importa la carpeta `front/` como proyecto (framework Vite). No requiere variables de entorno.

## Rutas

| Ruta | Quién | Pantalla |
|---|---|---|
| `/` | todos | Catálogo con filtros |
| `/ingresar`, `/registro`, `/registro-negocio`, `/recuperar` | anónimos | Acceso y registro |
| `/verificar-correo`, `/restablecer-clave` | todos | Destino de los enlaces de correo (`token_hash` o `token`) |
| `/bandeja` | autenticados | Correos de la plataforma |
| `/negocio`, `/negocio/:id/...` | PROVIDER, ADMIN | Resumen, tipos, recursos, servicios, agendas |
| `/admin/proveedores`, `/admin/clientes` | ADMIN | Listados, acciones con motivo, CSV |

## Límites conocidos del backend

- No hay endpoint de refresh: al vencer el token se pide iniciar sesión de nuevo.
- No hay módulo de reservas todavía, por eso no hay flujo de reserva.
- El rol dentro del negocio (OWNER/MANAGER/STAFF) no se expone; si falta permiso, el backend responde 403 y se muestra el motivo.
- Un usuario ADMIN se asigna a mano en Supabase (`app_metadata.role = "admin"`).
