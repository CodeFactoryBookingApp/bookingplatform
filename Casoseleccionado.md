# **Presentación** 

Este documento contiene un caso empresarial que será desarrollados por el equipos durante el semestre. Este caso representa un problema real de negocio que requiere una solución de software robusta, con enfoque en arquitectura backend robusta, calidad, seguridad y gestión ágil. 

El objetivo no es únicamente construir un sistema funcional, sino demostrar: 

- Madurez arquitectónica 

- Dominio de reglas de negocio 

- Implementación técnica sólida 

- Calidad de código verificable 

- Gestión profesional del proyecto 

# **CASO SELECCIONADO** 

## **Plataforma de Reservas de Servicios** 

## **Contexto de negocio** 

Negocios como clínicas, consultorios, salones de belleza o centros deportivos dependen de sistemas de reservas para gestionar citas y disponibilidad de recursos. 

La falta de herramientas adecuadas genera sobreocupación, cancelaciones desordenadas y dificultades para gestionar agendas. 

La empresa busca desarrollar una plataforma que permita administrar reservas de servicios. 

Desarrollar una aplicación que permita gestionar reservas de servicios o eventos. 

Contemplar: 

- Registro de usuarios y proveedores de servicios 

- Definición de agendas y horarios disponibles 

- Creación y cancelación de reservas 

- Control de disponibilidad de recursos 

- Registro de historial de reservas realizadas 

- Reportes sobre ocupación y uso de los servicios 

## **Valor agregado para el negocio** 

- Optimización del uso de recursos 

- Mejora en la experiencia del cliente 

- Mayor eficiencia en la gestión de agendas 

# **Lineamientos** 

## **Perfiles tecnológicos armonizados** 

**Propósito:** Backend robusto integrado a base de datos, con arquitectura, seguridad, calidad y operación verificables. 

**Frontend:** N/A 

**Backend:** SpringBoot 3.X o superior (JDK 11 o superior) 

**Datos:** PostgreSQL; Supabase o Neon como opción de servicio administrado. 

**API:** REST con contratos bien definidos, versionado, validación y documentación. 

**Despliegue:** Render mínimo en nube con contenedores (Ejemplo Docker); evolución opcional a orquestación con Kubernetes. 

## **Alcance funcional y arquitectura** 

- Desarrollar un backend con integración a base de datos, organizado en dominios o módulos de negocio y, al menos, un módulo transversal: autenticación y autorización, auditoría, logging u observabilidad. 

## **Diseño y Construcción de APIs**
*   **Exposición de Contratos:** Publicación estricta del contrato usando OpenAPI/Swagger versionado (REST).
*   **REST:**
    *   Diseño orientado a recursos.
    *   Versionado explícito.
    *   Respuestas uniformes de error con códigos HTTP correctos y estructura estándar (ej. `errorCode`, `message`, `details`, `traceId`).
*   **Reglas de Negocio:** Las operaciones no pueden reducirse a simples CRUD. Deben encapsular lógica de negocio real.
*   **Validación:** Validación robusta de _payloads_ (entradas del usuario) a nivel del servidor.
*   **Observabilidad:** Generación de logs estructurados (ej. en formato JSON) correlacionados mediante `traceId`.

## **Entregables por curso y sprint** 

### **Arquitectura de Software** 

**Sprint 1:** Diagrama de Paquetes y componentes con interfaces; estilo preliminar; proyecto base Spring Boot en GitHub; al menos una HU implementada; despliegue inicial. Mínimo 3 ADR priorizados. 

### **Bases de Datos** 

**Sprint 1:** Entidades y relaciones; preguntas/consultas clave; modelo lógico; modelo físico inicial. 

## **Gestión de código y configuración** 

- Utilizar GitHub como repositorio de código y Azure DevOps para backlog y métricas, salvo decisión diferente a nivel de CodeF@ctory. 

- Adoptar trunk-based development con ramas de vida corta y una rama principal protegida. 

- Usar nombres como feature/-, sin espacios ni caracteres problemáticos. 

- Integrar mediante pull request, revisión por pares y comprobaciones automáticas obligatorias. 

- Mantener cambios pequeños y frecuentes; actualizar la rama antes de integrar y evitar commits directos a la rama protegida. 

- Usar inglés y convenciones idiomáticas en código: PascalCase para clases Java y camelCase para variables y métodos. 

## **HUs propuestas para el sprint 1:** 

### **HU-001 – Registro de usuario cliente** 

**Como** cliente **Quiero** registrarme en la plataforma con mis datos personales y de contacto **Para** poder reservar servicios y consultar el historial de mis citas. 

### **Reglas de negocio:** 

- Autorregistro con: nombre, documento, fecha de nacimiento, correo, teléfono, ciudad y canal de notificación preferido. 

- El cliente recibe correo de verificación y su cuenta queda en estado PENDIENTE_VERIFICACION hasta confirmar. 

