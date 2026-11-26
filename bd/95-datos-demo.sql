-- =====================================================================
-- CoordinadITO · 95 · Datos de DEMOSTRACIÓN (todas las personas son ficticias)
-- Ejecutar DESPUÉS de 01, 02, 03 y 04. Puede convivir con 90-datos-prueba.sql.
--
-- Qué deja listo para la demo:
--   · 4 carreras, coordinación de cada una y jefatura (sin administración: ver bd/06).
--   · Padrón de 16 alumnos (13 con cuenta) y un alumno con datos autodeclarados.
--   · 6 trámites (dos nuevos: Constancia de estudios y Revisión de calificación).
--   · 33 solicitudes en todos los estados, con bitácora, mensajes, respuestas del
--     formulario, una prioridad elevada y notificaciones (algunas sin leer).
--   · 4 aperturas de grupo con alumnos adheridos (una ya resuelta).
--   · Solicitudes rezagadas y resueltas a tiempo y fuera de tiempo (paneles de jefatura),
--     incluidas tres del periodo anterior (Ene–Jun 2026).
--   · Avisos vigentes, programado y concluido; horarios de atención y una cita.
--   · Registros de auditoría.
-- Solo AGREGA datos: no borra ni modifica lo que ya existe (salvo abrir el periodo de
-- «Baja de materia» para que se pueda registrar durante la demo).
-- Todo va en una transacción: si algo falla, no queda nada a medias.
-- Las notificaciones se marcan como ya enviadas por correo, para que CorreoService
-- nunca intente escribir a estas cuentas ficticias.
-- =====================================================================

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'usuario' AND column_name = 'origen_datos') THEN
        RAISE EXCEPTION 'Falta la migración 04: ejecuta primero bd/04-migracion-v3.sql';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'carga_padron' AND column_name = 'id_responsable') THEN
        RAISE EXCEPTION 'Falta la migración 06: ejecuta primero bd/05-lectura-avisos.sql y bd/06-sin-administracion.sql';
    END IF;
    IF EXISTS (SELECT 1 FROM CARGA_PADRON WHERE NOMBRE_ARCHIVO = 'padron-demo-ago-dic-2026.csv') THEN
        RAISE EXCEPTION 'Los datos de demostración ya están cargados (no se ejecuta dos veces).';
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- Catálogos
-- ---------------------------------------------------------------------
INSERT INTO CARRERA (CLAVE, NOMBRE) VALUES
 ('ISC', 'Ingeniería en Sistemas Computacionales'),
 ('IC',  'Ingeniería Civil'),
 ('IGE', 'Ingeniería en Gestión Empresarial'),
 ('II',  'Ingeniería Industrial')
ON CONFLICT DO NOTHING;

-- Personal (sin datos escolares).
INSERT INTO USUARIO (NOMBRES, APELLIDO_P, APELLIDO_M, CORREO, ROL, FECHA_ALTA, ULTIMO_ACCESO) VALUES
 ('Elena',    'Ramírez', 'Ruiz',    'coordinacion.isc@itoaxaca.edu.mx', 'COORDINADOR',   NOW() - INTERVAL '120 days', NOW() - INTERVAL '3 hours'),
 ('Mario',    'Díaz',    NULL,      'coordinacion.ic@itoaxaca.edu.mx',  'COORDINADOR',   NOW() - INTERVAL '120 days', NOW() - INTERVAL '1 day'),
 ('Laura',    'Méndez',  'Cruz',    'coordinacion.ige@itoaxaca.edu.mx', 'COORDINADOR',   NOW() - INTERVAL '110 days', NOW() - INTERVAL '2 days'),
 ('Patricia', 'Ortiz',   'Vásquez', 'jefatura@itoaxaca.edu.mx',         'JEFE',          NOW() - INTERVAL '120 days', NOW() - INTERVAL '2 days')
ON CONFLICT DO NOTHING;

INSERT INTO COORDINACION_CARRERA (ID_USUARIO, ID_CARRERA)
SELECT u.ID_USUARIO, c.ID_CARRERA
FROM (VALUES ('coordinacion.isc', 'ISC'), ('coordinacion.ic', 'IC'), ('coordinacion.ic', 'II'),
             ('coordinacion.ige', 'IGE')) x(cuenta, carrera)
JOIN USUARIO u ON u.CORREO = x.cuenta || '@itoaxaca.edu.mx' AND u.ROL = 'COORDINADOR'
JOIN CARRERA c ON c.CLAVE = x.carrera
ON CONFLICT DO NOTHING;

-- Trámites: los de 90 (si no existen) y dos nuevos.
INSERT INTO TRAMITE (CLAVE, NOMBRE, PRIORIDAD, DESCRIPCION, REQUISITOS, DOC_REQ, ES_COLECTIVO, INICIO_PERIODO, FIN_PERIODO, DIAS_ESPERADOS) VALUES
 ('BM', 'Baja de materia', 'ALTA', 'Baja parcial de una materia', E'Identificar la materia y el grupo.\nIndicar el motivo de la baja.', FALSE, FALSE, CURRENT_DATE - 30, CURRENT_DATE + 30, 3),
 ('CH', 'Cambio de horario', 'MEDIA', 'Cambio de grupo', E'Horario actual en PDF.', TRUE, FALSE, NULL, NULL, 5),
 ('BT', 'Baja temporal', 'BAJA', 'Baja del semestre', NULL, FALSE, FALSE, CURRENT_DATE - 120, CURRENT_DATE - 10, 10),
 ('AG', 'Apertura de grupo', 'MEDIA', 'Pide que se abra un grupo de una materia. Si alguien ya lo pidió, súmate a esa solicitud.',
  E'Elegir la materia de tu carrera.\nUna sola solicitud por materia: los demás se adhieren.', FALSE, TRUE, NULL, NULL, 15),
 ('CE', 'Constancia de estudios', 'BAJA', 'Constancia de que estás inscrito, con o sin promedio.',
  E'No tener adeudos en biblioteca ni en servicios escolares.', FALSE, FALSE, NULL, NULL, 2),
 ('RC', 'Revisión de calificación', 'ALTA', 'Pide que se revise una calificación capturada en el SII.',
  E'Indicar materia y unidad.\nExplicar el motivo de la revisión.', FALSE, FALSE, NULL, NULL, 5)
ON CONFLICT DO NOTHING;
-- Que «Baja de materia» esté abierta durante la demo.
UPDATE TRAMITE SET INICIO_PERIODO = CURRENT_DATE - 30, FIN_PERIODO = CURRENT_DATE + 30 WHERE CLAVE = 'BM';

INSERT INTO CAMPO_FORMULARIO (ID_TRAMITE, ETIQUETA, TIPO, OBLIGATORIO, ORDEN, LONGITUD_MAX, OPCIONES)
SELECT t.ID_TRAMITE, x.etiqueta, x.tipo::TIPO_CAMPO, x.obligatorio, x.orden, x.longitud, x.opciones
FROM (VALUES
  ('BM', 'Materia', 'TEXTO', TRUE, 1, 100, NULL),
  ('BM', 'Motivo', 'SELECCION', TRUE, 2, NULL, E'Salud\nTrabajo\nOtro'),
  ('BM', 'Créditos', 'NUMERO', FALSE, 3, NULL, NULL),
  ('RC', 'Materia', 'TEXTO', TRUE, 1, 100, NULL),
  ('RC', 'Unidad', 'NUMERO', TRUE, 2, NULL, NULL),
  ('RC', 'Motivo de la revisión', 'TEXTO_LARGO', TRUE, 3, 1000, NULL)
) x(clave, etiqueta, tipo, obligatorio, orden, longitud, opciones)
JOIN TRAMITE t ON t.CLAVE = x.clave
WHERE NOT EXISTS (SELECT 1 FROM CAMPO_FORMULARIO c WHERE c.ID_TRAMITE = t.ID_TRAMITE AND (c.ORDEN = x.orden OR c.ETIQUETA = x.etiqueta));

