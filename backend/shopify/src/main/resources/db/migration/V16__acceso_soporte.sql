-- =============================================================================
--  MIGRACIÓN FLYWAY V16 · Acceso de soporte de Vendely
--
--  El dueño puede dar permiso al equipo de Vendely (superadmin) para entrar a su
--  tienda y armarla: subir productos, fotos, envíos, diseño. El permiso:
--    - vence solo (soporte_hasta) y el dueño lo puede quitar cuando quiera;
--    - no ocupa un cupo de empleado y funciona con cualquier plan;
--    - no deja tocar el plan ni los pagos, ni agregar empleados, ni eliminar ventas.
--
--  Todo queda anotado en soporte_registro: cuándo se dio o quitó el permiso y cada
--  cambio que hizo soporte dentro de la tienda (método y ruta de la petición).
-- =============================================================================

-- NULL o una fecha pasada = soporte no puede entrar.
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS soporte_hasta TIMESTAMPTZ NULL;

CREATE TABLE IF NOT EXISTS soporte_registro (
    id_registro   BIGSERIAL     PRIMARY KEY,
    id_tienda     BIGINT        NOT NULL REFERENCES tiendas (id_tienda) ON DELETE CASCADE,
    id_login      BIGINT        NULL REFERENCES login (id_login),   -- quién lo hizo (dueño o soporte)
    accion        VARCHAR(20)   NOT NULL,
    metodo        VARCHAR(10)   NULL,                                -- POST, PUT, PATCH, DELETE (solo en CAMBIO)
    ruta          VARCHAR(300)  NULL,                                -- /api/panel/tiendas/5/productos (solo en CAMBIO)
    fecha         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_soporte_registro_accion CHECK (accion IN ('PERMITIDO', 'QUITADO', 'CAMBIO'))
);

CREATE INDEX IF NOT EXISTS ix_soporte_registro_tienda ON soporte_registro (id_tienda, fecha DESC);
