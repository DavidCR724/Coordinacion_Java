# Controles del WAR · conexión de las vistas con el backend (Sprints 1 y 2)

## La idea en una analogía

Siguiendo la ventanilla escolar del backend:

- **Las vistas `.xhtml`** son el mostrador: lo que el alumno o la coordinadora ven y llenan.
- **Los controles (`@Named`)** son quien atiende el mostrador. Toma el formulario, lo pasa a la ventanilla (EJB) y le regresa al usuario la respuesta ya acomodada para leerla: fechas como «12 nov 2026», estados como «En revisión», folios como «ITO-0042».
- **La ventanilla (EJB, `modelo.bean`)** sigue decidiendo todo. El control nunca decide permisos: aunque alguien manipule la página, el EJB recarga al usuario y revisa su rol en cada operación.

## Dónde está cada cosa

```
CoordinadITO-war / Source Packages
└── mx.edu.itoaxaca.coordinadito.control          ← beans @Named (uno por pantalla) + filtro de acceso
    └── vista                                      ← objetos que leen las vistas y utilidades
```

| Control | Alcance | Vista | Historias | Llama a |
|---|---|---|---|---|
| `SesionControl` | Sesión | `login`, barra superior, menú | 6.1, 6.2 | `AccesoService.ingresar` (acceso de desarrollo; el institucional entra por `AccesoServlet`), `NotificacionService.sinLeer` |
| `AccesoFiltro` | Filtro `/*` | todas | 6.4, 8.2 | IP para la auditoría; sin sesión → `login.xhtml` (también en AJAX); primer acceso pendiente → `primer-acceso.xhtml` |
| `InicioControl` | Petición | `inicio` | 2.3, 3.4, 5.2 | `misSolicitudes`, `resumen`, `bandeja`, `recientes`, `marcarLeidas` |
| `CatalogoControl` | Vista | `tramites` | 1.1, 1.4 | `TramiteService.catalogo` |
| `NuevaSolicitudControl` | Vista | `tramite-detalle` | 1.2, 1.5, 2.2, 2.9 | `TramiteService.formulario`, `SolicitudService.registrar` |
| `SolicitudesControl` | Vista | `solicitudes` | 2.3, 2.8 | `misSolicitudes` (alumno) / `bandeja` con todos los estados |
| `BandejaControl` | Vista | `bandeja` | 2.1, 3.1, 3.4 | `bandeja` con filtros, `resumen` |
| `DetalleSolicitudControl` | Vista | `solicitud-detalle` | 2.3–2.8, 3.3 | `detalle`, `cambiarEstado`, `solicitarInformacion`, `responder`, `elevarPrioridad`, `descargarAdjunto` |
| `NotificacionesControl` | Vista | `notificaciones` | 5.1, 5.2 | `recientes`, `marcarLeidas` |
| `PeriodosControl` | Vista | `periodos` | 1.3 | `todos`, `definirPeriodo`, `definirActivo` |
| `PrioridadesControl` | Vista | `prioridades` | 3.2 | `todos`, `abiertasPorTramite`, `definirPrioridad` |
| `DiagnosticoControl` | Petición | `diagnostico` | — | `DiagnosticoService.probar` |

Paquete `control.vista`:

| Clase | Para qué |
|---|---|
| `UsuarioVista`, `TramiteVista`, `CampoVista`, `SolicitudFila`, `SolicitudDetalle`, `EventoVista`, `DatoVista`, `MensajeVista`, `AdjuntoVista`, `CambioPrioridadVista`, `AvisoVista`, `PeriodoFila`, `PrioridadFila` | Copias de solo lectura de las entidades, con los **mismos nombres de propiedad que ya usaban las vistas** (`codigo`, `apertura`, `codigoEstado`, `registro`…). Por eso los componentes casi no cambiaron. |
| `Formato` | Fechas en hora de Oaxaca («12 nov 2026, 8:40 a.m.»), tamaños de archivo y la traducción `EstadoSolicitud` ↔ código de las vistas. |
| `Mensajes` | Ejecuta una llamada al EJB y convierte una `ReglaNegocioException` en un mensaje del `p:growl`. Cualquier otro error se registra en el log de Payara y muestra un texto genérico. |

