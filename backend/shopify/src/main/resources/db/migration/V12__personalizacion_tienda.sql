-- =============================================================================
--  MIGRACIÓN FLYWAY V12 · Personalización de la tienda pública
--
--  Hasta ahora el dueño solo elegía logo, color principal y plantilla. Con esta
--  migración también puede elegir (según su plan, lo valida TiendaService):
--
--    Todos los planes
--      mostrar_garantias, mostrar_destacado, mostrar_categorias: secciones de la portada
--    Básico y Pro (planes.permite_personalizar)
--      color_acento        etiquetas de oferta, detalles y cinta
--      texto_anuncio       la barra de arriba (NULL = el texto automático de envíos)
--      mostrar_anuncio     ocultar la barra de anuncio
--      imagen_portada_url  el banner de la portada
--      fuente              'auto' (la de la plantilla), moderna, expresiva, elegante, impacto
--      estilo_botones      'auto' (el de la plantilla), redondo, suave, recto
--    Pro (planes.permite_plantillas)
--      fondo               blanco, hueso, perla, arena (tonos claros: el texto siempre se lee)
--
--  Los valores por defecto dejan las tiendas que ya existen tal como se veían.
--  Si una tienda baja de plan, lo guardado no se borra: TiendaPublicaResponse no lo
--  muestra y vuelve a verse al mejorar el plan.
-- =============================================================================

ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS color_acento       VARCHAR(7)   NULL;
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS texto_anuncio      VARCHAR(120) NULL;
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS mostrar_anuncio    BOOLEAN      NOT NULL DEFAULT TRUE;
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS imagen_portada_url VARCHAR(500) NULL;
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS fuente             VARCHAR(20)  NOT NULL DEFAULT 'auto';
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS estilo_botones     VARCHAR(20)  NOT NULL DEFAULT 'auto';
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS fondo              VARCHAR(20)  NOT NULL DEFAULT 'blanco';
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS mostrar_garantias  BOOLEAN      NOT NULL DEFAULT TRUE;
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS mostrar_destacado  BOOLEAN      NOT NULL DEFAULT TRUE;
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS mostrar_categorias BOOLEAN      NOT NULL DEFAULT TRUE;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_tiendas_color_acento') THEN
        ALTER TABLE tiendas ADD CONSTRAINT ck_tiendas_color_acento
            CHECK (color_acento IS NULL OR color_acento ~ '^#[0-9A-F]{6}$');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_tiendas_fuente') THEN
        ALTER TABLE tiendas ADD CONSTRAINT ck_tiendas_fuente
            CHECK (fuente IN ('auto', 'moderna', 'expresiva', 'elegante', 'impacto'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_tiendas_estilo_botones') THEN
        ALTER TABLE tiendas ADD CONSTRAINT ck_tiendas_estilo_botones
            CHECK (estilo_botones IN ('auto', 'redondo', 'suave', 'recto'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_tiendas_fondo') THEN
        ALTER TABLE tiendas ADD CONSTRAINT ck_tiendas_fondo
            CHECK (fondo IN ('blanco', 'hueso', 'perla', 'arena'));
    END IF;
END $$;