- Solo un cliente verificado puede confirmar reservas. 

- No se permite autorregistro de menores de 18 años. 

- Correo y documento son únicos. 

### **Criterios de aceptación:** 

- Registro exitoso de un cliente mayor de edad. 

- Rechazo por correo o documento ya registrado. 

- Rechazo por formato inválido de correo o teléfono. 

- Rechazo de menores de edad. 

- Cliente no verificado no puede confirmar reserva. 

- Reenvío del correo de verificación con enlace vigente. 

### **HU-002 – Registro y configuración de proveedor de servicios** 

**Como** proveedor de servicios **Quiero** registrar mi negocio con sus datos, sedes y parámetros de operación **Para** poder publicar mis servicios y recibir reservas de clientes. 

### **Reglas de negocio:** 

- Registro de negocio: razón social, NIT, categoría, contacto, zona horaria y al menos una sede. 

- Configuración de reglas de operación: anticipación mínima y máxima para reservar, ventana de cancelación sin penalidad. 

- Estado inicial: PENDIENTE_APROBACION hasta que un administrador lo valide. 

- NIT único. 

- Anticipación mínima no puede ser mayor que la máxima. 

- Proveedor pendiente no es visible ni reservable. 

- Cambios de parámetros aplican a nuevas reservas; las vigentes conservan sus condiciones originales. 

### **Criterios de aceptación:** 

- Registro exitoso con al menos una sede. 

- Rechazo por NIT o documento duplicado. 

- Rechazo por anticipación mínima mayor que la máxima. 

- Proveedor pendiente no visible en catálogo. 

- Aprobación por administrador cambia estado a ACTIVO y notifica por correo. 

- Edición de parámetros sin afectar reservas vigentes. 

### **HU-003 – Listado y administración de usuarios con filtros** 

**Como** administrador de la plataforma **Quiero** ver un listado de clientes y proveedores con filtros, ordenamiento y cambio de estado **Para** supervisar la base de usuarios y gestionar aprobaciones, suspensiones y reactivaciones. 

### **Reglas de negocio:** 

- Lista paginada separada por clientes y proveedores. 

- Filtros combinables: estado, categoría, ciudad, fecha de registro. 

- Acciones de aprobar, suspender o reactivar exigen motivo. 

- Suspensión de proveedor con reservas futuras exige confirmación, informe de reservas afectadas y motivo. 

- Exportación a CSV del listado filtrado. 

### **Criterios de aceptación:** 

- Listado paginado con filtros combinados y total de resultados. 

- Ordenamiento por columna ascendente/descendente. 

- Restablecer filtros. 

- Suspensión con confirmación, informe y motivo. 

- Exportación CSV del listado filtrado. 

### **HU-004 – Definición de servicios ofertados** 

**Como** proveedor de servicios **Quiero** crear y configurar los servicios que ofrezco, con su duración, precio y recursos requeridos **Para** que los clientes puedan reservarlos según la disponibilidad real de mi negocio. 

### **Reglas de negocio:** 

- Cada servicio incluye: nombre, descripción, sedes donde se presta, duración, tiempos de preparación y limpieza, precio, capacidad (individual o grupal) y tipos de recurso necesarios. 

- Servicios se pueden activar o desactivar sin eliminarlos. 

- Nombre de servicio único por proveedor. 

- Servicio grupal exige cupo máximo mayor a 1. 

- Servicio con reservas asociadas no se elimina; se desactiva. 

- Cambios de duración o tiempos no afectan reservas existentes. 

### **Criterios de aceptación:** 

- Creación de servicio con recurso requerido. 

- Rechazo por nombre duplicado. 

- Servicio grupal con cupo válido. 

- Desactivación de servicio con reservas. 

- Cambio de duración no afecta reservas existentes. 

### **HU-021 – Autenticación de usuarios del sistema** **_(transversal)_** 

**Como** usuario del sistema **Quiero** iniciar sesión de forma segura con mis credenciales **Para** acceder a las funcionalidades de la plataforma según mi rol asignado. 

### **Reglas de negocio:** 

- Clientes, personal de proveedores y administradores inician y cierran sesión con correo y contraseña. 

- Políticas de seguridad de contraseñas. 

- Bloqueo tras varios intentos fallidos. 

- Recuperación de contraseña por correo con enlace de un solo uso. 

- MFA obligatorio para administradores. 

### **Criterios de aceptación:** 

- Inicio de sesión exitoso según rol. 

- Rechazo de contraseña que no cumple política. 

- Bloqueo temporal tras intentos fallidos. 

- Recuperación de contraseña con enlace de un solo uso. 

- Cierre de sesión invalida la sesión. 

## **Objetivo del sprint 1:** 

Establecer las bases arquitectónicas, de datos y de seguridad del backend, dejando un proyecto funcional, desplegado y documentado. 