INSERT INTO MATERIA (CLAVE, NOMBRE, ID_CARRERA, SEMESTRE, CREDITOS)
SELECT x.clave, x.nombre, c.ID_CARRERA, x.sem, x.cred
FROM (VALUES
  ('SCD-1027', 'Tópicos Avanzados de Programación', 'ISC', 5, 5),
  ('SCC-1017', 'Métodos Numéricos', 'ISC', 5, 4),
  ('AEF-1031', 'Fundamentos de Bases de Datos', 'ISC', 4, 5),
  ('SCD-1022', 'Simulación', 'ISC', 6, 5),
  ('ACA-0909', 'Taller de Investigación I', 'ISC', 6, 4),
  ('SCD-1003', 'Arquitectura de Computadoras', 'ISC', 5, 5),
  ('ICC-1012', 'Mecánica de Suelos', 'IC', 5, 4),
  ('ICF-1020', 'Hidráulica Básica', 'IC', 4, 5),
  ('GED-0908', 'Contabilidad Orientada a Negocios', 'IGE', 3, 5),
  ('INC-1018', 'Investigación de Operaciones', 'II', 5, 4)
) x(clave, nombre, carrera, sem, cred)
JOIN CARRERA c ON c.CLAVE = x.carrera
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- Padrón (HU 8.1) y alumnos
-- ---------------------------------------------------------------------
INSERT INTO CARGA_PADRON (ID_RESPONSABLE, NOMBRE_ARCHIVO, FECHA, TOTAL_REGISTROS, NUEVOS, ACTUALIZADOS, RECHAZADOS)
SELECT ID_USUARIO, 'padron-demo-ago-dic-2026.csv', NOW() - INTERVAL '100 days', 17, 15, 2, 0
FROM USUARIO WHERE CORREO = 'jefatura@itoaxaca.edu.mx';

INSERT INTO PADRON_ALUMNO (NUM_CONTROL, CORREO, NOMBRES, APELLIDO_P, APELLIDO_M, ID_CARRERA, SEMESTRE, ID_CARGA)
SELECT x.nc, x.correo, x.nom, x.ap, x.am, c.ID_CARRERA, x.sem,
       (SELECT ID_CARGA FROM CARGA_PADRON WHERE NOMBRE_ARCHIVO = 'padron-demo-ago-dic-2026.csv')
FROM (VALUES
 ('00000001', '00000001@itoaxaca.edu.mx', 'Sofía', 'Hernández', 'López', 'ISC', 7),
 ('00000002', '00000002@itoaxaca.edu.mx', 'Luis', 'Pérez', NULL, 'ISC', 5),
 ('00000003', '00000003@itoaxaca.edu.mx', 'Ana', 'García', 'Torres', 'IC', 3),
 ('99160101', '99160101@itoaxaca.edu.mx', 'Mariana', 'Cruz', 'Santiago', 'ISC', 5),
 ('99160102', '99160102@itoaxaca.edu.mx', 'Diego', 'Martínez', 'Ruiz', 'ISC', 5),
 ('99160103', '99160103@itoaxaca.edu.mx', 'Fernanda', 'López', 'Jiménez', 'ISC', 3),
 ('99160104', '99160104@itoaxaca.edu.mx', 'Jorge', 'Ramírez', 'Aquino', 'ISC', 7),
 ('99160105', '99160105@itoaxaca.edu.mx', 'Valeria', 'Santos', 'Mendoza', 'ISC', 9),
 ('99160106', '99160106@itoaxaca.edu.mx', 'Ricardo', 'Vásquez', 'Ortiz', 'ISC', 6),
 ('99160107', '99160107@itoaxaca.edu.mx', 'Daniela', 'Reyes', 'Castillo', 'ISC', 1),
 ('99160201', '99160201@itoaxaca.edu.mx', 'Alejandro', 'Morales', 'Pérez', 'IC', 5),
 ('99160202', '99160202@itoaxaca.edu.mx', 'Paola', 'Gómez', 'Hernández', 'IC', 7),
 ('99160203', '99160203@itoaxaca.edu.mx', 'Emilio', 'Silva', 'Luna', 'IC', 3),
 ('99160301', '99160301@itoaxaca.edu.mx', 'Camila', 'Torres', 'Juárez', 'IGE', 4),
 ('99160302', '99160302@itoaxaca.edu.mx', 'Héctor', 'Díaz', 'Rojas', 'IGE', 6),
 ('99160401', '99160401@itoaxaca.edu.mx', 'Itzel', 'Ramos', 'Cortés', 'II', 5),
 ('99160402', '99160402@itoaxaca.edu.mx', 'Bruno', 'Castro', 'Flores', 'II', 8)
) x(nc, correo, nom, ap, am, carrera, sem)
JOIN CARRERA c ON c.CLAVE = x.carrera
ON CONFLICT DO NOTHING;

INSERT INTO USUARIO (NOMBRES, APELLIDO_P, APELLIDO_M, CORREO, ROL, ID_CARRERA, NUM_CONTROL, SEMESTRE, ORIGEN_DATOS, FECHA_ALTA, ULTIMO_ACCESO)
SELECT x.nom, x.ap, x.am, x.correo, 'ESTUDIANTE', c.ID_CARRERA, x.nc, x.sem, x.origen::ORIGEN_DATOS,
       NOW() - make_interval(days => x.alta), NOW() - make_interval(hours => (x.acceso * 24)::int)
FROM (VALUES
 
 ('Sofía', 'Hernández', 'López', '00000001@itoaxaca.edu.mx', 'ISC', '00000001', 7, 'PADRON', 100, 0.2),
 ('Luis', 'Pérez', NULL, '00000002@itoaxaca.edu.mx', 'ISC', '00000002', 5, 'PADRON', 97, 1.1),
 ('Mariana', 'Cruz', 'Santiago', '99160101@itoaxaca.edu.mx', 'ISC', '99160101', 5, 'PADRON', 94, 2.0),
 ('Diego', 'Martínez', 'Ruiz', '99160102@itoaxaca.edu.mx', 'ISC', '99160102', 5, 'PADRON', 91, 2.9),
 ('Fernanda', 'López', 'Jiménez', '99160103@itoaxaca.edu.mx', 'ISC', '99160103', 3, 'PADRON', 88, 3.8),
 ('Jorge', 'Ramírez', 'Aquino', '99160104@itoaxaca.edu.mx', 'ISC', '99160104', 7, 'PADRON', 85, 4.7),
 ('Valeria', 'Santos', 'Mendoza', '99160105@itoaxaca.edu.mx', 'ISC', '99160105', 9, 'PADRON', 82, 5.6),
 ('Ricardo', 'Vásquez', 'Ortiz', '99160106@itoaxaca.edu.mx', 'ISC', '99160106', 6, 'VALIDADO', 79, 0.2),
 ('Alejandro', 'Morales', 'Pérez', '99160201@itoaxaca.edu.mx', 'IC', '99160201', 5, 'PADRON', 76, 1.1),
 ('Paola', 'Gómez', 'Hernández', '99160202@itoaxaca.edu.mx', 'IC', '99160202', 7, 'PADRON', 73, 2.0),
 ('Emilio', 'Silva', 'Luna', '99160203@itoaxaca.edu.mx', 'IC', '99160203', 3, 'PADRON', 70, 2.9),
 ('Camila', 'Torres', 'Juárez', '99160301@itoaxaca.edu.mx', 'IGE', '99160301', 4, 'PADRON', 67, 3.8),
 ('Héctor', 'Díaz', 'Rojas', '99160302@itoaxaca.edu.mx', 'IGE', '99160302', 6, 'PADRON', 64, 4.7),
 ('Itzel', 'Ramos', 'Cortés', '99160401@itoaxaca.edu.mx', 'II', '99160401', 5, 'PADRON', 61, 5.6),
 ('Andrés', 'Navarro', 'Gil', '99160150@itoaxaca.edu.mx', 'ISC', '99160150', 4, 'AUTODECLARADO', 58, 0.2)
) x(nom, ap, am, correo, carrera, nc, sem, origen, alta, acceso)
JOIN CARRERA c ON c.CLAVE = x.carrera
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- Solicitudes con su bitácora, mensajes, formulario y notificaciones
-- ---------------------------------------------------------------------
WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'FINALIZADA', 'Cambié de horario en mi trabajo y ya no alcanzo a llegar a la clase.', 'Baja aplicada en el SII. Tu carga queda en 30 créditos.', NOW() - INTERVAL '960 hours',
         NOW() - INTERVAL '888 hours', NOW() - INTERVAL '888 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'BM' AND a.NUM_CONTROL = '00000001'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 40), ('ENVIADA', 'PROCESANDO', 'c', 'Revisando tu carga académica.', 39), ('PROCESANDO', 'FINALIZADA', 'c', 'Baja aplicada en el SII. Tu carga queda en 30 créditos.', 37)) v(ant, nuevo, quien, com, dias)),
