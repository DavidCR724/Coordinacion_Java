<#
  CoordinadITO · Cambia Payara de Supabase a una base PostgreSQL LOCAL (en esta PC).

  Ejecútalo en PowerShell (no hace falta abrirlo como administrador):

      cd C:\Users\<tu-usuario>\Documents\NetBeansProjects\CoordinadITO
      powershell -ExecutionPolicy Bypass -File herramientas\configurar-local.ps1

  Qué hace:
    1. Busca psql.exe en C:\Program Files\PostgreSQL\<versión>\bin (la versión más nueva).
    2. Pide la contraseña del usuario postgres de TU PostgreSQL (la que pusiste al instalarlo),
       sin mostrarla, y comprueba que conecta.
    3. Crea la base "coordinadito" si no existe y ejecuta los scripts que falten:
       01 a 07 (esquema) y 95 (datos de demostración, salvo con -SinDemo).
    4. Guarda esa contraseña como alias cifrado de Payara (coordinadito-db-local).
    5. Apunta el pool CoordinadITOPool a localhost (sin SSL) y prueba la conexión.

  No borra nada: si la base ya existe, solo ejecuta lo que le falte.
  Para volver a Supabase: herramientas\configurar-supabase.ps1 (el alias de Supabase no se toca).

  Si tienes dos versiones de PostgreSQL, cada una escucha en su puerto (normalmente la que
  instalaste primero en 5432 y la otra en 5433). Elige con:  -Puerto 5433
#>
param(
    [string]$PayaraHome  = "$env:USERPROFILE\Payara_Server",
    [string]$Dominio     = "coordinadito",
    [int]   $PuertoAdmin = 4848,

    [int]   $Puerto      = 5432,
    [string]$Base        = "coordinadito",
    [string]$Usuario     = "postgres",
    [string]$PgBin       = "",          # vacío = la versión más nueva en C:\Program Files\PostgreSQL

    [int]   $MaxConexiones = 10,
    [switch]$SinDemo
)

$ErrorActionPreference = "Stop"
$Pool    = "CoordinadITOPool"
$Recurso = "jdbc/coordinadito"
$Alias   = "coordinadito-db-local"
$bd      = Join-Path (Split-Path $PSScriptRoot -Parent) "bd"

$asadmin = Join-Path $PayaraHome "bin\asadmin.bat"
if (-not (Test-Path $asadmin)) { throw "No encontré $asadmin. Pasa la ruta correcta con -PayaraHome." }

# asadmin debe correr con Java 21 (Payara 7).
$ErrorActionPreference = "Continue"
& $asadmin version --local *> $null
$javaOk = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = "Stop"
if (-not $javaOk) {
    throw "asadmin no arranca. Payara 7 necesita Java 21: agrega 'set AS_JAVA=<ruta del JDK 21>' al final de $PayaraHome\glassfish\config\asenv.bat."
}

function Asadmin([string[]]$argumentos, [switch]$PuedeFallar) {
    $todos = @("--port", "$PuertoAdmin") + $argumentos
    if ($script:archivoAdmin) { $todos = @("--passwordfile", $script:archivoAdmin) + $todos }
    & $asadmin @todos
    if ($LASTEXITCODE -ne 0 -and -not $PuedeFallar) { throw "Falló: asadmin $($argumentos -join ' ')" }
}

# psql: devuelve la salida; con -Archivo ejecuta un script completo y se detiene en el primer error.
function Psql([string]$baseDatos, [string]$sql = "", [string]$Archivo = "") {
    $argumentos = @("-h", "localhost", "-p", "$Puerto", "-U", $Usuario, "-d", $baseDatos, "-v", "ON_ERROR_STOP=1", "-q", "-w")
    if ($Archivo) { $argumentos += @("-f", $Archivo) } else { $argumentos += @("-tAc", $sql) }
    $ErrorActionPreference = "Continue"
    $salida = & $script:psql @argumentos 2>&1
    $codigo = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    if ($codigo -ne 0) { throw "psql falló ($baseDatos): $($salida | Out-String)" }
    return (($salida | Where-Object { $_ -is [string] }) -join "`n").Trim()
}

