-- =====================================================================================
--  V25: Boleta y factura con la numeración de la SUNAT
--
--  Cada pedido de una tienda de Perú lleva su tipo de comprobante:
--    B = boleta de venta (consumidor final): serie B001. DNI opcional; obligatorio desde S/ 700.
--    F = factura (empresa o negocio):        serie F001. RUC del cliente (con su dígito
--        verificador, fn_ruc_valido de V24) y su razón social obligatorios.
--  La numeración es SERIE-CORRELATIVO, como manda la SUNAT: serie de 4 caracteres que empieza
--  con B o F, y correlativo de hasta 8 dígitos que empieza en 1: B001-00000001, F001-00000001.
--
--  El número se asigna cuando el PAGO SE APRUEBA (no al crear el pedido): así un pedido que nunca
--  se paga no gasta un número. Cada tienda tiene su propia numeración (cada negocio, su RUC).
--  No se repite (UNIQUE) ni se salta: el contador sube dentro de la misma transacción que aprueba
--  el pago, con la fila bloqueada; si algo falla, se deshace todo y el número no se pierde.
--
--  Nota: es la representación del comprobante, lista para conectarse con un OSE (operador
--  autorizado por la SUNAT, como Nubefact); no se envía a la SUNAT desde aquí.
--  Los pedidos anteriores y los de otros países quedan con tipo NULL (comprobante interno).
-- =====================================================================================

ALTER TABLE pedidos
    ADD COLUMN IF NOT EXISTS tipo_comprobante       CHAR(1)      NULL,
    ADD COLUMN IF NOT EXISTS cliente_doc_tipo       VARCHAR(3)   NULL,  -- DNI, CE o RUC
    ADD COLUMN IF NOT EXISTS cliente_doc_numero     VARCHAR(11)  NULL,
    ADD COLUMN IF NOT EXISTS cliente_razon_social   VARCHAR(150) NULL,
    ADD COLUMN IF NOT EXISTS comprobante_serie      VARCHAR(4)   NULL,
    ADD COLUMN IF NOT EXISTS comprobante_numero     INTEGER      NULL,
    ADD COLUMN IF NOT EXISTS comprobante_emitido_el TIMESTAMPTZ  NULL;

ALTER TABLE pedidos ADD CONSTRAINT ck_pedidos_tipo_comprobante
    CHECK (tipo_comprobante IS NULL OR tipo_comprobante IN ('B', 'F'));

-- El documento del cliente, según su tipo.
ALTER TABLE pedidos ADD CONSTRAINT ck_pedidos_cliente_doc CHECK (
    (cliente_doc_tipo IS NULL AND cliente_doc_numero IS NULL)
    OR (cliente_doc_tipo = 'DNI' AND cliente_doc_numero ~ '^[0-9]{8}$')
    OR (cliente_doc_tipo = 'CE'  AND cliente_doc_numero ~ '^[0-9]{9}$')
    OR (cliente_doc_tipo = 'RUC' AND fn_ruc_valido(cliente_doc_numero)));

-- Factura: siempre con el RUC y la razón social del cliente.
ALTER TABLE pedidos ADD CONSTRAINT ck_pedidos_factura CHECK (
    tipo_comprobante IS DISTINCT FROM 'F'
    OR (cliente_doc_tipo = 'RUC' AND length(trim(coalesce(cliente_razon_social, ''))) >= 3));

-- Boleta: con DNI o carné de extranjería (nunca RUC); desde S/ 700 hay que identificar al cliente.
ALTER TABLE pedidos ADD CONSTRAINT ck_pedidos_boleta CHECK (
    tipo_comprobante IS DISTINCT FROM 'B'
    OR (coalesce(cliente_doc_tipo, 'DNI') IN ('DNI', 'CE') AND (total <= 700 OR cliente_doc_numero IS NOT NULL)));

-- Serie y número van juntos, con el formato de la SUNAT, y la serie es la de su tipo.
ALTER TABLE pedidos ADD CONSTRAINT ck_pedidos_comprobante_numero CHECK (
    (comprobante_serie IS NULL AND comprobante_numero IS NULL AND comprobante_emitido_el IS NULL)
    OR (comprobante_serie = tipo_comprobante || '001'
        AND comprobante_numero BETWEEN 1 AND 99999999
        AND comprobante_emitido_el IS NOT NULL));

-- Un número no se repite dentro de la misma tienda y serie.
ALTER TABLE pedidos ADD CONSTRAINT uq_pedidos_comprobante UNIQUE (id_tienda, comprobante_serie, comprobante_numero);

-- ---------------------------------------------------------------- el contador de cada serie