r AS (
  INSERT INTO RESPUESTA_CAMPO (ID_SOLICITUD, ID_CAMPO, VALOR)
  SELECT s.ID_SOLICITUD, c.ID_CAMPO, v.valor
  FROM s, (VALUES ('Materia', 'Métodos Numéricos'), ('Motivo', 'Trabajo'), ('Créditos', '5')) v(etiqueta, valor)
  JOIN CAMPO_FORMULARIO c ON c.ETIQUETA = v.etiqueta
  WHERE c.ID_TRAMITE = s.ID_TRAMITE AND c.ACTIVO),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 40),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Revisando tu carga académica.', 39),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: Baja aplicada en el SII. Tu carga queda en 30 créditos.', 37)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'FINALIZADA', 'Constancia para trámite de beca de transporte.', 'Constancia lista. Recógela en ventanilla de coordinación.', NOW() - INTERVAL '480 hours',
         NOW() - INTERVAL '456 hours', NOW() - INTERVAL '456 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'CE' AND a.NUM_CONTROL = '00000001'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 20), ('ENVIADA', 'PROCESANDO', 'c', 'En elaboración.', 19.8), ('PROCESANDO', 'FINALIZADA', 'c', 'Constancia lista. Recógela en ventanilla de coordinación.', 19)) v(ant, nuevo, quien, com, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 20),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: En elaboración.', 19.8),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: Constancia lista. Recógela en ventanilla de coordinación.', 19)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'PROCESANDO', 'Creo que no se sumó la práctica 3 en la unidad 2.', NULL, NOW() - INTERVAL '96 hours',
         NOW() - INTERVAL '72 hours', NULL, a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'RC' AND a.NUM_CONTROL = '00000001'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 4), ('ENVIADA', 'PROCESANDO', 'c', 'Se pidió el acta al docente.', 3)) v(ant, nuevo, quien, com, dias)),
r AS (
  INSERT INTO RESPUESTA_CAMPO (ID_SOLICITUD, ID_CAMPO, VALOR)
  SELECT s.ID_SOLICITUD, c.ID_CAMPO, v.valor
  FROM s, (VALUES ('Materia', 'Fundamentos de Bases de Datos'), ('Unidad', '2'), ('Motivo de la revisión', 'No se consideró la práctica 3 entregada en tiempo.')) v(etiqueta, valor)
  JOIN CAMPO_FORMULARIO c ON c.ETIQUETA = v.etiqueta
  WHERE c.ID_TRAMITE = s.ID_TRAMITE AND c.ACTIVO),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 4),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Se pidió el acta al docente.', 3)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, NULL,
         'ENVIADA', 'Mi calificación final de Simulación aparece como NA.', NULL, NOW() - INTERVAL '288 hours',
         NOW() - INTERVAL '288 hours', NULL, a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'RC' AND a.NUM_CONTROL = '00000002'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 12)) v(ant, nuevo, quien, com, dias)),
r AS (
  INSERT INTO RESPUESTA_CAMPO (ID_SOLICITUD, ID_CAMPO, VALOR)
  SELECT s.ID_SOLICITUD, c.ID_CAMPO, v.valor
  FROM s, (VALUES ('Materia', 'Simulación'), ('Unidad', '4'), ('Motivo de la revisión', 'El docente confirmó que aprobé, pero en el SII aparece NA.')) v(etiqueta, valor)
  JOIN CAMPO_FORMULARIO c ON c.ETIQUETA = v.etiqueta
  WHERE c.ID_TRAMITE = s.ID_TRAMITE AND c.ACTIVO),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 12),
          ('c', 'Nueva solicitud ' || s.FOLIO, 'Hay una solicitud nueva por atender.', 12)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'RECHAZADA', 'Constancia con promedio para servicio social.', 'Tienes un adeudo en biblioteca. Libéralo y vuelve a solicitarla.', NOW() - INTERVAL '1440 hours',
         NOW() - INTERVAL '1392 hours', NOW() - INTERVAL '1392 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'CE' AND a.NUM_CONTROL = '00000002'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 60), ('ENVIADA', 'PROCESANDO', 'c', 'Verificando adeudos.', 59), ('PROCESANDO', 'RECHAZADA', 'c', 'Tienes un adeudo en biblioteca. Libéralo y vuelve a solicitarla.', 58)) v(ant, nuevo, quien, com, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 60),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Verificando adeudos.', 59),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está rechazada', 'Resolución: Tienes un adeudo en biblioteca. Libéralo y vuelve a solicitarla.', 58)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'ESPERA', 'Quiero dar de baja una materia porque se empalma con mi residencia.', NULL, NOW() - INTERVAL '144 hours',
         NOW() - INTERVAL '120 hours', NULL, a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'BM' AND a.NUM_CONTROL = '99160101'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 6), ('ENVIADA', 'PROCESANDO', 'c', 'Revisando.', 5), ('PROCESANDO', 'ESPERA', 'c', 'Se pidió información: ¿En qué grupo y horario estás inscrita en Simulación?', 5)) v(ant, nuevo, quien, com, dias)),
m AS (
  INSERT INTO MENSAJE_SOLICITUD (ID_MENSAJE, ID_SOLICITUD, ID_AUTOR, TIPO, CONTENIDO, ID_RESPONDE_A, FECHA)
  SELECT v.id, s.ID_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.tipo::TIPO_MENSAJE, v.texto, v.responde, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES ('6e780cba-6338-471f-86f7-02b748b615f8'::UUID, 'SOLICITUD_INFORMACION', 'c', '¿En qué grupo y horario estás inscrita en Simulación?', NULL::UUID, 5)) v(id, tipo, quien, texto, responde, dias)),
