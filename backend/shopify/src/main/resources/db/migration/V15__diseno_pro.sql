-- =============================================================================
--  MIGRACIÓN FLYWAY V15 · Diseño avanzado de la vitrina (plan Pro)
--
--  Portada a medida (título, texto, dos botones, alineación, velo y alto), barra de
--  anuncio con color propio y varios mensajes, franja de promoción, categorías en el
--  menú y etiqueta de descuento en los productos rebajados.
--
--  Se guarda como JSON (record DisenoPro): son opciones de presentación que se leen y
--  se escriben siempre juntas, sin consultas por campo. NULL = la tienda nunca lo
--  configuró (se usan los valores por defecto). Lo valida el backend (DisenoPro y
--  TiendaService); la vitrina solo lo muestra si el plan de la tienda es Pro.
-- =============================================================================

ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS diseno_pro JSONB NULL;
