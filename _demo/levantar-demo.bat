@echo off
REM CoordinadITO - levanta la demo local: enciende el dominio y despliega el EAR.
REM Uso: doble clic. La bitacora queda en _demo\levantar.log
setlocal
set ASADMIN=%USERPROFILE%\Payara_Server\bin\asadmin.bat
set DEMO=%~dp0
set LOG=%DEMO%levantar.log
call :principal > "%LOG%" 2>&1
type "%LOG%"
echo.
echo Listo. Abre http://localhost:8080/CoordinadITO-war/
pause
exit /b

:principal
echo == %DATE% %TIME%
echo == PostgreSQL (servicios)
sc query state= all | findstr /i "SERVICE_NAME.*postgres"
for /f "tokens=2 delims=:" %%s in ('sc query state^= all ^| findstr /i "SERVICE_NAME.*postgres"') do sc query %%s | findstr /i "STATE"
echo == Dominio coordinadito
call "%ASADMIN%" list-domains
call "%ASADMIN%" start-domain coordinadito
echo == Despliegue
call "%ASADMIN%" list-applications
call "%ASADMIN%" deploy --force=true --name CoordinadITO "%DEMO%CoordinadITO.ear"
call "%ASADMIN%" list-applications
echo == Fin %TIME%
exit /b