r AS (
  INSERT INTO RESPUESTA_CAMPO (ID_SOLICITUD, ID_CAMPO, VALOR)
  SELECT s.ID_SOLICITUD, c.ID_CAMPO, v.valor
  FROM s, (VALUES ('Materia', 'Simulación'), ('Motivo', 'Otro'), ('Créditos', '5')) v(etiqueta, valor)
  JOIN CAMPO_FORMULARIO c ON c.ETIQUETA = v.etiqueta
  WHERE c.ID_TRAMITE = s.ID_TRAMITE AND c.ACTIVO),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 6),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Revisando.', 5),
          ('a', 'Necesitamos información para ' || s.FOLIO, '¿En qué grupo y horario estás inscrita en Simulación?', 5)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, NULL,
         'ENVIADA', 'Constancia de inscripción para el IMSS.', NULL, NOW() - INTERVAL '48 hours',
         NOW() - INTERVAL '48 hours', NULL, a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'CE' AND a.NUM_CONTROL = '99160101'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 2)) v(ant, nuevo, quien, com, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 2),
          ('c', 'Nueva solicitud ' || s.FOLIO, 'Hay una solicitud nueva por atender.', 2)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'PROCESANDO', 'Revisión de la calificación de la unidad 3.', NULL, NOW() - INTERVAL '216 hours',
         NOW() - INTERVAL '120 hours', NULL, a.ID_CARRERA, 'CRITICA'::PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'RC' AND a.NUM_CONTROL = '99160102'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 9), ('ENVIADA', 'PROCESANDO', 'c', 'Revisando.', 8), ('PROCESANDO', 'ESPERA', 'c', 'Se pidió información: Adjunta la rúbrica o el correo del docente con tu calificación.', 7), ('ESPERA', 'PROCESANDO', 'c', 'El alumno respondió.', 6)) v(ant, nuevo, quien, com, dias)),
m AS (
  INSERT INTO MENSAJE_SOLICITUD (ID_MENSAJE, ID_SOLICITUD, ID_AUTOR, TIPO, CONTENIDO, ID_RESPONDE_A, FECHA)
  SELECT v.id, s.ID_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.tipo::TIPO_MENSAJE, v.texto, v.responde, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES ('1d1799c5-a2ee-43ef-bd64-4e154febf664'::UUID, 'SOLICITUD_INFORMACION', 'c', 'Adjunta la rúbrica o el correo del docente con tu calificación.', NULL::UUID, 7), ('e4d6eb0a-3bc1-4d78-88a6-37ee72df626b'::UUID, 'RESPUESTA', 'a', 'El docente dice que la calificación correcta es 85; ya le escribí para que lo confirme.', '1d1799c5-a2ee-43ef-bd64-4e154febf664'::UUID, 6)) v(id, tipo, quien, texto, responde, dias)),
r AS (
  INSERT INTO RESPUESTA_CAMPO (ID_SOLICITUD, ID_CAMPO, VALOR)
  SELECT s.ID_SOLICITUD, c.ID_CAMPO, v.valor
  FROM s, (VALUES ('Materia', 'Tópicos Avanzados de Programación'), ('Unidad', '3'), ('Motivo de la revisión', 'El proyecto final no se calificó.')) v(etiqueta, valor)
  JOIN CAMPO_FORMULARIO c ON c.ETIQUETA = v.etiqueta
  WHERE c.ID_TRAMITE = s.ID_TRAMITE AND c.ACTIVO),
p AS (
  INSERT INTO CAMBIO_PRIORIDAD (ID_SOLICITUD, PRIORIDAD_ANTERIOR, PRIORIDAD_NUEVA, MOTIVO, ID_ACTOR, FECHA)
  SELECT s.ID_SOLICITUD, t.PRIORIDAD, 'CRITICA', 'El cierre de actas es este viernes.', (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         NOW() - INTERVAL '120 hours'
  FROM s JOIN TRAMITE t ON t.ID_TRAMITE = s.ID_TRAMITE),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 9),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Revisando.', 8),
          ('a', 'Necesitamos información para ' || s.FOLIO, 'Adjunta la rúbrica o el correo del docente con tu calificación.', 7),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: El alumno respondió.', 6),
          ('c', 'Respuesta en ' || s.FOLIO, 'El alumno respondió tu petición de información.', 6)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'FINALIZADA', 'Baja por motivos de salud.', 'Baja aplicada. Recuerda que no podrás recursar en verano.', NOW() - INTERVAL '1920 hours',
         NOW() - INTERVAL '1680 hours', NOW() - INTERVAL '1680 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'BM' AND a.NUM_CONTROL = '99160102'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 80), ('ENVIADA', 'PROCESANDO', 'c', 'Revisando.', 78), ('PROCESANDO', 'FINALIZADA', 'c', 'Baja aplicada. Recuerda que no podrás recursar en verano.', 70)) v(ant, nuevo, quien, com, dias)),
r AS (
  INSERT INTO RESPUESTA_CAMPO (ID_SOLICITUD, ID_CAMPO, VALOR)
  SELECT s.ID_SOLICITUD, c.ID_CAMPO, v.valor
  FROM s, (VALUES ('Materia', 'Arquitectura de Computadoras'), ('Motivo', 'Salud'), ('Créditos', '5')) v(etiqueta, valor)
  JOIN CAMPO_FORMULARIO c ON c.ETIQUETA = v.etiqueta
  WHERE c.ID_TRAMITE = s.ID_TRAMITE AND c.ACTIVO),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 80),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Revisando.', 78),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: Baja aplicada. Recuerda que no podrás recursar en verano.', 70)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'FINALIZADA', 'Necesito cambiar al grupo vespertino de Cálculo Vectorial.', 'Cambio realizado al grupo 3B (vespertino).', NOW() - INTERVAL '600 hours',
         NOW() - INTERVAL '360 hours', NOW() - INTERVAL '360 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'CH' AND a.NUM_CONTROL = '99160103'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 25), ('ENVIADA', 'PROCESANDO', 'c', 'Buscando lugar en el grupo B.', 22), ('PROCESANDO', 'FINALIZADA', 'c', 'Cambio realizado al grupo 3B (vespertino).', 15)) v(ant, nuevo, quien, com, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 25),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Buscando lugar en el grupo B.', 22),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: Cambio realizado al grupo 3B (vespertino).', 15)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, NULL,
         'ENVIADA', 'Constancia de estudios simple.', NULL, NOW() - INTERVAL '24 hours',
         NOW() - INTERVAL '24 hours', NULL, a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'CE' AND a.NUM_CONTROL = '99160103'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 1)) v(ant, nuevo, quien, com, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 1),
          ('c', 'Nueva solicitud ' || s.FOLIO, 'Hay una solicitud nueva por atender.', 1)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'FINALIZADA', 'Baja temporal por motivos familiares.', 'Baja temporal autorizada por un semestre.', NOW() - INTERVAL '2280 hours',
         NOW() - INTERVAL '2040 hours', NOW() - INTERVAL '2040 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'BT' AND a.NUM_CONTROL = '99160104'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 95), ('ENVIADA', 'PROCESANDO', 'c', 'Revisando documentos.', 93), ('PROCESANDO', 'FINALIZADA', 'c', 'Baja temporal autorizada por un semestre.', 85)) v(ant, nuevo, quien, com, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 95),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Revisando documentos.', 93),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: Baja temporal autorizada por un semestre.', 85)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'FINALIZADA', 'Revisión de calificación de Ecuaciones Diferenciales.', 'El docente corrigió la calificación a 78.', NOW() - INTERVAL '720 hours',
         NOW() - INTERVAL '648 hours', NOW() - INTERVAL '648 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'RC' AND a.NUM_CONTROL = '99160104'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 30), ('ENVIADA', 'PROCESANDO', 'c', 'Consultando al docente.', 29), ('PROCESANDO', 'FINALIZADA', 'c', 'El docente corrigió la calificación a 78.', 27)) v(ant, nuevo, quien, com, dias)),
r AS (
  INSERT INTO RESPUESTA_CAMPO (ID_SOLICITUD, ID_CAMPO, VALOR)
  SELECT s.ID_SOLICITUD, c.ID_CAMPO, v.valor
  FROM s, (VALUES ('Materia', 'Ecuaciones Diferenciales'), ('Unidad', '1'), ('Motivo de la revisión', 'Hubo un error de captura.')) v(etiqueta, valor)
  JOIN CAMPO_FORMULARIO c ON c.ETIQUETA = v.etiqueta
  WHERE c.ID_TRAMITE = s.ID_TRAMITE AND c.ACTIVO),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 30),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Consultando al docente.', 29),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: El docente corrigió la calificación a 78.', 27)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'PROCESANDO', 'Cambio de grupo de Inteligencia Artificial por empalme con residencia.', NULL, NOW() - INTERVAL '72 hours',
         NOW() - INTERVAL '48 hours', NULL, a.ID_CARRERA, t.PRIORIDAD,
         NULL, TRUE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'CH' AND a.NUM_CONTROL = '99160105'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 3), ('ENVIADA', 'PROCESANDO', 'c', 'Necesitamos verte para revisar tu horario.', 2)) v(ant, nuevo, quien, com, dias)),
