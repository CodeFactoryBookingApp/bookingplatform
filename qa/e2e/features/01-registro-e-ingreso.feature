# language: es
@registro-ingreso
Característica: Registro de clientes, verificación del correo e ingreso
  Como persona que quiere reservar servicios
  Quiero crear mi cuenta, verificar mi correo e ingresar
  Para poder reservar en la plataforma

  @E2E-01 @HU-001 @HU-021 @positivo
  Escenario: Una persona se registra, verifica su correo con el enlace e ingresa como cliente
    Dado que una persona adulta abre el formulario de registro
    Cuando completa el formulario con datos válidos y envía el registro
    Entonces ve que debe revisar su correo para verificar la cuenta
    Cuando abre el enlace de verificación que recibió
    Entonces ve que su correo quedó confirmado
    Y al ingresar con su correo y contraseña entra a la plataforma como cliente

  @E2E-02 @HU-001 @excepcional
  Escenario: Un menor de edad no puede registrarse
    Dado que una persona de 17 años abre el formulario de registro
    Cuando completa el formulario con su fecha de nacimiento y envía el registro
    Entonces ve el error "Debes ser mayor de 18 años" y sigue en el formulario

  @E2E-03 @HU-021 @excepcional
  Escenario: Ingreso con contraseña errada
    Dado que existe una clienta verificada
    Cuando intenta ingresar con su correo y la contraseña "OtraClave2026!"
    Entonces ve el mensaje "Correo o contraseña incorrectos." y no inicia sesión
