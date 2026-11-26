-- =====================================================================
-- CoordinadITO · 05 · Lectura de avisos (rediseño del inicio del estudiante)
-- Ejecutar DESPUÉS de 04. Solo agrega una tabla: no borra datos.
--
-- Cada fila dice que una persona ya leyó un aviso. Con eso el inicio muestra cuántos
-- avisos faltan por leer (el número junto al ícono) y pinta en gris los ya leídos.
-- =====================================================================

BEGIN;

CREATE TABLE AVISO_LECTURA (
    ID_AVISO    UUID NOT NULL,
    ID_USUARIO  UUID NOT NULL,
    FECHA       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT PK_AVISO_LECTURA PRIMARY KEY (ID_AVISO, ID_USUARIO),
    CONSTRAINT FK_AL_AVISO FOREIGN KEY (ID_AVISO) REFERENCES AVISO(ID_AVISO) ON DELETE CASCADE,
    CONSTRAINT FK_AL_USUARIO FOREIGN KEY (ID_USUARIO) REFERENCES USUARIO(ID_USUARIO) ON DELETE CASCADE
);
CREATE INDEX IDX_AVISO_LECTURA_USUARIO ON AVISO_LECTURA (ID_USUARIO);

COMMENT ON TABLE AVISO_LECTURA IS 'Qué avisos ya leyó cada persona (contador de pendientes en el inicio).';

-- Igual que 03: cerrar la API REST de Supabase.
ALTER TABLE AVISO_LECTURA ENABLE ROW LEVEL SECURITY;

COMMIT;