m AS (
  INSERT INTO MENSAJE_SOLICITUD (ID_MENSAJE, ID_SOLICITUD, ID_AUTOR, TIPO, CONTENIDO, ID_RESPONDE_A, FECHA)
  SELECT v.id, s.ID_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.tipo::TIPO_MENSAJE, v.texto, v.responde, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES ('34c7e651-27eb-4d70-a6aa-bb639e8cd65d'::UUID, 'NOTA', 'c', 'Trae tu horario de residencia firmado por tu asesor externo.', NULL::UUID, 2)) v(id, tipo, quien, texto, responde, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 3),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Necesitamos verte para revisar tu horario.', 2),
          ('a', 'Agenda una cita para ' || s.FOLIO, 'Trae tu horario de residencia firmado por tu asesor externo. Elige un horario desde el detalle de tu solicitud.', 2)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'FINALIZADA', 'Constancia para residencia profesional.', 'Constancia enviada a tu correo institucional.', NOW() - INTERVAL '1080 hours',
         NOW() - INTERVAL '1032 hours', NOW() - INTERVAL '1032 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'CE' AND a.NUM_CONTROL = '99160105'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 45), ('ENVIADA', 'PROCESANDO', 'c', 'En elaboración.', 44), ('PROCESANDO', 'FINALIZADA', 'c', 'Constancia enviada a tu correo institucional.', 43)) v(ant, nuevo, quien, com, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 45),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: En elaboración.', 44),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: Constancia enviada a tu correo institucional.', 43)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, NULL,
         'ENVIADA', 'Baja de Lenguajes y Autómatas II.', NULL, NOW() - INTERVAL '360 hours',
         NOW() - INTERVAL '360 hours', NULL, a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'BM' AND a.NUM_CONTROL = '99160106'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 15)) v(ant, nuevo, quien, com, dias)),
r AS (
  INSERT INTO RESPUESTA_CAMPO (ID_SOLICITUD, ID_CAMPO, VALOR)
  SELECT s.ID_SOLICITUD, c.ID_CAMPO, v.valor
  FROM s, (VALUES ('Materia', 'Lenguajes y Autómatas II'), ('Motivo', 'Trabajo'), ('Créditos', '5')) v(etiqueta, valor)
  JOIN CAMPO_FORMULARIO c ON c.ETIQUETA = v.etiqueta
  WHERE c.ID_TRAMITE = s.ID_TRAMITE AND c.ACTIVO),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 15),
          ('c', 'Nueva solicitud ' || s.FOLIO, 'Hay una solicitud nueva por atender.', 15)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, NULL,
         'ENVIADA', 'Constancia para inscribirme al curso de inglés.', NULL, NOW() - INTERVAL '120 hours',
         NOW() - INTERVAL '120 hours', NULL, a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'CE' AND a.NUM_CONTROL = '99160150'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 5)) v(ant, nuevo, quien, com, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 5),
          ('c', 'Nueva solicitud ' || s.FOLIO, 'Hay una solicitud nueva por atender.', 5)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'FINALIZADA', 'Cambio de grupo de Redes de Computadoras.', 'Cambio realizado al grupo 6A.', NOW() - INTERVAL '4080 hours',
         NOW() - INTERVAL '3960 hours', NOW() - INTERVAL '3960 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'CH' AND a.NUM_CONTROL = '00000001'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 170), ('ENVIADA', 'PROCESANDO', 'c', 'Revisando.', 168), ('PROCESANDO', 'FINALIZADA', 'c', 'Cambio realizado al grupo 6A.', 165)) v(ant, nuevo, quien, com, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 170),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Revisando.', 168),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: Cambio realizado al grupo 6A.', 165)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'FINALIZADA', 'Baja de Probabilidad y Estadística.', 'Baja aplicada.', NOW() - INTERVAL '3600 hours',
         NOW() - INTERVAL '3528 hours', NOW() - INTERVAL '3528 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'BM' AND a.NUM_CONTROL = '00000002'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 150), ('ENVIADA', 'PROCESANDO', 'c', 'Revisando.', 149), ('PROCESANDO', 'FINALIZADA', 'c', 'Baja aplicada.', 147)) v(ant, nuevo, quien, com, dias)),
r AS (
  INSERT INTO RESPUESTA_CAMPO (ID_SOLICITUD, ID_CAMPO, VALOR)
  SELECT s.ID_SOLICITUD, c.ID_CAMPO, v.valor
  FROM s, (VALUES ('Materia', 'Probabilidad y Estadística'), ('Motivo', 'Otro'), ('Créditos', '5')) v(etiqueta, valor)
  JOIN CAMPO_FORMULARIO c ON c.ETIQUETA = v.etiqueta
  WHERE c.ID_TRAMITE = s.ID_TRAMITE AND c.ACTIVO),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 150),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Revisando.', 149),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: Baja aplicada.', 147)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'FINALIZADA', 'Constancia para beca.', 'Constancia lista.', NOW() - INTERVAL '4800 hours',
         NOW() - INTERVAL '4752 hours', NOW() - INTERVAL '4752 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'CE' AND a.NUM_CONTROL = '99160104'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 200), ('ENVIADA', 'PROCESANDO', 'c', 'En elaboración.', 199.5), ('PROCESANDO', 'FINALIZADA', 'c', 'Constancia lista.', 198)) v(ant, nuevo, quien, com, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 200),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: En elaboración.', 199.5),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: Constancia lista.', 198)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ic@itoaxaca.edu.mx'),
         'FINALIZADA', 'Baja de Topografía por cambio de turno laboral.', 'Baja aplicada.', NOW() - INTERVAL '240 hours',
         NOW() - INTERVAL '192 hours', NOW() - INTERVAL '192 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'BM' AND a.NUM_CONTROL = '99160201'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ic@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 10), ('ENVIADA', 'PROCESANDO', 'c', 'Revisando.', 9), ('PROCESANDO', 'FINALIZADA', 'c', 'Baja aplicada.', 8)) v(ant, nuevo, quien, com, dias)),
r AS (
  INSERT INTO RESPUESTA_CAMPO (ID_SOLICITUD, ID_CAMPO, VALOR)
  SELECT s.ID_SOLICITUD, c.ID_CAMPO, v.valor
  FROM s, (VALUES ('Materia', 'Topografía'), ('Motivo', 'Trabajo'), ('Créditos', '5')) v(etiqueta, valor)
  JOIN CAMPO_FORMULARIO c ON c.ETIQUETA = v.etiqueta
  WHERE c.ID_TRAMITE = s.ID_TRAMITE AND c.ACTIVO),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 10),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Revisando.', 9),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: Baja aplicada.', 8)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.ic@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, NULL,
         'ENVIADA', 'Constancia de estudios para el banco.', NULL, NOW() - INTERVAL '72 hours',
         NOW() - INTERVAL '72 hours', NULL, a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'CE' AND a.NUM_CONTROL = '99160201'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ic@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 3)) v(ant, nuevo, quien, com, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 3),
          ('c', 'Nueva solicitud ' || s.FOLIO, 'Hay una solicitud nueva por atender.', 3)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.ic@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ic@itoaxaca.edu.mx'),
         'PROCESANDO', 'Cambio de grupo de Hidrología.', NULL, NOW() - INTERVAL '192 hours',
         NOW() - INTERVAL '168 hours', NULL, a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'CH' AND a.NUM_CONTROL = '99160202'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ic@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 8), ('ENVIADA', 'PROCESANDO', 'c', 'Esperando cupo en el grupo A.', 7)) v(ant, nuevo, quien, com, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 8),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Esperando cupo en el grupo A.', 7)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.ic@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ic@itoaxaca.edu.mx'),
         'RECHAZADA', 'Revisión de calificación de Estructuras.', 'El periodo de revisión de esa unidad ya concluyó.', NOW() - INTERVAL '1200 hours',
         NOW() - INTERVAL '1128 hours', NOW() - INTERVAL '1128 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'RC' AND a.NUM_CONTROL = '99160202'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ic@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 50), ('ENVIADA', 'PROCESANDO', 'c', 'Revisando.', 49), ('PROCESANDO', 'RECHAZADA', 'c', 'El periodo de revisión de esa unidad ya concluyó.', 47)) v(ant, nuevo, quien, com, dias)),
