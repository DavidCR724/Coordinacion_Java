<#
  CoordinadITO · Conecta el envío de correos (códigos de acceso y avisos) a una cuenta de Gmail.

  Antes: crea una CONTRASEÑA DE APLICACIÓN en tu cuenta de Google (no es tu contraseña normal):
    1. https://myaccount.google.com/security → activa la Verificación en dos pasos.
    2. https://myaccount.google.com/apppasswords → nombre "CoordinadITO" → Crear.
    3. Copia las 16 letras que aparecen (puedes pegarlas con o sin espacios).

  Ejecútalo en PowerShell, con el dominio de Payara encendido o apagado:

      cd C:\Users\<tu-usuario>\Documents\NetBeansProjects\CoordinadITO
      powershell -ExecutionPolicy Bypass -File herramientas\configurar-correo.ps1

  Qué hace:
    1. Pide tu dirección de Gmail y la contraseña de aplicación (sin mostrarla).
    2. La PRUEBA: te manda un correo de prueba a ti mismo por smtp.gmail.com:587. Si Google la
       rechaza, se detiene sin cambiar nada.
    3. La guarda como alias cifrado de Payara (coordinadito-correo): en domain.xml solo queda
       ${ALIAS=coordinadito-correo}, nunca la contraseña.
    4. Crea (o rehace) la sesión de correo mail/coordinadito que usa CorreoService.

  No hace falta volver a desplegar: el login muestra «Enviarme un código» en cuanto existe la sesión.
  Límite de Gmail personal: unos 500 correos al día. Para quitarlo: -Quitar.
#>
param(
    [string]$PayaraHome  = "$env:USERPROFILE\Payara_Server",
    [string]$Dominio     = "coordinadito",
    [int]   $PuertoAdmin = 4848,

    [string]$Gmail       = "",
    [string]$Servidor    = "smtp.gmail.com",
    [int]   $Puerto      = 587,
    [switch]$SinPrueba,
    [switch]$Quitar
)

$ErrorActionPreference = "Stop"
$Recurso = "mail/coordinadito"
$Alias   = "coordinadito-correo"

$asadmin = Join-Path $PayaraHome "bin\asadmin.bat"
if (-not (Test-Path $asadmin)) { throw "No encontré $asadmin. Pasa la ruta correcta con -PayaraHome." }

$ErrorActionPreference = "Continue"
& $asadmin version --local *> $null
$javaOk = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = "Stop"
if (-not $javaOk) {
    throw "asadmin no arranca. Payara 7 necesita Java 21: agrega 'set AS_JAVA=<ruta del JDK 21>' al final de $PayaraHome\glassfish\config\asenv.bat."
}