CREATE TABLE IF NOT EXISTS series_comprobante (
    id_tienda      BIGINT     NOT NULL REFERENCES tiendas (id_tienda) ON DELETE CASCADE,
    serie          VARCHAR(4) NOT NULL,
    ultimo_numero  INTEGER    NOT NULL DEFAULT 0,
    PRIMARY KEY (id_tienda, serie),
    CONSTRAINT ck_series_serie  CHECK (serie IN ('B001', 'F001')),
    CONSTRAINT ck_series_numero CHECK (ultimo_numero BETWEEN 0 AND 99999999)
);

-- ---------------------------------------------------------------- al crear el pedido

-- Una tienda de Perú siempre emite comprobante: si no dijo cuál, es boleta. En otro país no hay.
CREATE OR REPLACE FUNCTION fn_pedido_tipo_comprobante() RETURNS trigger AS $$
BEGIN
    IF (SELECT id_pais FROM tiendas WHERE id_tienda = NEW.id_tienda) = 'PE' THEN
        NEW.tipo_comprobante := coalesce(NEW.tipo_comprobante, 'B');
    ELSIF NEW.tipo_comprobante IS NOT NULL THEN
        RAISE EXCEPTION 'La boleta y la factura son solo para tiendas de Perú';
    END IF;
    -- El número lo pone la base al aprobarse el pago, nunca quien crea el pedido.
    NEW.comprobante_serie := NULL;
    NEW.comprobante_numero := NULL;
    NEW.comprobante_emitido_el := NULL;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tg_pedidos_tipo_comprobante ON pedidos;
CREATE TRIGGER tg_pedidos_tipo_comprobante BEFORE INSERT ON pedidos
    FOR EACH ROW EXECUTE FUNCTION fn_pedido_tipo_comprobante();

-- Lo que dice un comprobante no cambia: los datos del cliente quedan fijos desde que se crea el
-- pedido, y el número desde que se emite (corregirlo exigiría una nota de crédito).
CREATE OR REPLACE FUNCTION fn_comprobante_inmutable() RETURNS trigger AS $$
BEGIN
    IF (NEW.tipo_comprobante, NEW.cliente_doc_tipo, NEW.cliente_doc_numero, NEW.cliente_razon_social)
       IS DISTINCT FROM (OLD.tipo_comprobante, OLD.cliente_doc_tipo, OLD.cliente_doc_numero, OLD.cliente_razon_social) THEN
        RAISE EXCEPTION 'Los datos del comprobante del pedido % no se cambian', OLD.numero_pedido;
    END IF;
    IF OLD.comprobante_numero IS NOT NULL
       AND (NEW.comprobante_serie, NEW.comprobante_numero, NEW.comprobante_emitido_el)
           IS DISTINCT FROM (OLD.comprobante_serie, OLD.comprobante_numero, OLD.comprobante_emitido_el) THEN
        RAISE EXCEPTION 'El comprobante %-% ya se emitió y no se cambia', OLD.comprobante_serie,
            lpad(OLD.comprobante_numero::TEXT, 8, '0');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tg_pedidos_comprobante_inmutable ON pedidos;
CREATE TRIGGER tg_pedidos_comprobante_inmutable BEFORE UPDATE ON pedidos
    FOR EACH ROW EXECUTE FUNCTION fn_comprobante_inmutable();

-- ---------------------------------------------------------------- al aprobarse el pago

CREATE OR REPLACE FUNCTION fn_emitir_comprobante() RETURNS trigger AS $$
DECLARE
    v_pedido pedidos;
    v_serie  VARCHAR(4);
    v_numero INTEGER;
BEGIN
    SELECT * INTO v_pedido FROM pedidos WHERE id_pedido = NEW.id_pedido FOR UPDATE;
    IF v_pedido.tipo_comprobante IS NULL OR v_pedido.comprobante_numero IS NOT NULL THEN
        RETURN NEW;   -- sin comprobante (otro país o pedido antiguo) o ya emitido
    END IF;
    v_serie := v_pedido.tipo_comprobante || '001';
    -- Sube el contador de la serie con la fila bloqueada: dos pagos a la vez no toman el mismo número.
    INSERT INTO series_comprobante (id_tienda, serie, ultimo_numero) VALUES (v_pedido.id_tienda, v_serie, 1)
    ON CONFLICT (id_tienda, serie) DO UPDATE SET ultimo_numero = series_comprobante.ultimo_numero + 1
    RETURNING ultimo_numero INTO v_numero;

    UPDATE pedidos
       SET comprobante_serie = v_serie, comprobante_numero = v_numero, comprobante_emitido_el = now()
     WHERE id_pedido = NEW.id_pedido;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tg_pagos_emitir_comprobante ON pagos;
CREATE TRIGGER tg_pagos_emitir_comprobante AFTER INSERT OR UPDATE OF estado ON pagos
    FOR EACH ROW WHEN (NEW.estado = 1) EXECUTE FUNCTION fn_emitir_comprobante();