r AS (
  INSERT INTO RESPUESTA_CAMPO (ID_SOLICITUD, ID_CAMPO, VALOR)
  SELECT s.ID_SOLICITUD, c.ID_CAMPO, v.valor
  FROM s, (VALUES ('Materia', 'Análisis Estructural'), ('Unidad', '2'), ('Motivo de la revisión', 'Calificación incompleta.')) v(etiqueta, valor)
  JOIN CAMPO_FORMULARIO c ON c.ETIQUETA = v.etiqueta
  WHERE c.ID_TRAMITE = s.ID_TRAMITE AND c.ACTIVO),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 50),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Revisando.', 49),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está rechazada', 'Resolución: El periodo de revisión de esa unidad ya concluyó.', 47)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.ic@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ic@itoaxaca.edu.mx'),
         'FINALIZADA', 'Constancia para transporte.', 'Constancia lista.', NOW() - INTERVAL '336 hours',
         NOW() - INTERVAL '288 hours', NOW() - INTERVAL '288 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'CE' AND a.NUM_CONTROL = '99160203'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ic@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 14), ('ENVIADA', 'PROCESANDO', 'c', 'En elaboración.', 13), ('PROCESANDO', 'FINALIZADA', 'c', 'Constancia lista.', 12)) v(ant, nuevo, quien, com, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 14),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: En elaboración.', 13),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: Constancia lista.', 12)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.ic@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, NULL,
         'ENVIADA', 'Cambio de grupo de Ergonomía.', NULL, NOW() - INTERVAL '96 hours',
         NOW() - INTERVAL '96 hours', NULL, a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'CH' AND a.NUM_CONTROL = '99160401'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ic@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 4)) v(ant, nuevo, quien, com, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 4),
          ('c', 'Nueva solicitud ' || s.FOLIO, 'Hay una solicitud nueva por atender.', 4)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.ic@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ic@itoaxaca.edu.mx'),
         'FINALIZADA', 'Baja de Estadística Inferencial II.', 'Baja aplicada.', NOW() - INTERVAL '840 hours',
         NOW() - INTERVAL '744 hours', NOW() - INTERVAL '744 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'BM' AND a.NUM_CONTROL = '99160401'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ic@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 35), ('ENVIADA', 'PROCESANDO', 'c', 'Revisando.', 34), ('PROCESANDO', 'FINALIZADA', 'c', 'Baja aplicada.', 31)) v(ant, nuevo, quien, com, dias)),
r AS (
  INSERT INTO RESPUESTA_CAMPO (ID_SOLICITUD, ID_CAMPO, VALOR)
  SELECT s.ID_SOLICITUD, c.ID_CAMPO, v.valor
  FROM s, (VALUES ('Materia', 'Estadística Inferencial II'), ('Motivo', 'Salud'), ('Créditos', '5')) v(etiqueta, valor)
  JOIN CAMPO_FORMULARIO c ON c.ETIQUETA = v.etiqueta
  WHERE c.ID_TRAMITE = s.ID_TRAMITE AND c.ACTIVO),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 35),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Revisando.', 34),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: Baja aplicada.', 31)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.ic@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ige@itoaxaca.edu.mx'),
         'FINALIZADA', 'Constancia para prácticas profesionales.', 'Constancia lista.', NOW() - INTERVAL '168 hours',
         NOW() - INTERVAL '144 hours', NOW() - INTERVAL '144 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'CE' AND a.NUM_CONTROL = '99160301'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ige@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 7), ('ENVIADA', 'PROCESANDO', 'c', 'En elaboración.', 6.5), ('PROCESANDO', 'FINALIZADA', 'c', 'Constancia lista.', 6)) v(ant, nuevo, quien, com, dias)),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 7),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: En elaboración.', 6.5),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: Constancia lista.', 6)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.ige@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ige@itoaxaca.edu.mx'),
         'PROCESANDO', 'Revisión de Contabilidad Orientada a Negocios.', NULL, NOW() - INTERVAL '48 hours',
         NOW() - INTERVAL '24 hours', NULL, a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'RC' AND a.NUM_CONTROL = '99160302'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ige@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 2), ('ENVIADA', 'PROCESANDO', 'c', 'Consultando al docente.', 1)) v(ant, nuevo, quien, com, dias)),
r AS (
  INSERT INTO RESPUESTA_CAMPO (ID_SOLICITUD, ID_CAMPO, VALOR)
  SELECT s.ID_SOLICITUD, c.ID_CAMPO, v.valor
  FROM s, (VALUES ('Materia', 'Contabilidad Orientada a Negocios'), ('Unidad', '3'), ('Motivo de la revisión', 'No aparece mi examen de la unidad 3.')) v(etiqueta, valor)
  JOIN CAMPO_FORMULARIO c ON c.ETIQUETA = v.etiqueta
  WHERE c.ID_TRAMITE = s.ID_TRAMITE AND c.ACTIVO),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 2),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Consultando al docente.', 1)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.ige@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ige@itoaxaca.edu.mx'),
         'FINALIZADA', 'Baja de Mercadotecnia.', 'Baja aplicada.', NOW() - INTERVAL '1560 hours',
         NOW() - INTERVAL '1320 hours', NOW() - INTERVAL '1320 hours', a.ID_CARRERA, t.PRIORIDAD,
         NULL, FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'BM' AND a.NUM_CONTROL = '99160301'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ige@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 65), ('ENVIADA', 'PROCESANDO', 'c', 'Revisando.', 60), ('PROCESANDO', 'FINALIZADA', 'c', 'Baja aplicada.', 55)) v(ant, nuevo, quien, com, dias)),
r AS (
  INSERT INTO RESPUESTA_CAMPO (ID_SOLICITUD, ID_CAMPO, VALOR)
  SELECT s.ID_SOLICITUD, c.ID_CAMPO, v.valor
  FROM s, (VALUES ('Materia', 'Mercadotecnia'), ('Motivo', 'Otro'), ('Créditos', '5')) v(etiqueta, valor)
  JOIN CAMPO_FORMULARIO c ON c.ETIQUETA = v.etiqueta
  WHERE c.ID_TRAMITE = s.ID_TRAMITE AND c.ACTIVO),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 65),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Revisando.', 60),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: Baja aplicada.', 55)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN (NULL)))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.ige@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'PROCESANDO', 'Somos varios que debemos Simulación y no hay grupo vespertino.', NULL, NOW() - INTERVAL '432 hours',
         NOW() - INTERVAL '240 hours', NULL, a.ID_CARRERA, t.PRIORIDAD,
         (SELECT ID_MATERIA FROM MATERIA WHERE CLAVE = 'SCD-1022'), FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'AG' AND a.NUM_CONTROL = '00000001'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 18), ('ENVIADA', 'PROCESANDO', 'c', 'Consultando disponibilidad de docente.', 10)) v(ant, nuevo, quien, com, dias)),
