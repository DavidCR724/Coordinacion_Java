<#
  CoordinadITO · Conecta Payara con la base de datos de Supabase (Session Pooler).

  Se ejecuta UNA vez en cada máquina (PowerShell, no hace falta abrirlo como administrador):

      cd C:\Users\<tu-usuario>\Documents\NetBeansProjects\CoordinadITO
      powershell -ExecutionPolicy Bypass -File herramientas\configurar-supabase.ps1

  Qué hace:
    1. Descarga el driver JDBC de PostgreSQL a la carpeta lib del dominio (si no está).
    2. Arranca o reinicia el dominio para que cargue el driver.
    3. Pide la contraseña de la base de Supabase SIN mostrarla y la guarda como alias
       cifrado de Payara (coordinadito-db). La contraseña nunca queda en el proyecto ni en Git.
    4. Crea el pool CoordinadITOPool (Session Pooler, SSL obligatorio) y el recurso jdbc/coordinadito.
    5. Hace ping al pool para comprobar que conecta.

  Se puede volver a ejecutar: borra y recrea el pool y el recurso, y actualiza el alias.
#>
param(
    [string]$PayaraHome  = "$env:USERPROFILE\Payara_Server",
    [string]$Dominio     = "coordinadito",
    [int]   $PuertoAdmin = 4848,

    # Datos del Session Pooler de Supabase (Project Settings > Database > Connection string > Session pooler)
    [string]$Servidor    = "aws-0-us-west-2.pooler.supabase.com",
    [int]   $Puerto      = 5432,
    [string]$Base        = "postgres",
    [string]$Usuario     = "postgres.nwokbcaajmfvfebobyzv",

    # Supabase limita las conexiones del pooler; con 2 PCs, 5 por máquina deja margen.
    [int]   $MaxConexiones = 5,
    [string]$VersionDriver = "42.7.14"
)

$ErrorActionPreference = "Stop"
$Pool    = "CoordinadITOPool"
$Recurso = "jdbc/coordinadito"
$Alias   = "coordinadito-db"

$asadmin = Join-Path $PayaraHome "bin\asadmin.bat"
$libDir  = Join-Path $PayaraHome "glassfish\domains\$Dominio\lib"
if (-not (Test-Path $asadmin)) { throw "No encontré $asadmin. Pasa la ruta correcta con -PayaraHome." }
if (-not (Test-Path $libDir))  { throw "No existe el dominio '$Dominio' en $PayaraHome." }

# asadmin debe correr con Java 21 (Payara 7). Si el PATH tiene Java 8, falla con UnsupportedClassVersionError.
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

Write-Host "`n== 1/5 Driver JDBC de PostgreSQL" -ForegroundColor Cyan
$jar = Join-Path $libDir "postgresql-$VersionDriver.jar"
$driverNuevo = $false
if (Test-Path $jar) {
    Write-Host "Ya está: $jar"
} else {
    $url = "https://repo1.maven.org/maven2/org/postgresql/postgresql/$VersionDriver/postgresql-$VersionDriver.jar"
    Write-Host "Descargando $url"
    Invoke-WebRequest -Uri $url -OutFile $jar -UseBasicParsing
    $driverNuevo = $true
}

Write-Host "`n== 2/5 Dominio $Dominio" -ForegroundColor Cyan
$adminPwd = Read-Host "Contraseña del usuario admin de Payara (Enter si no tiene)" -AsSecureString
$adminPlano = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminPwd))
$script:archivoAdmin = $null
if ($adminPlano) {
    $script:archivoAdmin = [IO.Path]::GetTempFileName()
    Set-Content -Path $script:archivoAdmin -Value "AS_ADMIN_PASSWORD=$adminPlano" -Encoding ASCII
}
& $asadmin list-domains | Out-String | Tee-Object -Variable estado | Out-Null
if ($estado -match "$Dominio running") {
    # Se reinicia siempre: el driver de lib solo se carga al arrancar el dominio.
    & $asadmin restart-domain $Dominio
} else {
    & $asadmin start-domain $Dominio
}
if ($LASTEXITCODE -ne 0) { throw "No se pudo arrancar el dominio $Dominio. Revisa glassfish\domains\$Dominio\logs\server.log." }

try {
    Write-Host "`n== 3/5 Contraseña de Supabase (alias $Alias)" -ForegroundColor Cyan
    $dbPwd = Read-Host "Contraseña de la base de datos de Supabase" -AsSecureString
    $dbPlano = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($dbPwd))
    if (-not $dbPlano) { throw "La contraseña de la base no puede ir vacía." }
    $archivoAlias = [IO.Path]::GetTempFileName()
    try {
        $lineas = @("AS_ADMIN_ALIASPASSWORD=$dbPlano")
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
        $dbPlano = $null
    }

    Write-Host "`n== 4/5 Pool $Pool y recurso $Recurso" -ForegroundColor Cyan
    Asadmin @("delete-jdbc-resource", $Recurso) -PuedeFallar | Out-Null
    Asadmin @("delete-jdbc-connection-pool", $Pool) -PuedeFallar | Out-Null

    # Propiedades del DataSource de PostgreSQL (se separan con ':').
    #   sslMode=require      Supabase solo acepta conexiones cifradas.
    #   stringType=unspecified  Permite guardar texto en columnas ENUM (ROLES, ESTADO_SOLICITUD...) desde JPA.
    $propiedades = "serverName=${Servidor}:portNumber=${Puerto}:databaseName=${Base}:user=${Usuario}" +
                   ":sslMode=require:stringType=unspecified:ApplicationName=CoordinadITO-${env:COMPUTERNAME}"
    Asadmin @(
        "create-jdbc-connection-pool",
        "--datasourceclassname", "org.postgresql.ds.PGSimpleDataSource",
        "--restype", "javax.sql.DataSource",
        "--steadypoolsize", "0",
        "--maxpoolsize", "$MaxConexiones",
        "--poolresize", "1",
        "--idletimeout", "300",
        "--maxwait", "30000",
        "--isconnectvalidatereq", "true",
        "--validationmethod", "custom-validation",
        "--validationclassname", "org.glassfish.api.jdbc.validation.PostgresConnectionValidation",
        "--failconnection", "true",
        "--property", $propiedades,
        $Pool)
    # La contraseña se referencia por alias: en domain.xml solo queda ${ALIAS=coordinadito-db}.
    Asadmin @("set", "resources.jdbc-connection-pool.$Pool.property.password=`${ALIAS=$Alias}")
    Asadmin @("create-jdbc-resource", "--connectionpoolid", $Pool, $Recurso)

    Write-Host "`n== 5/5 Prueba de conexión" -ForegroundColor Cyan
    Asadmin @("ping-connection-pool", $Pool)
    Write-Host "`nListo. Despliega el EAR desde NetBeans y abre:" -ForegroundColor Green
    Write-Host "  http://localhost:8080/CoordinadITO-war/vista/diagnostico.xhtml"
} finally {
    if ($script:archivoAdmin) { Remove-Item $script:archivoAdmin -Force -ErrorAction SilentlyContinue }
    $adminPlano = $null
}
