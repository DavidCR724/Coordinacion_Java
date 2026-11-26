# Backend de CoordinadITO · Sprints 1 y 2

> **Actualización (Iteraciones 3 y 4):** `UsuarioService` se sustituyó por `AccesoService` (acceso institucional y primer acceso), `CuentaService` y `AlumnoService`; los permisos ahora se revisan con `Permiso` + `Guardia`. El detalle está en `ITERACIONES-3-4.md`.

## La idea en una analogía

Piensa en el backend como una **ventanilla de servicios escolares**:

- **Los expedientes** (`modelo.entity`) son las carpetas físicas: una solicitud, su bitácora, sus mensajes, sus adjuntos. Cada carpeta sabe qué se le puede hacer; por ejemplo, una solicitud *finalizada* no acepta otro sello.
- **El archivero** (`acceso.datos`) es quien sabe en qué cajón está cada carpeta y cómo buscarlas por folio, por carrera o por nombre. No decide nada, solo guarda y busca.
- **El personal de ventanilla** (`modelo.bean`) aplica el reglamento: revisa que el alumno llene lo obligatorio, que el periodo esté abierto, que no tenga otra solicitud abierta del mismo trámite y que quien atiende sea de esa carrera. Si algo no cumple, rechaza el trámite con un motivo claro y **no se guarda nada a medias**.

Las vistas (WAR) solo hablan con la ventanilla. Nunca abren el archivero directamente.

## Estructura (la que pidió el profesor)

```
CoordinadITO-ejb / Source Packages
├── modelo.entity     ← entidades JPA y enums (las tablas)
├── modelo.bean       ← servicios EJB, excepciones, DTOs, almacén de archivos
└── acceso.datos      ← repositorios (consultas JPQL)
```

| Paquete | Clases |
|---|---|
| `modelo.entity` | `Usuario`, `Carrera`, `Materia`, `Tramite`, `CampoFormulario`, `Solicitud`, `RespuestaCampo`, `EntradaBitacora` (tabla `HISTORIAL_SOLICITUD`), `MensajeSolicitud`, `DocumentoAdjunto`, `CambioPrioridad`, `Notificacion`; enums `Rol`, `EstadoSolicitud`, `Prioridad`, `TipoCampo`, `TipoMensaje`, `DisponibilidadTramite`; utilería `Fechas` |
| `acceso.datos` | `RepositorioBase<T,K>`, `UsuarioRepository`, `CarreraRepository`, `TramiteRepository`, `SolicitudRepository`, `NotificacionRepository` |
| `modelo.bean` | Servicios: `SolicitudService`, `TramiteService`, `UsuarioService`, `NotificacionService`, `CorreoService`, `DiagnosticoService`. Excepciones: `ReglaNegocioException`, `SolicitudDuplicadaException`, `AccesoDenegadoException`, `NoEncontradoException`. DTOs: `ArchivoNuevo`, `FiltroBandeja`, `Pagina<T>`, `ResumenBandeja`, `EstadoConexion`. Archivos: `AlmacenArchivos` |

El paquete viejo `mx.edu.itoaxaca.coordinadito.servicio` se movió a `_to_delete/`. `DiagnosticoControl` (WAR) ya importa de `modelo.bean`.

## Cómo viaja una petición

```
vista .xhtml → bean de control (WAR, @Named) → servicio (@Stateless, modelo.bean)
             → repositorio (@Stateless, acceso.datos) → EntityManager (CoordinadITOPU)
             → jdbc/coordinadito → Supabase
```

Cada método público de un servicio es **una transacción**. Si lanza una `ReglaNegocioException` (o una subclase), el contenedor hace *rollback* de todo: no quedan solicitudes sin bitácora ni avisos de algo que no se guardó.

## Qué cubre cada historia de usuario