ad AS (
  INSERT INTO ADHESION (ID_SOLICITUD, ID_ALUMNO, FECHA)
  SELECT s.ID_SOLICITUD, u.ID_USUARIO, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES ('00000002', 17), ('99160101', 16), ('99160102', 16), ('99160104', 14), ('99160105', 12), ('99160106', 11), ('99160150', 9)) v(nc, dias) JOIN USUARIO u ON u.NUM_CONTROL = v.nc),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 18),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Consultando disponibilidad de docente.', 10)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN ('00000002', '99160101', '99160102', '99160104', '99160105', '99160106', '99160150')))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, NULL,
         'ENVIADA', 'Necesitamos Taller de Investigación I para poder titularnos a tiempo.', NULL, NOW() - INTERVAL '216 hours',
         NOW() - INTERVAL '216 hours', NULL, a.ID_CARRERA, t.PRIORIDAD,
         (SELECT ID_MATERIA FROM MATERIA WHERE CLAVE = 'ACA-0909'), FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'AG' AND a.NUM_CONTROL = '99160101'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 9)) v(ant, nuevo, quien, com, dias)),
ad AS (
  INSERT INTO ADHESION (ID_SOLICITUD, ID_ALUMNO, FECHA)
  SELECT s.ID_SOLICITUD, u.ID_USUARIO, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES ('99160102', 8), ('99160106', 7), ('00000002', 6), ('99160103', 3)) v(nc, dias) JOIN USUARIO u ON u.NUM_CONTROL = v.nc),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 9),
          ('c', 'Nueva solicitud ' || s.FOLIO, 'Hay una solicitud nueva por atender.', 9)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN ('99160102', '99160106', '00000002', '99160103')))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, NULL,
         'ENVIADA', 'Grupo de Hidráulica Básica en turno vespertino.', NULL, NOW() - INTERVAL '144 hours',
         NOW() - INTERVAL '144 hours', NULL, a.ID_CARRERA, t.PRIORIDAD,
         (SELECT ID_MATERIA FROM MATERIA WHERE CLAVE = 'ICF-1020'), FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'AG' AND a.NUM_CONTROL = '99160201'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.ic@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 6)) v(ant, nuevo, quien, com, dias)),
ad AS (
  INSERT INTO ADHESION (ID_SOLICITUD, ID_ALUMNO, FECHA)
  SELECT s.ID_SOLICITUD, u.ID_USUARIO, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES ('99160203', 5)) v(nc, dias) JOIN USUARIO u ON u.NUM_CONTROL = v.nc),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 6),
          ('c', 'Nueva solicitud ' || s.FOLIO, 'Hay una solicitud nueva por atender.', 6)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN ('99160203')))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.ic@itoaxaca.edu.mx'))
SELECT 1;

