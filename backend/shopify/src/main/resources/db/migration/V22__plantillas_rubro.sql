-- =====================================================================================
--  V22: Plantillas por rubro
--
--  Cada rubro tiene su plantilla (TemaRubro.java y utils/temas.js): una pastelería nace con
--  "Horno", una tienda de tecnología con "Circuito", etc. Se guardan en la columna tema (V13),
--  así que solo hay que aceptar sus nombres en el CHECK.
--
--  mostrar_seccion_rubro: la sección propia del rubro en la portada (horario y entrega para
--  comida, marcas para tecnología, guía de tallas para moda...). Se puede ocultar en cualquier plan.
-- =====================================================================================

ALTER TABLE tiendas DROP CONSTRAINT IF EXISTS ck_tiendas_tema;
ALTER TABLE tiendas ADD CONSTRAINT ck_tiendas_tema CHECK (tema IS NULL OR tema IN
    ('esencial', 'mercado', 'minimal', 'escaparate', 'pop', 'atelier', 'arena',
     'horno', 'circuito', 'impulso', 'pasarela', 'glow', 'nido', 'huellitas'));

ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS mostrar_seccion_rubro BOOLEAN NOT NULL DEFAULT TRUE;
