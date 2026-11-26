-- =====================================================================
-- CoordinadITO · 06 · Se retira el rol de administración
-- Ejecutar DESPUÉS de 05. No borra datos.
--
-- Lo que hacía administración se repartió (ver modelo.bean.Permiso):
--   · Coordinación: personal, padrón y auditoría, solo de sus carreras.
--   · Jefatura: lo mismo en todas las carreras, más lo que es común a todas
--     (catálogo de trámites, periodos y prioridades).
--
-- 1. Las cuentas que eran de administración pasan a jefatura: conservan el acceso
--    y su historial (cargas de padrón, auditoría). Jefatura puede cambiarlas después.
-- 2. Ninguna cuenta puede volver a tener el rol ADMINISTRADOR. El valor sigue en el
--    tipo ROLES porque PostgreSQL no permite quitar un valor de un ENUM sin recrearlo;
--    la restricción CHK_SIN_ADMINISTRACION hace el mismo trabajo sin riesgo.
-- 3. CARGA_PADRON.ID_ADMINISTRADOR se llama ahora ID_RESPONSABLE (quien subió el archivo).
-- =====================================================================

BEGIN;

UPDATE USUARIO SET ROL = 'JEFE' WHERE ROL = 'ADMINISTRADOR';

ALTER TABLE USUARIO ADD CONSTRAINT CHK_SIN_ADMINISTRACION CHECK (ROL <> 'ADMINISTRADOR');

ALTER TABLE CARGA_PADRON RENAME COLUMN ID_ADMINISTRADOR TO ID_RESPONSABLE;

COMMENT ON COLUMN USUARIO.ROL IS 'ESTUDIANTE, COORDINADOR o JEFE (HU 6.3). ADMINISTRADOR ya no se usa (06).';
COMMENT ON TABLE CARGA_PADRON IS 'Cada archivo de padrón que sube una coordinación (sus carreras) o jefatura (HU 8.1).';
COMMENT ON COLUMN CARGA_PADRON.ID_RESPONSABLE IS 'Quién subió el archivo.';
COMMENT ON TABLE AUDITORIA IS 'Bitácora de acciones de gestión; solo admite inserciones (HU 8.2). Jefatura la ve completa; coordinación, la de sus carreras.';

COMMIT;
