# CoordinadITO · Refactor de interfaces (menos carga cognitiva, primero el teléfono)

## La idea en una analogía

Antes, el inicio del alumno era como **un tablero de corcho donde se clavaba todo**: avisos, expediente, consejos, la bandeja completa y una tabla. Todo estaba "a la vista", pero para encontrar lo que importaba había que leerlo todo.

Ahora se parece más a **la pantalla de bloqueo de un teléfono**:

- Arriba, **un sobre con un numerito** (los avisos que no has abierto). Si no hay número, no hay nada nuevo.
- Debajo, **tres contadores** que se pueden tocar (como los globos rojos de las apps).
- Luego, **tus trámites como tarjetas**, cada una con su estado en color.
- Al final, **el buzón de novedades**, abierto en "Sin leer".

Para coordinación, la analogía es **una charola de "pendientes" y otra de "archivado"**: en lugar de seis filtros por estado, dos botones (Activas / Finalizadas). Y atender una solicitud dejó de ser llenar un formulario: si se hizo lo que el alumno pidió, basta con **un clic en «Registrar»**.

## Qué cambió

### Barra superior (todas las pantallas)
| Antes | Ahora |
|---|---|
| Botón de cuadritos (escritorio) y otro de menú (teléfono) | Un solo ☰: en escritorio contrae la barra lateral (se recuerda); en el teléfono abre el menú |
| Migas «Mi Espacio > Inicio» | Solo el nombre de la página actual |
| Chip del semestre | Se quitó |
| Avatar + nombre + rol | Solo el avatar; al tocarlo, un menú con nombre, rol, «Mi perfil» y «Salir» |
| Tarjeta "Acompañamos tu camino" en la barra lateral | Se quitó |

### Inicio del estudiante (una columna, pensada para el teléfono)
1. Saludo y botón «Solicitar un trámite» (ancho completo en el teléfono). Sin el texto «TU ESPACIO ACADÉMICO».
2. **Avisos de la carrera** (`comp-avisos-desplegable`): sobre con globo naranja y el número de avisos sin abrir. Al tocar un aviso se despliega y queda como leído; los leídos siguen en la lista, en gris, y el sobre queda sin número. La lista empieza abierta solo si hay algo por leer.
3. **Resumen tocable**: «Actualizaciones sin leer» (primero), «Solicitudes en curso», «Trámites finalizados». Cada número lleva a donde se atiende.
4. **Mis solicitudes recientes** en tarjetas (`comp-lista-solicitudes`) en lugar de una tabla que en el teléfono se apilaba.
5. **Bandeja de entrada** al final: pestaña «Sin leer» primero (se abre sola si hay novedades), «Todas las actualizaciones» como alternativa; cinco por pestaña y enlace a Notificaciones.
6. Se quitaron las tarjetas «Mi expediente» y «Antes de comenzar» (repetían lo que está en Mi perfil y en Trámites).

Altura del inicio en un teléfono (390 px de ancho): **de ~18 500 px a ~1 800 px**.

### Coordinación
- **Activas / Finalizadas** (`comp-selector-vista` + `ListaSolicitudes`) junto a la búsqueda, en el inicio, en Solicitudes y en la bandeja. Se quitó el filtro por estado de la columna. *Activas* = recibidas, en revisión o esperando al alumno; *Finalizadas* = finalizadas o rechazadas (el historial).
- **Atender solicitud** en un solo panel: «Pasar a revisión / Finalizar / Rechazar / Pedir información» como botones; viene marcada la opción de avanzar.
- **El comentario es opcional** al pasar a revisión y al finalizar. Si se deja vacío, el alumno recibe «Se atendió lo que pediste» y el detalle muestra «Se atendió lo que se pidió en la solicitud».
- **Sigue siendo obligatorio** al **rechazar** (el alumno necesita saber qué corregir) y al **pedir información** (hay que decirle qué falta).
- «Subir prioridad» quedó plegado: es excepcional y ya no compite con «Atender».