| HU | Qué hace el backend | Método |
|---|---|---|
| 1.1 Catálogo | Trámites activos con su disponibilidad | `TramiteService.catalogo()` |
| 1.2 Formulario | Trámite con sus campos activos, ya ordenados | `TramiteService.formulario(id)` |
| 1.3 Periodos | Administración define inicio/fin; valida que el fin no sea anterior | `TramiteService.definirPeriodo(...)` |
| 1.4 Disponibilidad | `DISPONIBLE`, `AUN_NO_ABRE`, `PERIODO_CONCLUIDO`, `INACTIVO` | `Tramite.getDisponibilidad()` |
| 1.5 / 2.2 Registro | Valida rol, periodo, duplicados, campos (obligatorio, longitud, número, fecha, opción) y archivos (PDF/JPG/PNG, 5 MB, máx. 5); genera folio `ITO-0001` | `SolicitudService.registrar(...)` |
| 2.9 Duplicados | Una sola solicitud abierta por alumno y trámite (también lo cuida el índice `uq_solicitud_abierta`) | `SolicitudDuplicadaException` |
| 2.3 Mis solicitudes / detalle | El alumno ve solo las suyas; la coordinación, solo las de sus carreras | `misSolicitudes`, `detalle`, `puedeVer` |
| 2.1 / 3.1 Bandeja | Filtros por estado, trámite, prioridad mínima, fechas y texto (folio exacto, nombre, apellido, número de control, correo); orden prioridad ↓ y antigüedad ↑; paginada | `SolicitudService.bandeja(...)` |
| 3.4 Resumen | Conteos por estado y por prioridad alta | `SolicitudService.resumen(...)` |
| 2.4 / 2.5 Cambio de estado | Máquina de estados + constancia obligatoria + asigna a quien atiende + cierra con resolución y fecha | `cambiarEstado(...)` |
| 2.6 Pedir información | Mensaje al alumno y pasa a `ESPERA` | `solicitarInformacion(...)` |
| 2.7 Responder | El alumno responde (con adjuntos opcionales) y vuelve a `PROCESANDO` | `responder(...)` |
| 3.3 Prioridad | Solo sube, con motivo; queda en `CAMBIO_PRIORIDAD` | `elevarPrioridad(...)` |
| 2.8 Bitácora | Cada cambio deja una entrada (quién, cuándo, de qué estado a cuál, comentario) | `Solicitud.getBitacora()` |
| 3.2 Prioridad por trámite | Administración define la prioridad base | `TramiteService.definirPrioridad(...)` |
| 5.1 Avisos | Notificación en la app por cada registro, cambio, petición y respuesta; el correo sale en segundo plano | `NotificacionService`, `CorreoService` |

### Máquina de estados

| Desde | Puede pasar a |
|---|---|
| ENVIADA (Recibida) | PROCESANDO, ESPERA, RECHAZADA |
| PROCESANDO | ESPERA, FINALIZADA, RECHAZADA |
| ESPERA | PROCESANDO, RECHAZADA |
| FINALIZADA, RECHAZADA | — (finales) |

`ESPERA` solo se alcanza con `solicitarInformacion` (porque exige el mensaje al alumno), y el regreso a `PROCESANDO` desde `ESPERA` normalmente lo provoca la respuesta del alumno.

## Decisiones y por qué

