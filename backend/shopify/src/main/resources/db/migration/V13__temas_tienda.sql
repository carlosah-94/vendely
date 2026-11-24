-- =============================================================================
--  MIGRACIÓN FLYWAY V13 · Temas de la tienda (Tienda online → Temas)
--
--  Un tema es una plantilla (clasica, moderna o boutique) con un estilo ya armado:
--  colores, letra, botones y fondo. El catálogo vive en el frontend (utils/temas.js);
--  aplicar un tema guarda esos valores en las columnas de V5 y V12, así que lo que
--  permite cada plan lo sigue validando TiendaService con esas mismas columnas.
--
--  Aquí solo se guarda CUÁL tema aplicó el dueño y cuándo, para mostrarlo en el panel.
--  NULL = nunca aplicó uno (las tiendas que ya existen): el panel lo muestra como "Esencial".
-- =============================================================================

ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS tema             VARCHAR(20)  NULL;
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS tema_aplicado_el TIMESTAMPTZ  NULL;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_tiendas_tema') THEN
        ALTER TABLE tiendas ADD CONSTRAINT ck_tiendas_tema CHECK (tema IS NULL OR tema IN
            ('esencial', 'mercado', 'minimal', 'escaparate', 'pop', 'atelier', 'arena'));
    END IF;
END $$;
