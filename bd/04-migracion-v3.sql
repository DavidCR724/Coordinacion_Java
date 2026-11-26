-- =====================================================================
-- CoordinadITO · 04 · Migración v2 → v3 (Iteraciones 3, 4 y trabajo futuro)
-- Ejecutar DESPUÉS de 01, 02 y 03. Solo agrega: no borra datos.
--
--   1. USUARIO.ORIGEN_DATOS     HU 6.4 y 6.5: distinguir datos autodeclarados,
--                               del padrón institucional o validados por coordinación.
--   2. TRAMITE.DIAS_ESPERADOS   HU 7.3: tiempo esperado de atención por tipo de trámite.
--   3. Agenda de citas          HU 9.1, 9.2 y 9.3: bloques de atención y citas.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1. Origen de los datos escolares del alumno
-- ---------------------------------------------------------------------
CREATE TYPE ORIGEN_DATOS AS ENUM ('AUTODECLARADO', 'PADRON', 'VALIDADO');

ALTER TABLE USUARIO ADD COLUMN ORIGEN_DATOS ORIGEN_DATOS;
UPDATE USUARIO SET ORIGEN_DATOS = 'AUTODECLARADO' WHERE ROL = 'ESTUDIANTE';
-- Solo los estudiantes tienen datos escolares.
ALTER TABLE USUARIO ADD CONSTRAINT CHK_USUARIO_ORIGEN
    CHECK ((ROL = 'ESTUDIANTE') = (ORIGEN_DATOS IS NOT NULL));

COMMENT ON COLUMN USUARIO.ORIGEN_DATOS IS
    'De dónde salen carrera y semestre del estudiante: AUTODECLARADO (primer acceso), PADRON (carga institucional) o VALIDADO (corregido por coordinación).';

-- ---------------------------------------------------------------------
-- 2. Tiempo esperado de atención por trámite
-- ---------------------------------------------------------------------
ALTER TABLE TRAMITE ADD COLUMN DIAS_ESPERADOS SMALLINT NOT NULL DEFAULT 5;
ALTER TABLE TRAMITE ADD CONSTRAINT CHK_TRAMITE_DIAS CHECK (DIAS_ESPERADOS BETWEEN 1 AND 180);
COMMENT ON COLUMN TRAMITE.DIAS_ESPERADOS IS
    'Días en que normalmente se resuelve. Una solicitud abierta que los rebasa es un rezago (HU 7.3).';

-- La vista de rezagos usa el tiempo esperado de cada trámite en lugar de 5 días fijos.
DROP VIEW V_SOLICITUD_REZAGADA;
CREATE VIEW V_SOLICITUD_REZAGADA AS
SELECT V.*, NOW() - V.FECHA_REGISTRO AS TIEMPO_ABIERTA
FROM V_SEGUIMIENTO_SOLICITUD V
JOIN TRAMITE T ON T.CLAVE = V.CLAVE_TRAMITE
WHERE V.ESTADO IN ('ENVIADA', 'PROCESANDO', 'ESPERA')
  AND V.FECHA_REGISTRO < NOW() - MAKE_INTERVAL(days => T.DIAS_ESPERADOS);
ALTER VIEW V_SOLICITUD_REZAGADA SET (security_invoker = true);

-- ---------------------------------------------------------------------
-- 3. Agenda de citas
-- ---------------------------------------------------------------------
-- HU 9.2: coordinación decide cuándo una solicitud necesita que el alumno se presente.
ALTER TABLE SOLICITUD ADD COLUMN CITA_REQUERIDA BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN SOLICITUD.CITA_REQUERIDA IS 'Verdadero cuando coordinación pidió atender al alumno en persona (HU 9.2).';

CREATE TYPE ESTADO_CITA AS ENUM ('PROGRAMADA', 'CANCELADA', 'ASISTIO', 'NO_ASISTIO');

-- HU 9.1: cada bloque es un espacio de atención presencial de una persona de coordinación.
CREATE TABLE BLOQUE_ATENCION (
    ID_BLOQUE        UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    ID_COORDINADOR   UUID NOT NULL,
    INICIO           TIMESTAMPTZ NOT NULL,
    FIN              TIMESTAMPTZ NOT NULL,
    LUGAR            VARCHAR(150) NOT NULL,
    CANCELADO        BOOLEAN NOT NULL DEFAULT FALSE,
    FECHA_ALTA       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT FK_BLOQUE_COORDINADOR FOREIGN KEY (ID_COORDINADOR) REFERENCES USUARIO(ID_USUARIO),
    CONSTRAINT CHK_BLOQUE_HORARIO CHECK (FIN > INICIO)
);
-- Una persona no publica dos bloques que empiecen a la misma hora.
CREATE UNIQUE INDEX UQ_BLOQUE_INICIO ON BLOQUE_ATENCION (ID_COORDINADOR, INICIO) WHERE NOT CANCELADO;
CREATE INDEX IDX_BLOQUE_FECHA ON BLOQUE_ATENCION (INICIO) WHERE NOT CANCELADO;

-- HU 9.2 y 9.3: la cita une un bloque con la solicitud que la originó.
CREATE TABLE CITA (
    ID_CITA          UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    ID_BLOQUE        UUID NOT NULL,
    ID_SOLICITUD     UUID NOT NULL,
    ID_ALUMNO        UUID NOT NULL,
    ESTADO           ESTADO_CITA NOT NULL DEFAULT 'PROGRAMADA',
    FECHA_REGISTRO   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT FK_CITA_BLOQUE FOREIGN KEY (ID_BLOQUE) REFERENCES BLOQUE_ATENCION(ID_BLOQUE),
    CONSTRAINT FK_CITA_SOLICITUD FOREIGN KEY (ID_SOLICITUD) REFERENCES SOLICITUD(ID_SOLICITUD),
    CONSTRAINT FK_CITA_ALUMNO FOREIGN KEY (ID_ALUMNO) REFERENCES USUARIO(ID_USUARIO)
);
-- Un bloque ocupado deja de ofrecerse; una solicitud tiene a lo más una cita vigente.
CREATE UNIQUE INDEX UQ_CITA_BLOQUE ON CITA (ID_BLOQUE) WHERE ESTADO = 'PROGRAMADA';
CREATE UNIQUE INDEX UQ_CITA_SOLICITUD ON CITA (ID_SOLICITUD) WHERE ESTADO = 'PROGRAMADA';
CREATE INDEX IDX_CITA_ALUMNO ON CITA (ID_ALUMNO);

COMMENT ON TABLE BLOQUE_ATENCION IS 'Horarios de atención presencial que publica coordinación (HU 9.1).';
COMMENT ON TABLE CITA IS 'Citas presenciales agendadas por alumnos para una solicitud (HU 9.2, 9.3).';

-- Igual que 03: cerrar la API REST de Supabase sobre las tablas nuevas.
ALTER TABLE BLOQUE_ATENCION ENABLE ROW LEVEL SECURITY;
ALTER TABLE CITA ENABLE ROW LEVEL SECURITY;

COMMIT;
