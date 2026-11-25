# language: es
@proveedor
Característica: Reservas del negocio vistas por el proveedor
  Como dueño de un negocio aprobado
  Quiero consultar las reservas de mis clientes
  Para preparar la atención

  @E2E-09 @HU-018 @positivo
  Escenario: El proveedor encuentra por código la reserva de una clienta
    Dado que existe una clienta verificada
    Y la clienta tiene una reserva confirmada hecha desde otro dispositivo dentro de 8 días
    Y el proveedor del negocio de prueba inició sesión
    Cuando abre las reservas de su negocio y filtra por el código de esa reserva
    Entonces ve la reserva con el nombre de la clienta, el servicio, el importe y el estado "Confirmada"