## Decisiones y por qué

- **Alcances.** `@SessionScoped` solo para la sesión, porque ocupa memoria por usuario durante 30 minutos. `@ViewScoped` en las pantallas con AJAX o pasos, porque los datos tienen que sobrevivir entre peticiones de la misma página (el p:wizard, los filtros, los archivos subidos). `@RequestScoped` en el inicio, que solo se lee.
- **Se devuelven objetos «vista», no entidades.** Una entidad desconectada que llega a la página puede disparar cargas perezosas a la base mientras se dibuja. Además, sus nombres no coinciden con los que ya usaban los componentes. El control arma la copia una vez y la vista solo lee.
- **Códigos de estado en inglés.** `received`, `in-review`, `waiting`, `completed` y `rejected` vienen del diseño en React y ya estaban en los componentes. `Formato.codigo()` los traduce desde el ENUM. Se agregó `waiting` para `ESPERA` («Requiere información»), que el diseño no tenía.
- **El rol ya no viaja en la URL.** `?rol=` y el selector «Ver como» se quitaron. El rol sale de la cuenta (`sesionControl.rol`) y el filtro manda al login a quien no tiene sesión.
- **Cuentas de prueba en el login.** Los botones de acceso rápido solo aparecen con `PROJECT_STAGE = Development` (en `web.xml`). En `Production` desaparecen.
- **Archivos.** Se usa `p:fileUpload` en modo avanzado con `p:validateFile` (PDF, JPG o PNG, 5 MB, máximo 5). En PrimeFaces 16 esos límites ya no son atributos del componente. Se agregó `<multipart-config>` al Faces Servlet en `web.xml`. El EJB vuelve a validar todo.

## Cambios en el EJB que pidió esta parte

- `UsuarioService.exigir` ahora es `public`. Los otros EJB lo llaman a través del contenedor y un método no público da *«Illegal non-business method access on no-interface view»*. En la prueba fuera de Payara no se notaba, en el servidor sí.
- `TramiteService.definirActivo(...)`: el interruptor «Habilitado» de periodos.
- `TramiteService.abiertasPorTramite()` y `SolicitudRepository.abiertasPorTramite()`: la columna «Abiertas» de prioridades.

> **Actualización (Iteraciones 3 y 4):** las pantallas de abajo ya están conectadas y todos los controles heredan de `ControlBase`. Ver `ITERACIONES-3-4.md`.

## Qué seguía con datos de demostración

Las pantallas de iteraciones posteriores siguen leyendo `datos-demo.xhtml`. El rol ya sale de la sesión, pero sus datos todavía son de ejemplo:

- grupos
- avisos
- roles
- seguimiento
- primer acceso
- los tableros de jefatura en el inicio

El perfil muestra la cuenta real, pero editarlo es de la Iteración 3.

## Cómo se probó

1. Se instaló Payara 7.2026.9 (la misma versión del proyecto) en un entorno aparte, con PostgreSQL 16 y los scripts `bd/01`, `02`, `03` y `90-datos-prueba.sql`.
2. Se desplegó el EAR completo.
3. Se recorrió la app en un navegador real (Playwright). **Resultado: 40 de 40 casos correctos.**

Lo que cubrió el recorrido:

- **Login:** correo inexistente rechazado. Al salir, `inicio.xhtml` regresa al login.
- **Catálogo:** búsqueda por AJAX y periodo cerrado.
- **Registro de solicitudes:**
  - se validan los campos obligatorios;
  - el paso de revisión muestra lo capturado y se genera el folio;
  - una solicitud duplicada se rechaza con mensaje;
  - un trámite que exige documento se rechaza sin él y se acepta al subir un PDF.
- **Coordinación:**
  - bandeja ordenada por prioridad y búsqueda por folio exacto;
  - la constancia es obligatoria;
  - recorre En revisión → Requiere información, sube la prioridad a Crítica y finaliza con resolución;
  - la bitácora queda con 5 entradas y ya no hay panel de atención.
- **Alumno:** responde con un adjunto (vuelve a En revisión) y lo descarga. También funcionan las notificaciones y «Marcar todo como leído».
- **Jefatura:** consulta sin panel de atención.
- **Administración:** reabre un periodo y cambia una prioridad.
