-- V21: costo de cada producto, para que el dueño vea su ganancia y no solo sus ventas.
--
--   precios.costo  lo que le costó al dueño cada unidad (lo que pagó al proveedor), en la
--                  moneda de la tienda. NULL = todavía no se cargó (productos anteriores a V21).
--
--   El costo vive junto al precio a propósito: cada línea de un pedido ya guarda con qué precio
--   se vendió (detalle_pedido.id_precio). Así cada venta conserva el costo de ESE día, aunque el
--   dueño cambie después lo que le cobra su proveedor. Es como la boleta de compra: no cambia
--   porque mañana suba el precio. Cambiar el costo cierra el precio vigente y crea otro igual
--   que el anterior pero con el costo nuevo (la misma regla que ya existe para el monto).

ALTER TABLE precios ADD COLUMN costo NUMERIC(12,2) NULL;
ALTER TABLE precios ADD CONSTRAINT ck_precios_costo CHECK (costo IS NULL OR costo >= 0);

-- T3 (ampliado): el costo de un precio ya creado tampoco se edita.
CREATE OR REPLACE FUNCTION fn_precio_inmutable() RETURNS trigger AS $$
BEGIN
    IF NEW.monto IS DISTINCT FROM OLD.monto
       OR NEW.monto_comparacion IS DISTINCT FROM OLD.monto_comparacion
       OR NEW.costo IS DISTINCT FROM OLD.costo
       OR NEW.id_variante IS DISTINCT FROM OLD.id_variante
       OR NEW.fecha_inicio IS DISTINCT FROM OLD.fecha_inicio THEN
        RAISE EXCEPTION 'Un precio no se edita: ciérralo con fecha_fin y crea uno nuevo';
    END IF;
    -- fecha_fin solo pasa de vacía a una fecha (cerrar); después no cambia
    IF OLD.fecha_fin IS NOT NULL AND NEW.fecha_fin IS DISTINCT FROM OLD.fecha_fin THEN
        RAISE EXCEPTION 'El precio % ya está cerrado: su periodo no se cambia', OLD.id_precio;
    END IF;
    -- y no puede cerrarse antes de una venta que ya lo usó
    IF OLD.fecha_fin IS NULL AND NEW.fecha_fin IS NOT NULL AND EXISTS (
        SELECT 1 FROM detalle_pedido d JOIN pedidos p USING (id_pedido)
         WHERE d.id_precio = NEW.id_precio AND p.fecha_creacion >= NEW.fecha_fin) THEN
        RAISE EXCEPTION 'El precio % se cobró en un pedido posterior a esa fecha de cierre', NEW.id_precio;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- T16 (ampliado): el costo respeta los decimales de la moneda de la tienda (en pesos chilenos, sin decimales).
CREATE OR REPLACE FUNCTION fn_monto_en_moneda() RETURNS trigger AS $$
DECLARE v_tienda BIGINT; v_dec SMALLINT; v_montos NUMERIC[];
BEGIN
    CASE TG_TABLE_NAME
    WHEN 'precios' THEN
        SELECT p.id_tienda INTO v_tienda FROM variantes v JOIN productos p USING (id_producto)
         WHERE v.id_variante = NEW.id_variante;
        v_montos := ARRAY[NEW.monto, NEW.monto_comparacion, NEW.costo];
    WHEN 'ofertas' THEN
        v_tienda := NEW.id_tienda;
        v_montos := CASE WHEN NEW.tipo_descuento = 2 THEN ARRAY[NEW.valor] ELSE ARRAY[]::NUMERIC[] END;
    ELSE  -- tarifas_envio y tarifas_internacionales
        v_tienda := NEW.id_tienda;
        v_montos := ARRAY[NEW.costo, NEW.envio_gratis_desde];
    END CASE;
    v_dec := fn_decimales_tienda(v_tienda);
    IF v_dec IS NOT NULL AND EXISTS (SELECT 1 FROM unnest(v_montos) m WHERE m IS NOT NULL AND m <> round(m, v_dec)) THEN
        RAISE EXCEPTION 'En la moneda de la tienda (%) los montos van con % decimales',
            (fn_pais_de_tienda(v_tienda)).id_moneda, v_dec;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
