# CoordinadITO · Verificación de la demo local (25 nov 2026)

## La idea en una analogía

Es el **ensayo general** antes del estreno: misma sala (tu laptop), mismo escenario (Payara + PostgreSQL locales) y los tres actores (estudiante, coordinación y jefatura) recorren la obra completa. Lo que se trabó en el ensayo se corrigió antes de la función.

## Cómo se montó

| Pieza | Estado |
|---|---|
| Servidor | Payara 7 de tu PC, dominio `coordinadito`, puerto 8080 |
| Base | PostgreSQL 18 local, base `coordinadito` |
| Migraciones | Al desplegar se aplicaron solas 05, 06 y 07 (`MigracionesService`); la base quedó con 23 tablas y los 20 modelos cuadran |
| Datos | Demo: 20 usuarios (17 alumnos de 4 carreras, 3 coordinaciones, jefatura), 6 trámites, 34 solicitudes |
| Arranque | `_demo\levantar-demo.bat` (doble clic): enciende el dominio y despliega `_demo\CoordinadITO.ear` |
| URL | http://localhost:8080/CoordinadITO-war/ — accesos de prueba en el login (modo desarrollo) |

## El flujo completo que se simuló

Protagonista: **Fernanda López (ISC, 3.er semestre)**; la atiende **Elena Ramírez (Coordinación ISC)**; supervisa **Patricia Ortiz (Jefatura)**.

| # | Quién | Qué hizo | HU | Resultado |
|---|---|---|---|---|
| 1 | Fernanda | Consulta el catálogo; «Baja temporal» aparece con periodo cerrado y su fecha | 1.1, 1.4 | ✓ |
| 2 | Fernanda | Envía el formulario de «Baja de materia» vacío: marca cada obligatorio | 1.2 | ✓ |
| 3 | Fernanda | Lo llena (número de control y carrera vienen de su cuenta, no se editan) y obtiene folio **ITO-0035** | 1.2, 2.2, 6.2 | ✓ |
| 4 | Fernanda | Intenta una segunda baja: el servidor la rechaza citando ITO-0035 | 2.9 | ✓ (ver mejora 4) |
| 5 | Elena | La solicitud aparece en su bandeja, ordenada por prioridad y antigüedad | 2.1, 3.1 | ✓ |
| 6 | Elena | Pide información («indica grupo y docente») | 2.5 | ✓ |
| 7 | Elena | Sube la prioridad Alta → Crítica con motivo; queda en la bitácora | 3.3 | ✓ (ver defecto 2) |
| 8 | Fernanda | Recibe la notificación y responde desde la misma solicitud; vuelve a «En revisión» | 2.6, 5.2 | ✓ |
| 9 | Fernanda | Se adhiere al grupo de Simulación (8 → 9), se retira (9 → 8, con confirmación) | 4.2, 4.3 | ✓ |
| 10 | Fernanda | Intenta abrir otra solicitud de Simulación: el sistema la manda a la existente antes de enviar | 4.1 | ✓ |
| 11 | Elena | Pide cita presencial | 9.2 | ✓ (ver mejora 5) |
| 12 | Elena | Publica un aviso para ISC (solo puede elegir sus carreras) | 5.3 | ✓ (ver mejora 6) |
| 13 | Elena | Publica horario de mañana: 9 bloques de 20 min | 9.1 | ✓ |
| 14 | Fernanda | Ve el aviso nuevo y agenda su cita a las 10:20 | 5.3, 9.2 | ✓ |
| 15 | Elena | Rechazar sin motivo: bloqueado. Finaliza con comentario; ya no admite cambios | 2.7 | ✓ |
| 16 | Patricia | Seguimiento: volumen, tiempos, rezagos; demanda de grupos; bitácora de otra carrera; roles, catálogo, padrón, auditoría | 2.8, 4.4, 7.1–7.3, 8.x | ✓ |

## Seguridad

| Prueba | Resultado |
|---|---|
| Estudiante abre solicitudes ajenas por folio (1, 2, 20…) | Bloqueado: «no disponible», igual que un folio inexistente (no se puede saber cuáles existen) |
| Estudiante abre 11 pantallas de coordinación/jefatura por URL | Todas «Sin acceso» |
| Coordinación ISC recorre los folios 1–36 | Ve solo los 24 de ISC; IC, IGE e II bloqueados |
| Inyección de HTML/JS en un campo (`<img onerror=…>`) | Se muestra como texto en las 3 pantallas donde aparece; no se ejecuta |
| Folio malicioso en la URL (`abc`, `<script>`) | Manejado, sin error |
| Cookie de sesión | `HttpOnly` (un script no puede leerla); sesión de 30 min |
| Tras cerrar sesión | Cualquier página privada manda al login |
| Prioridad interna y su motivo | No los ve el estudiante |
| Auditoría | Registra cada acceso con usuario, fecha e IP |

## Fluidez

- Páginas completas: **0.1–0.5 s** en la laptop (TTFB ≈ 0.3 s en la primera carga).
- Celular (375 px): sin desbordes horizontales en las pantallas revisadas.

## Defectos encontrados y corregidos

| # | Defecto | Riesgo | Corrección |
|---|---|---|---|
| 1 | **Error 500 con «vista caducada»**: tras 30 min de inactividad, o con muchas pestañas, cualquier botón (incluso «Cerrar sesión») mostraba «Algo no salió como esperábamos» | Alto en una demo | `ManejadorExcepciones` (registrado en `faces-config.xml`): si la sesión sigue, recarga la misma página con un aviso; si venció, manda al login con «Tu sesión terminó» |
| 2 | Paneles «Subir prioridad» y «Cita presencial» se plegaban al fallar la validación y escondían el error | Medio (parecía que no pasaba nada) | El botón actualiza solo su formulario; si sale bien, el control redibuja el detalle |
| 3 | **Diagnóstico público** (sin iniciar sesión) mostraba base, usuario `postgres` y tablas; también en producción | Alto en producción | Pública solo en desarrollo; en producción, solo jefatura |
| 4 | Faltaban cabeceras de seguridad | Medio | `nosniff`, CSP `frame-ancestors/form-action/base-uri/object-src`, `Referrer-Policy`, `Permissions-Policy` |

## Mejoras de flujo aplicadas

| # | Antes | Ahora |
|---|---|---|
| 4 | El aviso de trámite duplicado llegaba al confirmar, tras llenar todo | Al abrir el trámite: «Ya tienes ITO-0035 en curso» con enlace, sin formulario |
| 5 | Coordinación podía pedir cita sin tener horarios; el alumno veía «no hay horarios» | Aviso a coordinación: «No tienes horarios libres; publícalos en Agenda» |
| 6 | El aviso oficial exigía elegir ambas fechas | Propone desde hoy y durante una semana |

## Pendientes honestos

- **«Admin Sistema»** aparece como responsable de la carga de padrón de la demo: es la cuenta de administración que la migración 06 convirtió en jefatura. Jefatura puede desactivarla en «Roles y carreras».
- **Correo**: no está configurado en la laptop (por eso el login no ofrece el código). Para mostrarlo: `herramientas\configurar-correo.ps1`.
- **Exportar historial (7.4)** no se descargó en el recorrido (requiere guardar un archivo); está cubierto por la prueba automática `flujos2`.
- **Folios consecutivos**: son adivinables, pero el servidor valida el permiso en cada consulta; no es una fuga.
- Falta `HTTPS` (y con él la bandera `Secure` de la cookie): aplica al desplegar en un servidor real.
