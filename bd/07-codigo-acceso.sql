-- =====================================================================
-- CoordinadITO · 07 · Acceso con código por correo
-- Ejecutar DESPUÉS de 06. No borra datos.
--
-- Quien quiere entrar escribe su correo institucional; el sistema le manda un código de
-- 6 dígitos de un solo uso (AccesoService.solicitarCodigo) y, al escribirlo, entra por el
-- mismo camino que el acceso institucional (AccesoService.ingresar).
--
-- Seguridad:
--   · Se guarda la HUELLA del código (SHA-256 con sal), nunca el código.
--   · Vence a los 10 minutos, sirve una sola vez y admite 5 intentos.
--   · Límite de envíos por correo y por dirección IP (ver AccesoService).
-- =====================================================================

BEGIN;

CREATE TABLE CODIGO_ACCESO (
    ID_CODIGO    UUID         PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    CORREO       VARCHAR(150) NOT NULL,
    HUELLA       VARCHAR(64)  NOT NULL,             -- SHA-256 (hex) de sal + código
    SAL          VARCHAR(32)  NOT NULL,
    CREADO_EN    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    VENCE_EN     TIMESTAMPTZ  NOT NULL,
    USADO_EN     TIMESTAMPTZ,                       -- NULL = aún no se usa
    INTENTOS     SMALLINT     NOT NULL DEFAULT 0,
    IP_ORIGEN    INET,
    CONSTRAINT CHK_CODIGO_VENCE CHECK (VENCE_EN > CREADO_EN),
    CONSTRAINT CHK_CODIGO_INTENTOS CHECK (INTENTOS >= 0)
);

CREATE INDEX IDX_CODIGO_CORREO ON CODIGO_ACCESO (LOWER(CORREO), CREADO_EN DESC);
CREATE INDEX IDX_CODIGO_IP ON CODIGO_ACCESO (IP_ORIGEN, CREADO_EN DESC);

ALTER TABLE CODIGO_ACCESO ENABLE ROW LEVEL SECURITY;

COMMENT ON TABLE CODIGO_ACCESO IS 'Códigos de un solo uso para entrar con el correo institucional (solo su huella).';
COMMENT ON COLUMN CODIGO_ACCESO.HUELLA IS 'SHA-256 en hexadecimal de SAL + código; el código nunca se guarda.';
COMMENT ON COLUMN CODIGO_ACCESO.USADO_EN IS 'Cuándo se usó para entrar (NULL = disponible si no ha vencido).';

COMMIT;