## Clics por historia de usuario (simulado con Playwright)

| HU | Quién | Antes | Ahora |
|---|---|---|---|
| 2.3 Ver en qué va mi solicitud (teléfono) | Estudiante | 3 toques (☰ → Mis solicitudes → fila) o desplazarse por la tabla apilada | **0** (el estado está en la tarjeta del inicio); 1 toque para el detalle |
| 5.3 Ver los avisos de mi carrera | Estudiante | Al final de la página, o ☰ → Avisos (2) | **0** para saber si hay algo nuevo (globo); 1 toque para leer uno |
| 5.2 Ver mis actualizaciones sin leer | Estudiante | 1 (cambiar a la pestaña «Sin leer») | **0** (abre en «Sin leer» si hay novedades) |
| 2.8 Ver mis trámites finalizados | Estudiante | 4 (☰ → Mis solicitudes → filtro → Finalizada) | **1** (tocar «Trámites finalizados») |
| 1.x Registrar un trámite | Estudiante | ~6 | ~6 (sin cambio: el botón ya estaba arriba) |
| 2.7 Finalizar una solicitud en revisión | Coordinación | 4 clics + escribir la resolución | **2 clics** (fila → Registrar), sin texto |
| 2.4 Pasar una solicitud a revisión | Coordinación | 2 clics + escribir un comentario | **2 clics**, sin texto |
| 2.7 Rechazar | Coordinación | 4 clics + motivo | 3 clics + motivo |
| 2.5 Pedir información | Coordinación | 2 clics + texto (otra tarjeta) | 3 clics + texto (mismo panel) |
| 3.4 Ver solo lo pendiente | Coordinación | 2 (abrir el filtro de estado y elegir; por defecto salían mezcladas) | **0** (por defecto) |
| 2.8 Ver el historial | Coordinación | 2, y solo un estado a la vez | **1** (Finalizadas y rechazadas juntas) |

**Concesión honesta:** «Pedir información» cuesta un clic más que antes, porque ahora es una opción del panel único en lugar de su propia tarjeta. A cambio, el detalle muestra un solo formulario en lugar de tres.

## Archivos

| Tipo | Archivos |
|---|---|
| Base de datos | `bd/05-lectura-avisos.sql` (tabla `AVISO_LECTURA`, con RLS). La aplica `herramientas/configurar-local.ps1`; en Supabase se ejecuta a mano. Quedan **22 tablas**. |
| EJB | `AvisoRepository.leidosPor / marcarLeido`, `AvisoService.leidos / marcarLeido` (solo avisos de su carrera), `SolicitudService.cambiarEstado` (comentario opcional salvo al rechazar) |
| Control | `AvisosControl` (no leídos, desplegar, abrir), `DetalleSolicitudControl` (panel único, `acciones`), `SolicitudesControl.listado`, `BandejaControl` (activas / finalizadas), `InicioControl` (pestañas recortadas) |
| Objetos de vista | `ListaSolicitudes` (nuevo), `ComunicadoVista.leido`, `SolicitudFila.activa` |
| Componentes nuevos | `comp-avisos-desplegable`, `comp-lista-solicitudes`, `comp-selector-vista` |
| Retirados (en `_to_delete`) | `comp-avisos-vigentes`, `comp-solicitar-informacion` |
| Plantilla | `plantilla-panel.xhtml` (barra superior), `resources/js/panel.js` (☰), `coordinadito.css` |

## Cómo se probó

