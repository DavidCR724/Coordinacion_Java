-- CoordinadITO · Datos de PRUEBA (solo desarrollo). Ejecutar después de 01 a 06.
-- Personas y correos ficticios. No usar en una base con datos reales.
INSERT INTO CARRERA(CLAVE,NOMBRE) VALUES ('ISC','Ingeniería en Sistemas Computacionales'),('IC','Ingeniería Civil');

-- Personal (las cuentas sin rol de estudiante no tienen datos escolares).
INSERT INTO USUARIO(NOMBRES,APELLIDO_P,APELLIDO_M,CORREO,ROL) VALUES
 ('Elena','Ramírez','Ruiz','coordinacion.isc@itoaxaca.edu.mx','COORDINADOR'),
 ('Mario','Díaz',NULL,'coordinacion.ic@itoaxaca.edu.mx','COORDINADOR'),
 ('Jefa','Coordinación',NULL,'jefatura@itoaxaca.edu.mx','JEFE');
INSERT INTO COORDINACION_CARRERA SELECT u.ID_USUARIO, c.ID_CARRERA FROM USUARIO u, CARRERA c WHERE u.CORREO='coordinacion.isc@itoaxaca.edu.mx' AND c.CLAVE='ISC';
INSERT INTO COORDINACION_CARRERA SELECT u.ID_USUARIO, c.ID_CARRERA FROM USUARIO u, CARRERA c WHERE u.CORREO='coordinacion.ic@itoaxaca.edu.mx' AND c.CLAVE='IC';

-- Padrón institucional de ejemplo (HU 8.1): tres alumnos; el tercero aún no entra al sistema.
INSERT INTO CARGA_PADRON(ID_RESPONSABLE,NOMBRE_ARCHIVO,TOTAL_REGISTROS,NUEVOS)
 SELECT ID_USUARIO,'padron-ejemplo.csv',3,3 FROM USUARIO WHERE CORREO='jefatura@itoaxaca.edu.mx';
INSERT INTO PADRON_ALUMNO(NUM_CONTROL,CORREO,NOMBRES,APELLIDO_P,APELLIDO_M,ID_CARRERA,SEMESTRE,ID_CARGA)
 SELECT x.nc, x.correo, x.nom, x.ap, x.am, c.ID_CARRERA, x.sem, (SELECT ID_CARGA FROM CARGA_PADRON LIMIT 1)
 FROM (VALUES ('00000001','00000001@itoaxaca.edu.mx','Sofía','Hernández','López','ISC',7),
              ('00000002','00000002@itoaxaca.edu.mx','Luis','Pérez',NULL,'ISC',5),
              ('00000003','00000003@itoaxaca.edu.mx','Ana','García','Torres','IC',3)) x(nc,correo,nom,ap,am,carrera,sem)
 JOIN CARRERA c ON c.CLAVE = x.carrera;

-- Alumnos con cuenta (sus datos vienen del padrón).
INSERT INTO USUARIO(NOMBRES,APELLIDO_P,APELLIDO_M,CORREO,ROL,ID_CARRERA,NUM_CONTROL,SEMESTRE,ORIGEN_DATOS) VALUES
 ('Sofía','Hernández','López','00000001@itoaxaca.edu.mx','ESTUDIANTE',(SELECT ID_CARRERA FROM CARRERA WHERE CLAVE='ISC'),'00000001',7,'PADRON'),
 ('Luis','Pérez',NULL,'00000002@itoaxaca.edu.mx','ESTUDIANTE',(SELECT ID_CARRERA FROM CARRERA WHERE CLAVE='ISC'),'00000002',5,'PADRON');

-- Catálogo de trámites.
INSERT INTO TRAMITE(CLAVE,NOMBRE,PRIORIDAD,DESCRIPCION,REQUISITOS,DOC_REQ,ES_COLECTIVO,INICIO_PERIODO,FIN_PERIODO,DIAS_ESPERADOS) VALUES
 ('BM','Baja de materia','ALTA','Baja parcial de una materia',E'Identificar la materia y el grupo.\nIndicar el motivo de la baja.',FALSE,FALSE,CURRENT_DATE-30,CURRENT_DATE+30,3),
 ('CH','Cambio de horario','MEDIA','Cambio de grupo',E'Horario actual en PDF.',TRUE,FALSE,NULL,NULL,5),
 ('BT','Baja temporal','BAJA','Baja del semestre',NULL,FALSE,FALSE,CURRENT_DATE-90,CURRENT_DATE-10,10),
 ('AG','Apertura de grupo','MEDIA','Pide que se abra un grupo de una materia. Si alguien ya lo pidió, súmate a esa solicitud.',
  E'Elegir la materia de tu carrera.\nUna sola solicitud por materia: los demás se adhieren.',FALSE,TRUE,NULL,NULL,15);
INSERT INTO CAMPO_FORMULARIO(ID_TRAMITE,ETIQUETA,TIPO,OBLIGATORIO,ORDEN,LONGITUD_MAX,OPCIONES) VALUES
 ((SELECT ID_TRAMITE FROM TRAMITE WHERE CLAVE='BM'),'Materia','TEXTO',TRUE,1,100,NULL),
 ((SELECT ID_TRAMITE FROM TRAMITE WHERE CLAVE='BM'),'Motivo','SELECCION',TRUE,2,NULL,E'Salud\nTrabajo\nOtro'),
 ((SELECT ID_TRAMITE FROM TRAMITE WHERE CLAVE='BM'),'Créditos','NUMERO',FALSE,3,NULL,NULL);

-- Materias para la apertura de grupos (HU 4.x).
INSERT INTO MATERIA(CLAVE,NOMBRE,ID_CARRERA,SEMESTRE,CREDITOS)
 SELECT x.clave, x.nombre, c.ID_CARRERA, x.sem, x.cred
 FROM (VALUES ('SCD-1027','Tópicos Avanzados de Programación','ISC',5,5),
              ('SCC-1017','Métodos Numéricos','ISC',5,4),
              ('AEF-1031','Fundamentos de Bases de Datos','ISC',4,5),
              ('ICC-1012','Mecánica de Suelos','IC',5,4)) x(clave,nombre,carrera,sem,cred)
 JOIN CARRERA c ON c.CLAVE = x.carrera;