function Plano([Security.SecureString]$s) {
    [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
}

function Asadmin([string[]]$argumentos, [switch]$PuedeFallar) {
    $todos = @("--port", "$PuertoAdmin") + $argumentos
    if ($script:archivoAdmin) { $todos = @("--passwordfile", $script:archivoAdmin) + $todos }
    $ErrorActionPreference = "Continue"
    $salida = & $asadmin @todos 2>&1
    $codigo = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    if ($codigo -ne 0 -and -not $PuedeFallar) { throw "Falló: asadmin $($argumentos[0])`n$($salida | Out-String)" }
    return $salida
}

$script:archivoAdmin = $null
$clave = $null; $adminPlano = $null
try {
    Write-Host "`n== 1/4 Dominio $Dominio" -ForegroundColor Cyan
    $adminPlano = Plano (Read-Host "Contraseña del usuario admin de Payara (Enter si no tiene)" -AsSecureString)
    if ($adminPlano) {
        $script:archivoAdmin = [IO.Path]::GetTempFileName()
        Set-Content -Path $script:archivoAdmin -Value "AS_ADMIN_PASSWORD=$adminPlano" -Encoding ASCII
    }
    & $asadmin list-domains | Out-String | Tee-Object -Variable estado | Out-Null
    if ($estado -notmatch "$Dominio running") {
        & $asadmin start-domain $Dominio
        if ($LASTEXITCODE -ne 0) { throw "No se pudo arrancar el dominio $Dominio. Revisa glassfish\domains\$Dominio\logs\server.log." }
    }
    $existeRecurso = (Asadmin @("list-javamail-resources")) -match "^$([regex]::Escape($Recurso))$"

    if ($Quitar) {
        if ($existeRecurso) { Asadmin @("delete-javamail-resource", $Recurso) | Out-Null }
        Asadmin @("delete-password-alias", $Alias) -PuedeFallar | Out-Null
        Write-Host "Listo: se quitó el envío de correos. El login vuelve a ocultar el acceso con código." -ForegroundColor Green
        return
    }

    Write-Host "`n== 2/4 Cuenta de Gmail" -ForegroundColor Cyan
    if (-not $Gmail) { $Gmail = Read-Host "Tu dirección de Gmail (desde la que saldrán los correos)" }
    $Gmail = $Gmail.Trim().ToLower()
    if ($Gmail -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$') { throw "'$Gmail' no parece una dirección de correo." }
    if ($Gmail -notmatch '@(gmail|googlemail)\.com$') {
        Write-Host "Aviso: no es una cuenta @gmail.com; se usará $Servidor`:$Puerto de todos modos." -ForegroundColor Yellow
    }
    $clave = (Plano (Read-Host "Contraseña de aplicación de 16 letras (no tu contraseña normal)" -AsSecureString)) -replace '\s', ''
    if (-not $clave) { throw "La contraseña de aplicación no puede ir vacía." }
    if ($clave.Length -ne 16) {
        Write-Host "Aviso: las contraseñas de aplicación de Google tienen 16 letras y esta tiene $($clave.Length)." -ForegroundColor Yellow
    }

    Write-Host "`n== 3/4 Prueba de envío" -ForegroundColor Cyan
    if ($SinPrueba) {
        Write-Host "Omitida (-SinPrueba)."
    } else {
        $credencial = New-Object Management.Automation.PSCredential($Gmail, (ConvertTo-SecureString $clave -AsPlainText -Force))
        try {
            # Send-MailMessage está marcado como obsoleto, pero sigue funcionando para esta prueba.
            Send-MailMessage -SmtpServer $Servidor -Port $Puerto -UseSsl -Credential $credencial `
                -From $Gmail -To $Gmail -Encoding UTF8 -WarningAction SilentlyContinue `
                -Subject "CoordinadITO · Prueba de correo" `
                -Body "Si te llegó este correo, CoordinadITO ya puede enviar códigos de acceso y avisos desde esta cuenta."
        } catch {
            throw ("Google no aceptó el envío: $($_.Exception.Message)`n" +
                   "Revisa: 1) que sea una contraseña de APLICACIÓN (requiere la verificación en dos pasos), " +
                   "2) que la dirección sea la misma cuenta que la creó. No se cambió nada en Payara.")
        }
        Write-Host "Enviado: revisa la bandeja de $Gmail (correo «CoordinadITO · Prueba de correo»)." -ForegroundColor Green
    }

    Write-Host "`n== 4/4 Alias $Alias y sesión $Recurso" -ForegroundColor Cyan
    $archivoAlias = [IO.Path]::GetTempFileName()
    try {
        $lineas = @("AS_ADMIN_ALIASPASSWORD=$clave")
        if ($adminPlano) { $lineas += "AS_ADMIN_PASSWORD=$adminPlano" }
        Set-Content -Path $archivoAlias -Value $lineas -Encoding ASCII
        $existeAlias = (& $asadmin --port $PuertoAdmin --passwordfile $archivoAlias list-password-aliases 2>$null) -match "^$Alias$"
        $sub = if ($existeAlias) { "update-password-alias" } else { "create-password-alias" }
        & $asadmin --port $PuertoAdmin --passwordfile $archivoAlias $sub $Alias
        if ($LASTEXITCODE -ne 0) { throw "No se pudo guardar el alias de la contraseña." }
    } finally {
        Remove-Item $archivoAlias -Force -ErrorAction SilentlyContinue
    }

    if ($existeRecurso) { Asadmin @("delete-javamail-resource", $Recurso) | Out-Null }
    # Propiedades separadas por ':'. La contraseña va por alias (Payara la resuelve al crear la sesión).
    # Los tiempos de espera evitan que el login se quede colgado si Gmail no responde.
    $propiedades = @(
        "mail.smtp.port=$Puerto",
        "mail.smtp.auth=true",
        "mail.smtp.starttls.enable=true",
        "mail.smtp.starttls.required=true",
        "mail.smtp.connectiontimeout=10000",
        "mail.smtp.timeout=15000",
        "mail.smtp.writetimeout=15000",
        "mail.smtp.password=`${ALIAS=$Alias}"
    ) -join ":"
    Asadmin @("create-javamail-resource",
        "--mailhost", $Servidor,
        "--mailuser", $Gmail,
        "--fromaddress", $Gmail,
        "--property", $propiedades,
        $Recurso) | Out-Null

    Write-Host "`nListo: CoordinadITO envía correos desde $Gmail." -ForegroundColor Green
    Write-Host "  Abre http://localhost:8080/CoordinadITO-war/vista/login.xhtml → «Enviarme un código»."
    Write-Host "  Si no aparece, reinicia el dominio: asadmin restart-domain $Dominio"
} finally {
    if ($script:archivoAdmin) { Remove-Item $script:archivoAdmin -Force -ErrorAction SilentlyContinue }
    $clave = $null; $adminPlano = $null
}