| Recorrido | Resultado |
|---|---|
| Estudiante en teléfono: orden del inicio, sobre con número, leer un aviso (baja el número y queda en gris), menú ☰, Activas/Finalizadas, `?ver=finalizadas` | 11 / 11 |
| Coordinación: solo activas por defecto, sin filtro de columna, búsqueda + cambio de vista + ordenar, bandeja, finalizar sin comentario, rechazo sin motivo bloqueado, pedir información desde el panel | 15 / 15 |
| Barra superior: ☰ contrae y se recuerda, menú de cuenta, salir | 3 / 3 |
| Regresión: grupos, agenda, avisos (27), roles, padrón, alumnos, jefatura, auditoría (36), OIDC (7), Sprints 1–2 (5) | 75 / 75 |
| Humo: todas las pantallas × 4 roles | sin excepciones |
| Revisor de encimados a 390, 768, 1024 y 1366 px | sin desbordes reales |

## Segunda ronda (estudiante, 18 nov 2026)

| Pantalla | Cambio |
|---|---|
| Inicio | Los avisos **siempre empiezan plegados** (el número del sobre dice si hay algo nuevo). La bandeja **siempre abre en «Sin leer»**. Se quitó el botón «Solicitar un trámite» de arriba. |
| Inicio | «Mis solicitudes recientes» se sustituyó por la sección **«Mis solicitudes» completa** (botón «Nueva solicitud», selector Activas / Finalizadas y tarjetas). Es el componente `comp-mis-solicitudes`, el mismo que usa `vista/solicitudes`: una sola copia. Los contadores «en curso» y «finalizados» eligen la vista y bajan a la sección. |
| Barra lateral | El estudiante solo tiene **Inicio, Trámites, Apertura de grupos y Cerrar sesión**. Mi perfil va en el avatar, Notificaciones en la campana, Mis solicitudes en el inicio y Avisos en el sobre: un solo camino a cada lugar. Coordinación, jefatura y administración no cambian. |
| Notificaciones | Sin la tarjeta «Cómo te avisamos» ni el aviso y el enlace a «Avisos de la carrera»; la lista queda en una sola columna. |
| Apertura de grupos | Sin la tarjeta «¿Tu materia ya está en la lista?». |
| Todas las páginas | Se eliminaron los textos azules en mayúsculas sobre el título (por ejemplo «Una sola lista por materia», «Coordinación a tu alcance»). |

Pruebas: 21/21 del recorrido nuevo; regresión 28 + 36 + 5 y humo sin fallos. El regreso desde el detalle de una solicitud lleva al estudiante al inicio.

## Tercera ronda: coordinación y jefatura (19 nov 2026)

### La idea en una analogía

Es la misma regla que con el estudiante: **una sola puerta por oficina**. Antes, coordinación tenía un «Inicio» con una tabla de solicitudes y, además, una «Bandeja» con otra tabla de las mismas solicitudes: dos puertas al mismo cuarto. Jefatura tenía tres puertas para el seguimiento (volumen, tiempos, rezagos) y dos más (periodos, prioridades) para cosas que ya se editan dentro de cada trámite.

### Menús

| Rol | Antes | Ahora |
|---|---|---|
| Coordinación | 11 opciones: Inicio, Bandeja, Agenda, Alumnos, Grupos, Notificaciones, Avisos, Personal, Padrón, Auditoría, Mi perfil | **8**: Bandeja (su entrada), Agenda, Alumnos, Apertura de grupos, Avisos, Personal de mi carrera, Padrón, Auditoría |
| Jefatura | 16 opciones | **9**: Seguimiento (su entrada), Solicitudes, Alumnos, Demanda de grupos, Avisos, Catálogo de trámites, Roles y carreras, Padrón, Auditoría |

Para todos los roles, Notificaciones va en la campana y Mi perfil en el avatar. `/vista/inicio` manda a cada rol a donde trabaja (`sesionControl.inicioDelRol`): coordinación a la bandeja, jefatura al seguimiento; el estudiante se queda en su inicio.

### Pantallas

