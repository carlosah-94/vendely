-- =============================================================================
--  MIGRACIÓN FLYWAY V17 · Mensajes entre el dueño y soporte de Vendely
--
--  Con el acceso de soporte (V16) el equipo de Vendely puede entrar a armar la tienda,
--  pero el dueño no tenía dónde explicar QUÉ quiere. Esta tabla es esa conversación:
--    - el dueño escribe al dar el acceso ("súbanme estos productos, quiero el banner azul")
--      y después, mientras el acceso siga activo;
--    - soporte responde o pregunta desde la misma tienda;
--    - a cada lado le llega un aviso por correo.
--
--  Los mensajes NO son cambios de la tienda: no van a soporte_registro.
-- =============================================================================

CREATE TABLE IF NOT EXISTS soporte_mensajes (
    id_mensaje    BIGSERIAL      PRIMARY KEY,
    id_tienda     BIGINT         NOT NULL REFERENCES tiendas (id_tienda) ON DELETE CASCADE,
    id_login      BIGINT         NULL REFERENCES login (id_login),   -- quién lo escribió
    de_soporte    BOOLEAN        NOT NULL,                            -- true: soporte · false: el dueño
    texto         VARCHAR(2000)  NOT NULL,
    fecha         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_soporte_mensajes_texto CHECK (length(btrim(texto)) > 0)
);

CREATE INDEX IF NOT EXISTS ix_soporte_mensajes_tienda ON soporte_mensajes (id_tienda, fecha);