Write-Host "`n== 1/5 PostgreSQL local" -ForegroundColor Cyan
if (-not $PgBin) {
    $instalado = Get-ChildItem "C:\Program Files\PostgreSQL" -Directory -ErrorAction SilentlyContinue |
                 Where-Object { Test-Path (Join-Path $_.FullName "bin\psql.exe") } |
                 Sort-Object { [int]($_.Name -replace '\D', '') } -Descending | Select-Object -First 1
    if (-not $instalado) { throw "No encontré PostgreSQL en C:\Program Files\PostgreSQL. Pasa la carpeta bin con -PgBin." }
    $PgBin = Join-Path $instalado.FullName "bin"
}
$script:psql = Join-Path $PgBin "psql.exe"
Write-Host "psql: $script:psql"
Get-Service -Name "postgresql*" -ErrorAction SilentlyContinue | ForEach-Object { Write-Host ("Servicio {0}: {1}" -f $_.Name, $_.Status) }

$pgPwd = Read-Host "Contraseña del usuario '$Usuario' de tu PostgreSQL local" -AsSecureString
$pgPlano = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($pgPwd))
if (-not $pgPlano) { throw "La contraseña no puede ir vacía." }
# Solo para los psql de este proceso; se borra al terminar.
$env:PGPASSWORD = $pgPlano
# Los scripts .sql están en UTF-8 (acentos); sin esto psql usaría la página de códigos de la consola.
$env:PGCLIENTENCODING = "UTF8"

