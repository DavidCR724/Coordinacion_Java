# CoordinadITO · Se retira el rol de administración

## La idea en una analogía

Antes había **un conserje con todas las llaves** (administración): abría cualquier oficina aunque no trabajara en ninguna. Ahora las llaves se repartieron entre quienes sí trabajan ahí:

- Cada **coordinación** tiene las llaves de **su propia oficina** (su carrera): su personal, su padrón y su bitácora.
- **Jefatura** tiene un **llavero maestro** (todas las carreras) y, además, las llaves de las **áreas comunes** del edificio: el catálogo de trámites, los periodos y las prioridades, porque cambiarlos afecta a todas las carreras a la vez.

## Reparto de permisos

La tabla vive en un solo lugar: `modelo.bean.Permiso` (patrón *Policy*). `Guardia.alcance(actor)` devuelve las carreras del actor (`null` = todas, si es jefatura).

| Permiso | Estudiante | Coordinación | Jefatura | Qué cubre |
|---|---|---|---|---|
| SOLICITAR | ✓ | | | Trámites, grupos, citas |
| ATENDER | | ✓ (sus carreras) | ✓ | Bandeja, atención, avisos, alumnos |
| AGENDAR_ATENCION | | ✓ | | Horarios y agenda |
| SEGUIMIENTO | | | ✓ | Volumen, tiempos, rezagos, exportación |
| **GESTIONAR_CARRERA** (nuevo) | | ✓ (sus carreras) | ✓ (todas) | Personal (HU 6.3), padrón (8.1), auditoría (8.2) |
| **CONFIGURAR** (nuevo) | | | ✓ | Catálogo (8.3), periodos (1.3), prioridades (3.2) |

### Lo que puede y no puede hacer una coordinación

| Pantalla | Puede | No puede |
|---|---|---|
| **Personal de mi carrera** | Ver las coordinaciones de sus carreras; dar de alta a alguien de apoyo **con el rol de coordinación y solo con sus carreras**; desactivar cuentas de su carrera | Ver ni tocar jefatura u otras carreras; otorgar el rol de jefatura; cambiar su propio rol |
| **Padrón institucional** | Cargar el padrón de sus carreras | Cargar filas de otra carrera (se rechazan por fila); llevarse a un alumno que hoy es de otra carrera; retirar alumnos de otras carreras (los ausentes solo se retiran dentro de sus carreras) |
| **Auditoría** | Ver lo que hicieron los alumnos y la coordinación de sus carreras | Ver lo que hizo jefatura, otras carreras o los intentos de acceso sin cuenta |
| Catálogo, periodos, prioridades | — | Son comunes a todas las carreras: los cambia jefatura (la pantalla muestra «Sin acceso a esta sección») |

Todo se valida en el **servidor** (EJB), no solo escondiendo opciones: aunque alguien fabrique la petición, `Guardia` la rechaza.

### Concesión honesta

El catálogo de trámites es **uno solo para todas las carreras** (la tabla `TRAMITE` no tiene carrera). Por eso la coordinación no puede editar trámites, periodos ni prioridades: si lo hiciera, cambiaría también los de las demás carreras. Si en el futuro cada carrera necesita sus propios periodos o trámites, habría que agregar una relación `TRAMITE_CARRERA`; es un cambio de modelo, no de pantallas.

## Base de datos: `bd/06-sin-administracion.sql`

1. Las cuentas que eran `ADMINISTRADOR` pasan a `JEFE` (conservan el acceso y su historial).
2. `CHK_SIN_ADMINISTRACION` impide volver a usar ese rol. (El valor sigue en el tipo `ROLES`: PostgreSQL no permite quitarlo sin recrear el tipo.)
3. `CARGA_PADRON.ID_ADMINISTRADOR` se llama ahora `ID_RESPONSABLE`.

`configurar-local.ps1` la aplica sola si falta. En Supabase se ejecuta a mano en el SQL Editor. Los datos de demo (`95`) y de prueba (`90`) ya no crean la cuenta `administracion@`; sus acciones quedaron a nombre de jefatura.

**En una base que ya tenía la demo cargada**, la cuenta «Admin Sistema» queda como jefatura. Jefatura puede desactivarla desde «Roles y carreras».

## Además: criterios de aceptación que faltaban

| HU | Criterio | Qué se hizo |
|---|---|---|
| 3.1 | 3. Alternar entre orden por prioridad y por fecha | Selector «Ordenar por: Prioridad / Antigüedad» en la bandeja |
| 3.4 | 1. Filtrar por rango de fechas | «Registradas desde» y «Hasta» en los filtros de la bandeja |
| 1.2 | 3. Lo capturado se conserva si el alumno sale y regresa | Borrador en `sessionStorage` (por trámite y alumno). Se usa `sessionStorage` y no `localStorage` a propósito: en una PC compartida del centro de cómputo, el borrador se borra al cerrar la pestaña. Se limpia al registrar la solicitud. |

## Historias del backlog que conviene actualizar (Product Owner)

Con este cambio, algunas historias ya no corresponden a lo construido. En Scrum, es el Product Owner quien las ajusta:

| HU | Dice hoy | Debería decir |
|---|---|---|
| 1.3, 3.2, 8.3 | Usuario: Administrador | Usuario: Jefatura (configuración común a todas las carreras) |
| 6.3 | El administrador asigna rol y carreras | Jefatura asigna cualquier rol; la coordinación, solo el de coordinación y con sus carreras |
| 8.1 | El administrador carga el padrón | La coordinación carga el de su carrera; jefatura, el de todas |
| 8.2 | Criterio 2: «Solo el administrador consulta la bitácora» | Jefatura consulta toda; la coordinación, la de su carrera |
| 2.7 | Criterio 1: «el registro del motivo es obligatorio» | Obligatorio al **rechazar**; opcional al finalizar (decisión del refactor de interfaces) |

## Cómo se probó

| Recorrido | Resultado |
|---|---|
| Demo sin administración: login, menú y pantallas de jefatura; coordinación sin acceso a lo común; personal, padrón y auditoría limitados a ISC; orden y fechas de la bandeja; borrador del formulario | 33 / 33 |
| Regresión: grupos, agenda y avisos (28); roles, padrón, alumnos, jefatura y auditoría con jefatura (36); OIDC (7); Sprints 1–2 (5) | 76 / 76 |
| Humo: todas las pantallas × 3 roles | sin excepciones |
| Migración 06 sobre una base que ya tenía la demo, y base nueva 01–06 + 95 | correctas; el rol queda bloqueado |
| Revisor de encimados a 1024 y 1366 px en las pantallas de configuración | sin desbordes |
