# language: es
@reservas-cliente
Característica: Reservas de la clienta
  Como clienta verificada
  Quiero reservar, cancelar, reprogramar y consultar mis reservas
  Para gestionar mis citas sin llamar al negocio

  Antecedentes:
    Dado que existe una clienta verificada
    Y la clienta inició sesión

  @E2E-05 @HU-012 @positivo
  Escenario: La clienta reserva una franja disponible desde el catálogo
    Cuando busca en el catálogo el servicio del negocio de prueba y elige reservarlo
    Y elige el día dentro de 3 días y la primera franja libre
    Entonces el resumen muestra el negocio, el precio y la cancelación gratuita hasta 1 día antes
    Cuando confirma la reserva
    Entonces ve el comprobante "Reserva confirmada" con el código de la reserva
    Y la reserva aparece en "Mis reservas" como "Confirmada"

  @E2E-06 @HU-013 @positivo
  Escenario: La clienta cancela una reserva dentro de la ventana de cancelación gratuita
    Dado que la clienta reservó por la web una franja dentro de 4 días
    Cuando abre "Mis reservas" y pide cancelar esa reserva con el motivo "Me surgió un viaje"
    Entonces se le informa "Cancelación sin penalidad"
    Cuando confirma la cancelación
    Entonces ve el aviso "Reserva cancelada." y la reserva queda "Cancelada"

  # Defecto BUG-070: el backend ya reprograma (POST /bookings/{id}/reschedule, CP14-01) pero
  # "Mis reservas" no ofrece la acción. Quitar @fixme cuando el front la tenga.
  @E2E-07 @HU-014 @CP14-01 @positivo @defecto @fixme
  Escenario: La clienta reprograma una reserva confirmada a otra franja libre
    Dado que la clienta reservó por la web una franja dentro de 5 días
    Cuando abre "Mis reservas" y elige reprogramar esa reserva
    Entonces puede elegir una nueva franja para la misma reserva

  # Defecto BUG-071: el backend ya lista el historial (GET /bookings) y genera el PDF
  # (GET /bookings/{id}/receipt), pero "Mis reservas" solo lee el localStorage del navegador.
  @E2E-08 @HU-017 @CP17-01 @CP17-04 @positivo @defecto @fixme
  Escenario: La clienta ve en su historial una reserva hecha desde otro dispositivo y descarga el comprobante PDF
    Dado que la clienta tiene una reserva confirmada hecha desde otro dispositivo dentro de 6 días
    Cuando abre "Mis reservas"
    Entonces ve esa reserva entre las próximas
    Y puede descargar el comprobante PDF de la reserva
