# language: es
@avisos
Característica: Bandeja de avisos de reservas
  Como clienta
  Quiero recibir en mi bandeja los avisos de mis reservas
  Para tener constancia de cada cambio

  @E2E-12 @HU-015 @positivo
  Escenario: La clienta recibe en su bandeja el aviso de la reserva creada
    Dado que existe una clienta verificada
    Y la clienta tiene una reserva confirmada hecha desde otro dispositivo dentro de 7 días
    Y la clienta inició sesión
    Cuando abre su bandeja de avisos
    Entonces encuentra el aviso "Reserva creada" con el código, el servicio y el estado "Confirmada"
