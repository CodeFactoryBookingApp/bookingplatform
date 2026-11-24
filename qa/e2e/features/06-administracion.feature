# language: es
@administracion
Característica: Administración de la plataforma
  Como administradora de la plataforma
  Quiero aprobar proveedores y cambiar roles
  Para controlar quién ofrece servicios

  Antecedentes:
    Dado que la administradora inició sesión

  @E2E-10 @HU-002 @positivo
  Escenario: La administradora aprueba un proveedor pendiente
    Dado que un negocio nuevo se registró y está pendiente de aprobación
    Cuando la administradora abre la lista de proveedores
    Entonces el negocio aparece como "Pendiente de aprobación"
    Cuando lo aprueba con el motivo "RUT y cámara de comercio verificados"
    Entonces ve el aviso "Proveedor aprobado" y el negocio queda "Activo"

  @E2E-11 @HU-022 @CP22-01 @positivo
  Escenario: La administradora cambia el rol de una clienta a proveedora
    Dado que existe una clienta verificada
    Cuando la administradora abre la lista de clientes y cambia el rol de la clienta a "Proveedor"
    Entonces ve el aviso "Rol cambiado a proveedor"
    Cuando la administradora sale y la clienta ingresa de nuevo
    Entonces la usuaria entra al área de negocio como proveedora
