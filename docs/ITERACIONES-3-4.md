# CoordinadITO · Iteraciones 3 y 4 (y trabajo futuro): lo que se programó

## La idea en una analogía

Seguimos con la ventanilla escolar de los documentos anteriores, pero ahora la escuela creció:

- **La entrada al edificio** ya no es «dime tu correo y pasa». Ahora hay un **guardia institucional** (el proveedor OpenID Connect del Tec): el alumno le muestra su credencial, el guardia le da un vale sellado y el sistema solo confía en ese vale si el sello es auténtico, es para este sistema, no ha caducado y corresponde a ese intento de entrada.
- **El expediente del alumno** dice de dónde salió cada dato: si lo trajo el **padrón oficial**, si el alumno lo **declaró** en su primera visita o si la coordinación lo **validó**.
- **La lista de firmas** para abrir un grupo: un alumno la inicia y los demás **se suman** en lugar de iniciar otra lista. La coordinación decide viendo cuántos firmaron.
- **El tablero de avisos oficiales** de cada carrera, con fecha de retiro.
- **La agenda de citas**: la coordinación publica sus horarios, pide a un alumno que venga en persona y el alumno elige un horario libre.
- **La oficina de jefatura** tiene sus tableros: cuántas solicitudes hay, cuánto tardan y cuáles se están quedando atrás. Y puede llevarse el historial en una hoja de cálculo.
- **El libro de registro** (auditoría): quién hizo cada cosa importante, cuándo y desde dónde. Nadie lo puede borrar.

## Mapa de historias → pantalla → control → servicio

| HU | Pantalla (`vista/`) | Control (`@Named`) | Servicio EJB |
|---|---|---|---|
| 4.1 Pedir apertura de grupo | `grupo-nuevo` | `GruposControl` | `GrupoService.solicitarApertura` |
| 4.2 Adherirse / retirarse | `grupos`, `grupo-detalle` | `GruposControl`, `GrupoControl` | `GrupoService.adherirse / retirarse` |
| 4.3 Avisos a todos los interesados | (notificaciones) | — | `SolicitudService.cambiarEstado` avisa a `getInteresados()` |
| 4.4 Demanda por materia | `grupos` (jefatura) | `GruposControl` | `GrupoService.grupos` (ordenada por interesados) |
| 5.3 Avisos oficiales | `avisos`, `aviso-nuevo`, `inicio` | `AvisosControl` | `AvisoService` |
| 6.1 / 6.2 Acceso institucional | `login` + `/acceso/*` | `SesionControl`, `AccesoServlet` | `AccesoService.ingresar` |
| 6.3 Roles y carreras | `roles` | `RolesControl` | `CuentaService` |
| 6.4 Primer acceso | `primer-acceso`, `perfil` | `PrimerAccesoControl` | `AccesoService.completarPrimerAcceso` |
| 6.5 Validar datos del alumno | `alumnos`, `alumno-detalle` | `AlumnosControl`, `AlumnoControl` | `AlumnoService` |
| 7.1 Volumen | `seguimiento-volumen` | `SeguimientoControl` | `SeguimientoService.volumen` |
| 7.2 Tiempos de atención | `seguimiento-tiempos` | `SeguimientoControl` | `SeguimientoService.tiempos` |
| 7.3 Rezagos | `seguimiento-rezagos` | `SeguimientoControl` | `SeguimientoService.rezagos` |
| 7.4 Exportar historial | botón en `seguimiento-volumen` | `SeguimientoControl.exportacion` | `SeguimientoService.exportar` (CSV) |
| 8.1 Padrón institucional | `padron` | `PadronControl` | `PadronService` |
| 8.2 Auditoría | `auditoria` | `AuditoriaControl` | `AuditoriaService` |
| 8.3 Catálogo de trámites | `catalogo-tramites`, `tramite-editar` | `CatalogoAdminControl`, `TramiteEdicionControl` | `TramiteService.guardar / eliminar` |
| 9.1 Publicar horarios | `agenda` | `AgendaControl` | `AgendaService.publicarBloques / cancelarBloque` |
| 9.2 Pedir y agendar cita | panel en `solicitud-detalle` | `CitaControl` | `SolicitudService.requerirCita`, `AgendaService.agendar / cancelarCita` |
| 9.3 Asistencia | `agenda` | `AgendaControl` | `AgendaService.registrarAsistencia` |