- **Un solo `Usuario` con campo `rol`** en vez de herencia (Alumno, Coordinador…). El rol de una persona puede cambiar y la tabla es una sola; la herencia JPA obligaría a tablas o discriminadores que la base no tiene.
- **Las reglas viven en la entidad cuando son de la carpeta misma** (`Solicitud.cambiarEstado`, `Tramite.disponibilidadEn`) y en el servicio cuando dependen de quién actúa o de otras tablas (permisos, duplicados). Así una regla no se puede saltar desde otro servicio.
- **UUID generado en Java** (`UUID.randomUUID()`): la entidad tiene identidad antes de guardarse y no hace falta otra consulta.
- **Folio con `insertable=false`**: lo genera la secuencia de PostgreSQL; tras guardar se hace `flush + refresh` para tenerlo.
- **Fechas en `America/Mexico_City`** (`Fechas.ahora()`): el servidor de Supabase está en UTC y los periodos se piensan en hora de Oaxaca.
- **Búsqueda por folio exacta**: escribir `ITO-0002` filtra solo ese folio; si no, «2» también coincidiría con números de control.
- **Correo en segundo plano con `FOR UPDATE SKIP LOCKED`**: con dos PCs corriendo Payara contra la misma base, cada aviso lo toma una sola máquina y nunca se manda dos veces.
- **Java 8 compatible**: el proyecto de NetBeans tiene `javac.source=1.8`; no se usan `var` ni `record`.

## Configuración pendiente en Payara

### Sesión de correo (opcional, para HU 5.1 por correo)
Sin ella, los avisos quedan en la app y `CorreoService` simplemente no envía. Para activarla (ejemplo con Gmail y contraseña de aplicación):

```
asadmin create-mail-resource --mailhost smtp.gmail.com --mailuser tu.cuenta@gmail.com ^
  --fromaddress tu.cuenta@gmail.com --storeprotocol imap --transprotocol smtp ^
  --property mail.smtp.auth=true:mail.smtp.port=587:mail.smtp.starttls.enable=true:mail.smtp.password=${ALIAS=coordinadito-mail} ^
  mail/coordinadito
```

Antes crea el alias `coordinadito-mail` con `asadmin create-password-alias coordinadito-mail` (igual que la contraseña de la base: nunca en el código).

### Carpeta de adjuntos
Por defecto se guardan en `C:\Users\<usuario>\coordinadito-archivos`. Para cambiarla:

```
asadmin create-system-properties coordinadito.archivos=D:\\coordinadito-archivos
```

## Limitaciones conocidas (para el backlog)

1. **Adjuntos locales.** Cada PC guarda los archivos en su disco: un adjunto subido en la PC 1 no se puede descargar en la PC 2. La referencia queda como `local:<nombre>`; cuando se integre Supabase Storage solo cambia `AlmacenArchivos`.
2. **Inicio de sesión provisional.** `UsuarioService.iniciarSesionProvisional(correo)` entra solo con el correo, sin contraseña. Es para poder probar las vistas; se reemplaza en la Iteración 3 (autenticación).
3. **Trámites colectivos** (`colectivo = true`) se rechazan al registrar: su flujo es del Sprint 3.

## Cómo se probó

Se corrió el backend completo fuera de Payara (Java SE + EclipseLink de Payara 7 + PostgreSQL 16 local) con los scripts `bd/01`, `02` y `03` y datos de prueba (`bd/90-datos-prueba.sql`). **57 de 57 casos correctos**, entre ellos:

- registro con folio, prioridad y carrera copiadas; rechazo por obligatorio faltante, número inválido, opción inválida, periodo cerrado, rol incorrecto, documento faltante, tipo de archivo y duplicado;
- aislamiento: un alumno no ve la solicitud de otro y la coordinación de IC no ve las de ISC;
- bandeja con orden, filtros, búsqueda por apellido y por folio, y resumen;
- ciclo completo ENVIADA → PROCESANDO → ESPERA → PROCESANDO → prioridad CRÍTICA → FINALIZADA, con 5 entradas de bitácora, y rechazo de cambios posteriores;
- permisos de administración sobre periodos y prioridades;
- notificaciones y cola de correo;
- diagnóstico con las 19 tablas.

### Datos de prueba
`bd/90-datos-prueba.sql` crea dos carreras (ISC, IC), dos alumnos (`00000001@…`, `00000002@…`), coordinación de cada carrera, jefatura, administración y tres trámites (BM con formulario, CH que exige documento, BT con periodo cerrado). **Solo para desarrollo**: no lo ejecutes en una base con datos reales.
