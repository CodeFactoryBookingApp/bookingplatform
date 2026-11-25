# language: es
@catalogo
Característica: Catálogo público de servicios
  Como visitante sin cuenta
  Quiero buscar servicios de negocios aprobados
  Para comparar antes de registrarme y reservar

  @E2E-04 @HU-005 @positivo
  Escenario: Un visitante busca un servicio y ve sus datos; una búsqueda sin coincidencias lo indica
    Dado que un visitante abre el catálogo sin iniciar sesión
    Cuando busca "corte de cabello"
    Entonces ve el servicio del negocio de prueba con su sede, duración y precio
    Cuando busca "servicio que no existe xyz"
    Entonces ve el mensaje de que no se encontraron servicios