`datos-demo.xhtml` ya no se usa: se movió a `_to_delete/`. Todas las pantallas leen datos reales.

## Patrones de diseño y por qué (sin código repetido)

| Patrón | Dónde | Qué evita |
|---|---|---|
| **Política** (*Policy*) | `Permiso` (enum con roles y mensaje) + `Guardia` | Que cada servicio revise roles a su manera. Un permiso se define una vez. |
| **Reglas compartidas** | `Reglas` (textos, periodos, semestre, disponibilidad) | Validar lo mismo con mensajes distintos en cada servicio. |
| **Builder** | `Consulta` (JPQL dinámica), `Csv`, `GraficaBarras` (Chart.js) | Concatenar consultas, comillas de CSV o JSON a mano. |
| **Plantilla con lambda** (*Template method*) | `TramiteService.modificar(..., Consumer<Tramite>)` | Repetir «revisar permiso → cargar → cambiar → auditar» en cada operación. |
| **Punto único de auditoría** | `AuditoriaService.registrar / registrarAparte` | Que un servicio olvide auditar o lo haga con otro formato. |
| **Contexto por hilo** | `ContextoPeticion` (IP) lo llena `AccesoFiltro` | Pasar la IP como parámetro por todas las capas. |
| **Adaptador** | `ListaPaginada` (Pagina del EJB → `LazyDataModel` de PrimeFaces) | Escribir un `LazyDataModel` por tabla (alumnos, auditoría). |
| **Base común** | `ControlBase` (`yo()`, `rol()`, `hecho(...)`, `numeroDeFolio`) | Que cada control inyecte la sesión y repita «hazlo; si se pudo, avísalo». |
| **Objeto de vista** | `GrupoVista`, `ComunicadoVista`, `PersonaFila`, `EspacioVista`, `AuditoriaFila`, `RezagoFila`… | Mandar entidades JPA a la página (cargas perezosas, nombres distintos). Una sola clase por concepto: `PersonaFila` sirve para alumnos y personal; `EspacioVista` para bloques, horarios libres y citas; `ComunicadoVista` para el aviso guardado y su vista previa. |
| **Catálogos compartidos** | `#{catalogos.carreras}`, `#{catalogos.rolesPersonal}`… y `#{formato.fechaHora(x)}` | Que cada pantalla arme su propia lista de carreras o su propio formato de fecha. |

## Acceso con la cuenta institucional (OpenID Connect)

### Cómo funciona (flujo *Authorization Code* con PKCE)

```
1. login.xhtml → «Entrar con mi cuenta institucional» → /acceso/iniciar
2. AccesoServlet crea un intento {state, nonce, verificador PKCE} y lo guarda en la sesión
3. Redirige al proveedor (Microsoft Entra o Google) con state, nonce y el reto S256
4. El alumno inicia sesión allá; el proveedor regresa a /acceso/retorno?code=…&state=…
5. ClienteOidc compara el state, canjea el code (con el verificador) y recibe el id_token
6. Verifica el id_token: firma RS256 con las llaves públicas (JWKS), emisor, destinatario,
   vigencia y nonce. Toma el correo (email / preferred_username / upn) y el nombre.
7. AccesoService.ingresar(identidad) decide: admitido, primer acceso o rechazo (auditado)
8. SesionControl.abrir(...) cambia el id de sesión y manda a inicio o a primer-acceso
```

Clases: `control.acceso.ConfiguracionOidc` (propiedades, descubrimiento y llaves, en caché), `ClienteOidc` (URL, canje y verificación) y `AccesoServlet` (las dos rutas). No se agregó ninguna librería: se usan `HttpURLConnection`, `jakarta.json` y `java.security`.

### Configuración en Payara (cada PC)

Se registra la aplicación en el proveedor del Tec (Entra ID o Google Workspace) con la URL de regreso `http://localhost:8080/CoordinadITO-war/acceso/retorno` y luego:

```
asadmin create-password-alias coordinadito-oidc
asadmin create-system-properties "coordinadito.oidc.emisor=https\://login.microsoftonline.com/<tenant>/v2.0:coordinadito.oidc.cliente=<id-de-la-aplicacion>"
asadmin create-system-properties "coordinadito.oidc.secreto=${ALIAS=coordinadito-oidc}"
```

Opcional: `coordinadito.oidc.redireccion` si la URL de regreso no es la que deduce el servlet, y `coordinadito.dominio` si el dominio institucional no es `itoaxaca.edu.mx`.

Mientras no esté configurado, el login muestra el aviso «aún no está configurado» y, **solo con `PROJECT_STAGE = Development`**, deja entrar escribiendo el correo (y con los botones de las cuentas de prueba). En `Production` ese acceso desaparece.

## Otros cambios importantes

- **`AccesoFiltro` ahora cubre `/*`**: deja la IP en `ContextoPeticion` para la auditoría, manda al login a quien no tiene sesión y obliga a terminar el primer acceso antes de ver otra página.
- **`persistence.xml`: `<shared-cache-mode>NONE</shared-cache-mode>`.** Las dos PCs escriben en la misma base de Supabase; con la caché compartida de EclipseLink, una PC podía mostrar datos viejos de lo que cambió la otra. Lo descubrieron las pruebas.
- **Mensajes y redirects**: `Mensajes.redirigir(vista)` es el único lugar que usa el Flash; antes se activaba en cada mensaje y llenaba el log de avisos `JSF1095`.
- **`web.xml`**: el límite de archivo sube a 10 MB por el padrón (los adjuntos siguen limitados a 5 MB en la vista y en el EJB).
- **`comp-grafica-barras`** ya no arma JSON con EL: recibe el que construye `GraficaBarras` en Java.
- **`TipoCampo`** tiene etiquetas para el editor del catálogo.
- **`PadronService.plantilla()`** genera el CSV de ejemplo con las mismas columnas que exige la carga.
- La franja amarilla del panel solo aparece en modo desarrollo.

## Base de datos

`bd/04-migracion-v3.sql` (después de 01, 02 y 03) agrega `USUARIO.ORIGEN_DATOS`, `TRAMITE.DIAS_ESPERADOS`, la vista de rezagos por trámite, `SOLICITUD.CITA_REQUERIDA` y las tablas `BLOQUE_ATENCION` y `CITA` (con RLS). `bd/90-datos-prueba.sql` se reescribió para incluir padrón, materias y el trámite colectivo «Apertura de grupo». Quedan **21 tablas**.

## Cómo se probó

Payara 7.2026.9 + PostgreSQL 16 en un entorno aparte, con 01–04 y 90, el EAR completo desplegado y un navegador real (Playwright):

| Recorrido | Casos |
|---|---|
| Humo: 22 pantallas × 4 roles, sin errores ni excepciones | 88 páginas |
| Grupos, agenda y citas, avisos | 27 / 27 |
| Roles, padrón, primer acceso, alumnos, catálogo, jefatura, auditoría | 36 / 36 |
| Acceso institucional con un proveedor OIDC falso (firma válida e inválida, dominio ajeno, alumno nuevo, *state* robado) | 7 / 7 |
| Regresión de los Sprints 1–2 (registro con formulario, bandeja, cambio de estado, notificación) | 5 / 5 |

Se verificó además que la auditoría guarda la IP (`INET`) y el detalle (`JSONB`) y que se leen bien de vuelta.

## Limitaciones conocidas

1. **Proveedor institucional**: falta registrar la aplicación en el Entra ID / Google Workspace del Tec (lo hace quien administra esas cuentas). Hasta entonces, solo el acceso de desarrollo.
2. **Mojarra 4.1** escribe en el log «The view rebuilt for this postback does not hold N components…»: son los scripts que PrimeFaces mueve al final de la página (`MOVE_SCRIPTS_TO_BOTTOM`). No afecta el funcionamiento.
3. **Adjuntos locales** (sin cambios): cada PC guarda los archivos en su disco.