WITH s AS (
  INSERT INTO SOLICITUD (ID_TRAMITE, ID_SOLICITANTE, ID_ASIGNADO, ESTADO, PETICION, RESOLUCION, FECHA_REGISTRO,
                         FECHA_ACTUALIZACION, FECHA_CIERRE, ID_CARRERA, PRIORIDAD, ID_MATERIA, CITA_REQUERIDA)
  SELECT t.ID_TRAMITE, a.ID_USUARIO, (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx'),
         'FINALIZADA', 'Grupo de Arquitectura de Computadoras para recursadores.', 'Se abre el grupo 5R, lunes a viernes de 13:00 a 14:00, aula K-4.', NOW() - INTERVAL '1680 hours',
         NOW() - INTERVAL '1440 hours', NOW() - INTERVAL '1440 hours', a.ID_CARRERA, t.PRIORIDAD,
         (SELECT ID_MATERIA FROM MATERIA WHERE CLAVE = 'SCD-1003'), FALSE
  FROM TRAMITE t, USUARIO a WHERE t.CLAVE = 'AG' AND a.NUM_CONTROL = '99160104'
  RETURNING ID_SOLICITUD, ID_TRAMITE, ID_SOLICITANTE, 'ITO-' || LPAD(FOLIO::TEXT, 4, '0') AS FOLIO),
h AS (
  INSERT INTO HISTORIAL_SOLICITUD (ID_SOLICITUD, ESTADO_ANTERIOR, ESTADO_NUEVO, ID_ACTOR, COMENTARIO, FECHA)
  SELECT s.ID_SOLICITUD, v.ant::ESTADO_SOLICITUD, v.nuevo::ESTADO_SOLICITUD,
         CASE v.quien WHEN 'a' THEN s.ID_SOLICITANTE ELSE (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx') END,
         v.com, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES (NULL, 'ENVIADA', 'a', 'Solicitud registrada', 70), ('ENVIADA', 'PROCESANDO', 'c', 'Reuniendo interesados.', 66), ('PROCESANDO', 'FINALIZADA', 'c', 'Se abre el grupo 5R, lunes a viernes de 13:00 a 14:00, aula K-4.', 60)) v(ant, nuevo, quien, com, dias)),
ad AS (
  INSERT INTO ADHESION (ID_SOLICITUD, ID_ALUMNO, FECHA)
  SELECT s.ID_SOLICITUD, u.ID_USUARIO, NOW() - make_interval(hours => (v.dias * 24)::int)
  FROM s, (VALUES ('99160105', 69), ('99160102', 68), ('99160106', 68), ('00000002', 67), ('00000001', 67)) v(nc, dias) JOIN USUARIO u ON u.NUM_CONTROL = v.nc),
n AS (
  INSERT INTO NOTIFICACION (ID_DESTINATARIO, ID_SOLICITUD, TITULO, MENSAJE, FECHA, CORREO_ENVIADO_EN, LEIDA_EN)
  SELECT u.ID_USUARIO, s.ID_SOLICITUD, v.titulo, v.mensaje, NOW() - make_interval(hours => (v.dias * 24)::int),
         NOW() - make_interval(hours => (v.dias * 24)::int),
         CASE WHEN v.dias > 2 THEN NOW() - make_interval(hours => (v.dias * 24)::int - 1) END
  FROM s CROSS JOIN LATERAL (VALUES
          ('a', 'Recibimos tu solicitud ' || s.FOLIO, 'Tu solicitud quedó registrada. Te avisaremos de cada cambio.', 70),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está en revisión', 'Comentario de coordinación: Reuniendo interesados.', 66),
          ('t', 'Tu solicitud ' || s.FOLIO || ' está finalizada', 'Resolución: Se abre el grupo 5R, lunes a viernes de 13:00 a 14:00, aula K-4.', 60)
       ) v(para, titulo, mensaje, dias)
  JOIN USUARIO u ON (v.para = 'a' AND u.ID_USUARIO = s.ID_SOLICITANTE)
                 OR (v.para = 't' AND (u.ID_USUARIO = s.ID_SOLICITANTE OR u.NUM_CONTROL IN ('99160105', '99160102', '99160106', '00000002', '00000001')))
                 OR (v.para = 'c' AND u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'))
SELECT 1;

-- ---------------------------------------------------------------------
-- Avisos oficiales (HU 5.3)
-- ---------------------------------------------------------------------
WITH a AS (
  INSERT INTO AVISO (ID_AUTOR, TITULO, CONTENIDO, INICIO_VIGENCIA, FIN_VIGENCIA, FECHA_PUBLICACION)
  SELECT u.ID_USUARIO, x.titulo, x.contenido, CURRENT_DATE + x.ini, CURRENT_DATE + x.fin, NOW() - make_interval(days => GREATEST(-x.ini, 0) + 1)
  FROM (VALUES
    ('coordinacion.isc', 'Último día de bajas parciales', 'Las bajas de materia se reciben hasta el viernes 27 por este sistema. Después de esa fecha ya no se aplican en el SII.', -3, 12, 'ISC'),
    ('coordinacion.isc', 'Entrega de constancias', 'Las constancias solicitadas en septiembre ya se pueden recoger en ventanilla.', -40, -20, 'ISC'),
    ('coordinacion.ic',  'Asesorías de Mecánica de Suelos', 'Habrá asesorías los martes y jueves de 16:00 a 18:00 en el laboratorio de suelos.', -2, 20, 'IC'),
    ('jefatura',         'Reinscripciones enero 2027', 'Las reinscripciones del semestre Ene–Jun 2027 serán del 11 al 15 de enero. Revisa que no tengas adeudos.', 20, 50, '*')
  ) x(cuenta, titulo, contenido, ini, fin, carreras)
  JOIN USUARIO u ON u.CORREO = x.cuenta || '@itoaxaca.edu.mx'
  RETURNING ID_AVISO, TITULO)
INSERT INTO AVISO_CARRERA (ID_AVISO, ID_CARRERA)
SELECT a.ID_AVISO, c.ID_CARRERA FROM a JOIN CARRERA c ON
   (a.TITULO = 'Reinscripciones enero 2027')
OR (a.TITULO IN ('Último día de bajas parciales', 'Entrega de constancias') AND c.CLAVE = 'ISC')
OR (a.TITULO = 'Asesorías de Mecánica de Suelos' AND c.CLAVE IN ('IC'));

-- ---------------------------------------------------------------------
-- Agenda de atención (HU 9.1 – 9.3): próximos 3 días hábiles, 10:00 a 12:00, bloques de 20 min
-- ---------------------------------------------------------------------
INSERT INTO BLOQUE_ATENCION (ID_COORDINADOR, INICIO, FIN, LUGAR)
SELECT u.ID_USUARIO,
       ((d.dia + h.hora) AT TIME ZONE 'America/Mexico_City'),
       ((d.dia + h.hora + INTERVAL '20 minutes') AT TIME ZONE 'America/Mexico_City'),
       'Cubículo 3, edificio de coordinación'
FROM USUARIO u,
     (SELECT dia FROM generate_series(CURRENT_DATE + 1, CURRENT_DATE + 7, INTERVAL '1 day') dia
      WHERE EXTRACT(ISODOW FROM dia) < 6 ORDER BY dia LIMIT 3) d,
     (SELECT TIME '10:00' + n * INTERVAL '20 minutes' AS hora FROM generate_series(0, 5) n) h
WHERE u.CORREO = 'coordinacion.isc@itoaxaca.edu.mx'
ON CONFLICT DO NOTHING;

-- Valeria ya eligió el primer horario para su solicitud con cita.
INSERT INTO CITA (ID_BLOQUE, ID_SOLICITUD, ID_ALUMNO, FECHA_REGISTRO)
SELECT b.ID_BLOQUE, s.ID_SOLICITUD, s.ID_SOLICITANTE, NOW() - INTERVAL '1 day'
FROM SOLICITUD s
JOIN USUARIO a ON a.ID_USUARIO = s.ID_SOLICITANTE AND a.NUM_CONTROL = '99160105'
JOIN BLOQUE_ATENCION b ON b.ID_COORDINADOR = (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = 'coordinacion.isc@itoaxaca.edu.mx')
WHERE s.CITA_REQUERIDA AND NOT b.CANCELADO
ORDER BY b.INICIO
LIMIT 1;

-- ---------------------------------------------------------------------
-- Auditoría (HU 8.2)
-- ---------------------------------------------------------------------
INSERT INTO AUDITORIA (ID_ACTOR, ACCION, ENTIDAD, ID_ENTIDAD, DETALLE, IP, FECHA)
SELECT (SELECT ID_USUARIO FROM USUARIO WHERE CORREO = x.actor || '@itoaxaca.edu.mx'), x.accion, x.entidad,
       (SELECT ID_USUARIO::TEXT FROM USUARIO WHERE CORREO = x.sobre || '@itoaxaca.edu.mx'),
       x.detalle::JSONB, x.ip::INET, NOW() - make_interval(hours => x.horas)
FROM (VALUES
  ('jefatura', 'ALTA_CUENTA', 'USUARIO', 'coordinacion.ige', '{"correo":"coordinacion.ige@itoaxaca.edu.mx","rol":"COORDINADOR","carreras":"[IGE]"}', '10.20.30.5', 110*24),
  ('jefatura', 'CAMBIO_ROL', 'USUARIO', 'coordinacion.ic', '{"correo":"coordinacion.ic@itoaxaca.edu.mx","antes":"COORDINADOR [IC]","despues":"COORDINADOR [IC, II]"}', '10.20.30.5', 105*24),
  ('jefatura', 'CARGAR_PADRON', 'CARGA_PADRON', NULL, '{"archivo":"padron-demo-ago-dic-2026.csv","nuevos":"15","actualizados":"2","rechazados":"0","retirados":"0"}', '10.20.30.5', 100*24),
  ('jefatura', 'CONFIGURAR_TRAMITE', 'TRAMITE', NULL, '{"cambio":"alta","clave":"CE"}', '10.20.30.5', 98*24),
  ('jefatura', 'CONFIGURAR_TRAMITE', 'TRAMITE', NULL, '{"cambio":"alta","clave":"RC"}', '10.20.30.5', 98*24),
  ('99160150', 'PRIMER_ACCESO', 'USUARIO', '99160150', '{"origen":"AUTODECLARADO","carrera":"ISC"}', '189.203.14.22', 46*24),
  ('coordinacion.isc', 'CORREGIR_ALUMNO', 'USUARIO', '99160106', '{"numControl":"99160106","antes":"ISC, 5.º","despues":"ISC, 6.º"}', '10.20.30.17', 20*24),
  (NULL, 'ACCESO_FALLIDO', 'USUARIO', NULL, '{"motivo":"Solo se admiten cuentas institucionales @itoaxaca.edu.mx."}', '187.190.55.4', 9*24),
  ('jefatura', 'EXPORTAR_HISTORIAL', 'SOLICITUD', NULL, '{"desde":"2026-08-01","hasta":"2026-12-31","solicitudes":"30"}', '10.20.30.2', 2*24),
  ('coordinacion.isc', 'ACCESO', 'USUARIO', 'coordinacion.isc', NULL, '10.20.30.17', 3),
  ('coordinacion.ic', 'ACCESO', 'USUARIO', 'coordinacion.ic', NULL, '10.20.30.21', 24),
  ('jefatura', 'ACCESO', 'USUARIO', 'jefatura', NULL, '10.20.30.2', 48),
  ('00000001', 'ACCESO', 'USUARIO', '00000001', NULL, '189.203.14.80', 5),
  ('99160105', 'ACCESO', 'USUARIO', '99160105', NULL, '177.244.8.13', 26)
) x(actor, accion, entidad, sobre, detalle, ip, horas);

-- Resumen de lo cargado.
SELECT 'carreras' AS que, COUNT(*) FROM CARRERA
UNION ALL SELECT 'alumnos con cuenta', COUNT(*) FROM USUARIO WHERE ROL = 'ESTUDIANTE'
UNION ALL SELECT 'personal', COUNT(*) FROM USUARIO WHERE ROL <> 'ESTUDIANTE'
UNION ALL SELECT 'solicitudes', COUNT(*) FROM SOLICITUD
UNION ALL SELECT 'adhesiones', COUNT(*) FROM ADHESION
UNION ALL SELECT 'notificaciones', COUNT(*) FROM NOTIFICACION
UNION ALL SELECT 'bloques de atención', COUNT(*) FROM BLOQUE_ATENCION
UNION ALL SELECT 'auditoría', COUNT(*) FROM AUDITORIA;

COMMIT;