| Pantalla | Cambio |
|---|---|
| Bandeja (coordinación) | Es su pantalla de entrada, con saludo. Cuatro tarjetas grandes → un resumen de cuatro números. A la vista solo **buscar** y **Activas / Finalizadas**; trámite, prioridad, fechas y orden van en **«Más filtros»** (el botón dice cuántos hay puestos, para que nunca filtre a escondidas). Sin chips ni párrafo de ayuda. En el teléfono, **tarjetas** en lugar de la tabla apilada. |
| Detalle de solicitud | **Una sola línea de tiempo «Seguimiento»**: los cambios de estado y las elevaciones de prioridad juntos (antes, tarjeta aparte). Así se cumple al pie de la letra la HU 3.3 («la elevación queda asentada en la bitácora»). El alumno no ve las elevaciones (son internas). «Cita presencial» va **plegada** mientras no se pida. La caja de apertura de grupo pasó a ser un solo botón. |
| Agenda | «Citas del día» primero (es lo diario); «Publicar horario» **plegado** cuando ya hay horarios, y recuerda si lo abriste. |
| Detalle del alumno | «Datos escolares» y «Corregir datos escolares» → **una sola tarjeta** (el formulario ya muestra los datos actuales). Sin el diálogo de confirmación. |
| Padrón | Sin la columna de métrica ni la caja «Qué pasa al cargar»: el número de vigentes va bajo el título y la explicación, en el subtítulo de la carga. |
| Personal, grupo, avisos | Sin las cajas «Queda registrado», «Decidir la apertura» y «Solo aquí es oficial». |
| Seguimiento (jefatura) | Volumen, Tiempos y Rezagos son **pestañas** de un solo lugar del menú. Es su pantalla de entrada. |
| Periodos y Prioridades | **Retiradas** (a `_to_delete`): ya se editan dentro de cada trámite del catálogo. También `PeriodosControl`, `PrioridadesControl`, `comp-periodo` y, en `TramiteService`, `definirPeriodo`, `definirPrioridad` y `definirActivo`, que solo usaban esas pantallas. |
| Inicio de jefatura y de coordinación | Retirados: repetían la demanda de grupos, el volumen por carrera y la tabla de solicitudes. |

### Clics por operación (simulados)

| Operación | Antes | Ahora |
|---|---|---|
| Coordinación: ver lo pendiente al entrar | 1 (menú Bandeja) o una tabla repetida en Inicio | **0** |
| Coordinación: finalizar una solicitud en revisión | 2 | 2 |
| Coordinación: ver las citas del día | 1 y bajar más allá del formulario | **1** |
| Coordinación: corregir datos de un alumno | 4 (con confirmación) | **3** |
| Coordinación: filtrar por trámite, prioridad o fecha | 1 | 2 (abrir «Más filtros») |
| Coordinación: pedir una cita presencial | el formulario a la vista | +1 (abrir el panel) |
| Jefatura: ver el seguimiento al entrar | 1 | **0** |
| Jefatura: cambiar el periodo o la prioridad de un trámite | 2–3 (pantalla aparte) | 3–4 (Catálogo → Editar) |

**Concesión honesta:** lo que se usa todos los días cuesta menos clics; lo excepcional (filtros avanzados, pedir cita, configurar periodos y prioridades una vez por semestre) cuesta uno más. Es la misma regla que con «Subir prioridad»: lo raro no debe competir con lo diario.

### Cómo se probó

| Recorrido | Resultado |
|---|---|
| Esta ronda: menús, entrada por rol, bandeja y «Más filtros», línea de tiempo con prioridad (y que el alumno no la vea), cita y agenda plegadas, alumno en una tarjeta, pestañas, pantallas retiradas | 23 / 23 |
| Rondas anteriores (estudiante, sin administración) | 21 / 21 y 29 / 29 |
| Regresión de los sprints: grupos, agenda y avisos (28); roles, padrón, alumnos, jefatura y auditoría (36); OIDC (7); Sprints 1–2 (5) | 76 / 76 |
| Humo: todas las pantallas × 3 roles | sin excepciones |
| Revisor de encimados a 390, 1024 y 1366 px en las pantallas que cambiaron | sin encimados (corregidas las tablas apiladas en el teléfono) |