try {
    try {
        $version = Psql "postgres" "SELECT current_setting('server_version')"
    } catch {
        throw "No pude conectar a localhost:$Puerto como '$Usuario'. Revisa la contraseña, que el servicio esté iniciado o prueba con -Puerto 5433.`n$_"
    }
    Write-Host "Conectado a PostgreSQL $version en el puerto $Puerto." -ForegroundColor Green

    Write-Host "`n== 2/5 Base de datos '$Base'" -ForegroundColor Cyan
    if ((Psql "postgres" "SELECT COUNT(*) FROM pg_database WHERE datname = '$Base'") -eq "0") {
        Psql "postgres" "CREATE DATABASE $Base WITH ENCODING 'UTF8' TEMPLATE template0" | Out-Null
        Write-Host "Base creada."
    } else {
        Write-Host "Ya existe; solo se ejecuta lo que falte."
    }

    Write-Host "`n== 3/5 Esquema y datos" -ForegroundColor Cyan
    $hay = { param($tabla, $columna)
        $sql = if ($columna) { "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$tabla' AND column_name='$columna'" }
               else { "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$tabla'" }
        (Psql $Base $sql) -ne "0"
    }
    $pasos = @(
        @{ Archivo = "01-esquema-v1.sql";    Hecho = { & $hay "usuario" $null } },
        @{ Archivo = "02-migracion-v2.sql";  Hecho = { & $hay "auditoria" $null } },
        @{ Archivo = "03-supabase-rls.sql";  Hecho = { (Psql $Base "SELECT COUNT(*) FROM pg_class WHERE relname='usuario' AND relrowsecurity") -ne "0" } },
        @{ Archivo = "04-migracion-v3.sql";  Hecho = { & $hay "usuario" "origen_datos" } },
        @{ Archivo = "05-lectura-avisos.sql"; Hecho = { & $hay "aviso_lectura" $null } },
        @{ Archivo = "06-sin-administracion.sql"; Hecho = { & $hay "carga_padron" "id_responsable" } },
        @{ Archivo = "07-codigo-acceso.sql"; Hecho = { & $hay "codigo_acceso" $null } }
    )
    if (-not $SinDemo) {
        $pasos += @{ Archivo = "95-datos-demo.sql"; Hecho = { (Psql $Base "SELECT COUNT(*) FROM carga_padron WHERE nombre_archivo='padron-demo-ago-dic-2026.csv'") -ne "0" } }
    }
    foreach ($p in $pasos) {
        if (& $p.Hecho) {
            Write-Host "  ya aplicado: $($p.Archivo)"
        } else {
            Write-Host "  ejecutando:  $($p.Archivo)"
            Psql $Base -Archivo (Join-Path $bd $p.Archivo) | Out-Null
        }
    }
    $tablas = Psql $Base "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'"
    Write-Host "Tablas: $tablas (deben ser 23)."

    Write-Host "`n== 4/5 Dominio y alias $Alias" -ForegroundColor Cyan
    $adminPwd = Read-Host "Contraseña del usuario admin de Payara (Enter si no tiene)" -AsSecureString
    $adminPlano = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminPwd))
    $script:archivoAdmin = $null
    if ($adminPlano) {
        $script:archivoAdmin = [IO.Path]::GetTempFileName()
        Set-Content -Path $script:archivoAdmin -Value "AS_ADMIN_PASSWORD=$adminPlano" -Encoding ASCII
    }
    & $asadmin list-domains | Out-String | Tee-Object -Variable estado | Out-Null
    if ($estado -notmatch "$Dominio running") {
        & $asadmin start-domain $Dominio
        if ($LASTEXITCODE -ne 0) { throw "No se pudo arrancar el dominio $Dominio. Revisa glassfish\domains\$Dominio\logs\server.log." }
    }
    $archivoAlias = [IO.Path]::GetTempFileName()
    try {
        $lineas = @("AS_ADMIN_ALIASPASSWORD=$pgPlano")
        if ($adminPlano) { $lineas += "AS_ADMIN_PASSWORD=$adminPlano" }
        Set-Content -Path $archivoAlias -Value $lineas -Encoding ASCII
        $existe = (& $asadmin --port $PuertoAdmin --passwordfile $archivoAlias list-password-aliases 2>$null) -match "^$Alias$"
        if ($existe) {
            & $asadmin --port $PuertoAdmin --passwordfile $archivoAlias update-password-alias $Alias
        } else {
            & $asadmin --port $PuertoAdmin --passwordfile $archivoAlias create-password-alias $Alias
        }
        if ($LASTEXITCODE -ne 0) { throw "No se pudo guardar el alias de la contraseña." }
    } finally {
        Remove-Item $archivoAlias -Force -ErrorAction SilentlyContinue
    }

    Write-Host "`n== 5/5 Pool $Pool → localhost:$Puerto/$Base" -ForegroundColor Cyan
    # Se cambian las propiedades del pool existente (la app desplegada no se toca).
    $existePool = (Asadmin @("list-jdbc-connection-pools")) -match "^$Pool$"
    if (-not $existePool) {
        Asadmin @(
            "create-jdbc-connection-pool",
            "--datasourceclassname", "org.postgresql.ds.PGSimpleDataSource",
            "--restype", "javax.sql.DataSource",
            "--isconnectvalidatereq", "true",
            "--validationmethod", "custom-validation",
            "--validationclassname", "org.glassfish.api.jdbc.validation.PostgresConnectionValidation",
            "--failconnection", "true",
            "--property", "serverName=localhost:portNumber=${Puerto}:databaseName=${Base}:user=${Usuario}",
            $Pool)
        Asadmin @("create-jdbc-resource", "--connectionpoolid", $Pool, $Recurso)
    }
    $p = "resources.jdbc-connection-pool.$Pool"
    Asadmin @("set",
        "$p.property.serverName=localhost",
        "$p.property.portNumber=$Puerto",
        "$p.property.databaseName=$Base",
        "$p.property.user=$Usuario",
        # Sin SSL en local; stringType=unspecified sigue siendo necesario para los ENUM, JSONB e INET.
        "$p.property.sslMode=disable",
        "$p.property.stringType=unspecified",
        "$p.max-pool-size=$MaxConexiones")
    # La contraseña se referencia por alias: en domain.xml solo queda ${ALIAS=coordinadito-db-local}.
    Asadmin @("set", "$p.property.password=`${ALIAS=$Alias}")
    # Cierra las conexiones que seguían abiertas contra Supabase.
    Asadmin @("flush-connection-pool", $Pool) -PuedeFallar | Out-Null
    Asadmin @("ping-connection-pool", $Pool)

    Write-Host "`nListo: CoordinadITO usa la base local '$Base'. Abre (sin volver a desplegar):" -ForegroundColor Green
    Write-Host "  http://localhost:8080/CoordinadITO-war/vista/diagnostico.xhtml"
} finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    if ($script:archivoAdmin) { Remove-Item $script:archivoAdmin -Force -ErrorAction SilentlyContinue }
    $pgPlano = $null; $adminPlano = $null
}
