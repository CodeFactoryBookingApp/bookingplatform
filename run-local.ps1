#Requires -Version 5.1
<#
.SYNOPSIS
  Ejecucion local: carga .env a variables de entorno del proceso y arranca la app.
.EXAMPLE
  .\run-local.ps1
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
  Write-Error "No existe .env en $root. Copialo desde .env.example y completa tus credenciales."
}
Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^\s*[^#\s=]+=' } | ForEach-Object {
  $k, $v = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim().Trim('"').Trim("'"), 'Process')
}
& (Join-Path $root 'mvnw.cmd') spring-boot:run @args
