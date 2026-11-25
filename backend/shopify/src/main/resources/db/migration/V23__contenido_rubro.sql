-- =====================================================================================
--  V23: Contenido de la plantilla por rubro
--
--  Los textos y secciones que el dueño adapta a su negocio (ContenidoRubro.java): anticipación de
--  los pedidos, pasos para pedir, garantía, guía de tallas, sus preguntas frecuentes... Cualquier
--  plan. null = la plantilla usa sus textos por defecto. El backend valida campos y largos.
-- =====================================================================================

ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS contenido_rubro JSONB NULL;

-- Que siempre sea un objeto JSON (nunca un número, un texto o una lista sueltos).
ALTER TABLE tiendas DROP CONSTRAINT IF EXISTS ck_tiendas_contenido_rubro;
ALTER TABLE tiendas ADD CONSTRAINT ck_tiendas_contenido_rubro
    CHECK (contenido_rubro IS NULL OR jsonb_typeof(contenido_rubro) = 'object');
