-- V18: formas de entrega además del courier (como "Entrega local" y "Retiro en tienda" de Shopify).
--
--   Una pastelería de Surco no manda sus tortas por Olva: las lleva ella misma a los distritos
--   cercanos, el mismo día, o el cliente pasa a recogerlas.
--
--   zonas_delivery  distritos (último nivel de la lista oficial) a los que la tienda lleva sus
--                   pedidos, con su costo. Sin zonas, la tienda no ofrece delivery.
--   tiendas         recojo_activo + dirección y horario del local.
--   pedidos         tipo_entrega: 0 courier · 1 delivery de la tienda · 2 recojo en tienda.
--                   El costo lo sigue poniendo el trigger T2 (fn_preparar_pedido), que ahora
--                   mira el tipo: el cliente no puede mandar "recojo" en una tienda sin local,
--                   ni "delivery" a un distrito que la tienda no atiende.

ALTER TABLE tiendas
    ADD COLUMN recojo_activo    BOOLEAN      NOT NULL DEFAULT false,
    ADD COLUMN recojo_direccion VARCHAR(200) NULL,
    ADD COLUMN recojo_horario   VARCHAR(120) NULL,
    ADD CONSTRAINT ck_tiendas_recojo CHECK (NOT recojo_activo OR recojo_direccion IS NOT NULL);

CREATE TABLE zonas_delivery (
    id_tienda           BIGINT        NOT NULL REFERENCES tiendas (id_tienda) ON DELETE CASCADE,
    id_division         BIGINT        NOT NULL REFERENCES divisiones (id_division),
    costo               NUMERIC(12,2) NOT NULL,                 -- en la moneda de la tienda
    envio_gratis_desde  NUMERIC(12,2) NULL,                     -- NULL = nunca es gratis
    estado              SMALLINT      NOT NULL DEFAULT 1,       -- 1 activa · 0 pausada
    PRIMARY KEY (id_tienda, id_division),                       -- un costo por distrito
    CONSTRAINT ck_zonas_costo  CHECK (costo >= 0),
    CONSTRAINT ck_zonas_gratis CHECK (envio_gratis_desde IS NULL OR envio_gratis_desde > 0),
    CONSTRAINT ck_zonas_estado CHECK (estado IN (0, 1))
);

-- El distrito es del país de la tienda y del último nivel de su lista (distrito, comuna...).
CREATE OR REPLACE FUNCTION fn_validar_zona_delivery() RETURNS trigger AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM divisiones d JOIN tiendas t ON t.id_pais = d.id_pais
         WHERE t.id_tienda = NEW.id_tienda AND d.id_division = NEW.id_division
           AND d.nivel = (SELECT max(n.nivel) FROM niveles_division n WHERE n.id_pais = d.id_pais)) THEN
        RAISE EXCEPTION 'La zona de delivery % debe ser un distrito del país de la tienda %', NEW.id_division, NEW.id_tienda;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_zonas_delivery_validar BEFORE INSERT OR UPDATE ON zonas_delivery
    FOR EACH ROW EXECUTE FUNCTION fn_validar_zona_delivery();

ALTER TABLE pedidos
    ADD COLUMN tipo_entrega SMALLINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_pedidos_tipo_entrega CHECK (tipo_entrega IN (0, 1, 2));

-- Un pedido enviado por courier necesita empresa y número de seguimiento. En delivery propio y en
-- recojo no hay número que rastrear: basta la fecha en que salió (o en que quedó listo).
ALTER TABLE pedidos DROP CONSTRAINT ck_pedidos_envio;
ALTER TABLE pedidos
    ADD CONSTRAINT ck_pedidos_envio CHECK (
        estado NOT IN (1, 2)
        OR (fecha_envio IS NOT NULL
            AND (tipo_entrega <> 0 OR (empresa_envio IS NOT NULL AND numero_seguimiento IS NOT NULL)))
    ) NOT VALID;

-- T2 con el tipo de entrega (el resto es igual a V14).
CREATE OR REPLACE FUNCTION fn_preparar_pedido() RETURNS trigger AS $$
DECLARE
    v_nuevo    BOOLEAN := (TG_OP = 'INSERT');
    v_envio    BOOLEAN;   -- ¿hay que revisar la tarifa?
    v_montos   BOOLEAN;   -- ¿hay que revisar total e impuesto?
    v_pais     paises;    -- país de la tienda (moneda, zona horaria)
    v_fecha    DATE;      -- fecha del pedido en la hora de ese país
    v_destino  CHAR(2);   -- país de la dirección de envío
    v_division BIGINT;    -- división de la dirección de envío (solo ventas nacionales)
    v_export   BOOLEAN;   -- ¿es una venta al extranjero?
    v_tarifa   RECORD;
    v_costo    NUMERIC(12,2);
    v_tasa     NUMERIC(5,2);
    v_imp      NUMERIC(12,2);
    v_dec      SMALLINT;  -- decimales de la moneda de la tienda
    v_recojo   BOOLEAN;   -- ¿la tienda ofrece recojo en tienda? (V18)
BEGIN
    v_pais := fn_pais_de_tienda(NEW.id_tienda);
    IF v_pais.id_pais IS NULL THEN RETURN NEW; END IF;   -- tienda inexistente: la FK se encarga
    v_fecha := (NEW.fecha_creacion AT TIME ZONE v_pais.zona_horaria)::date;

    SELECT id_pais, id_division INTO v_destino, v_division
      FROM direcciones WHERE id_direccion = NEW.id_direccion_envio;
    IF NOT FOUND THEN RETURN NEW; END IF;                -- dirección inexistente: la FK se encarga
    v_export := (v_destino <> v_pais.id_pais);

    IF v_nuevo THEN
        IF (SELECT estado FROM tiendas WHERE id_tienda = NEW.id_tienda) <> 1 THEN
            RAISE EXCEPTION 'La tienda % está suspendida y no puede recibir pedidos', NEW.id_tienda;
        END IF;

        UPDATE tiendas
           SET ultimo_numero_pedido = ultimo_numero_pedido + 1
         WHERE id_tienda = NEW.id_tienda
        RETURNING ultimo_numero_pedido INTO NEW.numero_pedido;

        IF NEW.moneda IS NULL THEN
            NEW.moneda := v_pais.id_moneda;
        END IF;

        IF NOT v_export AND NEW.id_impuesto IS NULL THEN
            SELECT id_impuesto INTO NEW.id_impuesto
              FROM impuestos
             WHERE id_pais = v_pais.id_pais
               AND vigente_desde <= v_fecha
               AND (vigente_hasta IS NULL OR vigente_hasta > v_fecha);
            IF NOT FOUND THEN
                RAISE EXCEPTION 'No hay un impuesto vigente en % para el %', v_pais.id_pais, v_fecha;
            END IF;
        END IF;
    END IF;

    -- El pedido se cobra en la moneda de la tienda, y eso no cambia
    IF v_nuevo AND NEW.moneda <> v_pais.id_moneda THEN
        RAISE EXCEPTION 'El pedido se cobra en la moneda de la tienda (%), no en %', v_pais.id_moneda, NEW.moneda;
    END IF;
    IF NOT v_nuevo AND NEW.moneda IS DISTINCT FROM OLD.moneda THEN
        RAISE EXCEPTION 'La moneda de un pedido no se cambia';
    END IF;

    -- Los montos respetan los decimales de la moneda (en pesos chilenos no hay centavos)
    SELECT decimales INTO v_dec FROM monedas WHERE id_moneda = NEW.moneda;
    IF NEW.subtotal <> round(NEW.subtotal, v_dec)
       OR NEW.costo_envio <> round(NEW.costo_envio, v_dec)
       OR NEW.total <> round(NEW.total, v_dec)
       OR NEW.descuento_cupon <> round(NEW.descuento_cupon, v_dec) THEN
        RAISE EXCEPTION 'En % los montos van con % decimales', NEW.moneda, v_dec;
    END IF;

    -- El tipo de cambio mostrado no puede ser de un día posterior al pedido
    IF NEW.fecha_tipo_cambio IS NOT NULL AND NEW.fecha_tipo_cambio > v_fecha THEN
        RAISE EXCEPTION 'El tipo de cambio del % es posterior a la fecha del pedido (%)', NEW.fecha_tipo_cambio, v_fecha;
    END IF;

    -- Impuesto: el del país de la tienda en ventas nacionales; ninguno en exportaciones
    IF v_nuevo OR (NEW.id_impuesto, NEW.fecha_creacion, NEW.id_direccion_envio)
                  IS DISTINCT FROM (OLD.id_impuesto, OLD.fecha_creacion, OLD.id_direccion_envio) THEN
        IF v_export THEN
            IF NEW.id_impuesto IS NOT NULL THEN
                RAISE EXCEPTION 'El pedido va a % y la tienda es de %: es una exportación y no lleva impuesto',
                    v_destino, v_pais.id_pais;
            END IF;
        ELSE
            IF NEW.id_impuesto IS NULL THEN
                RAISE EXCEPTION 'Una venta dentro de % debe llevar su impuesto', v_pais.id_pais;
            END IF;
            IF NOT EXISTS (SELECT 1 FROM impuestos
                            WHERE id_impuesto = NEW.id_impuesto AND id_pais = v_pais.id_pais) THEN
                RAISE EXCEPTION 'La tasa % no es de % (país de la tienda)', NEW.id_impuesto, v_pais.id_pais;
            END IF;
            IF NOT EXISTS (SELECT 1 FROM impuestos
                            WHERE id_impuesto = NEW.id_impuesto
                              AND vigente_desde <= v_fecha
                              AND (vigente_hasta IS NULL OR vigente_hasta > v_fecha)) THEN
                RAISE EXCEPTION 'La tasa % no estaba vigente el %', NEW.id_impuesto, v_fecha;
            END IF;
        END IF;
    END IF;

    -- Al editar un pedido solo se revisa lo que cambió: si la tienda subió
    -- su tarifa después, cambiar las notas o la facturación de un pedido
    -- antiguo no debe fallar.
    v_envio  := v_nuevo OR (NEW.id_direccion_envio, NEW.subtotal, NEW.costo_envio, NEW.tipo_entrega)
                           IS DISTINCT FROM (OLD.id_direccion_envio, OLD.subtotal, OLD.costo_envio, OLD.tipo_entrega);
    v_montos := v_envio OR (NEW.total, NEW.monto_impuestos, NEW.id_impuesto, NEW.descuento_cupon)
                           IS DISTINCT FROM (OLD.total, OLD.monto_impuestos, OLD.id_impuesto, OLD.descuento_cupon);

    -- Un pedido ya pagado no cambia sus montos (el cobro quedaría distinto del total)
    IF NOT v_nuevo AND (NEW.subtotal, NEW.costo_envio, NEW.total, NEW.monto_impuestos, NEW.id_impuesto,
                        NEW.id_cupon, NEW.descuento_cupon, NEW.tipo_entrega)
                       IS DISTINCT FROM (OLD.subtotal, OLD.costo_envio, OLD.total, OLD.monto_impuestos, OLD.id_impuesto,
                                         OLD.id_cupon, OLD.descuento_cupon, OLD.tipo_entrega)
       AND EXISTS (SELECT 1 FROM pagos WHERE id_pedido = NEW.id_pedido AND estado = 1) THEN
        RAISE EXCEPTION 'El pedido % ya tiene un pago aprobado: sus montos no se cambian', NEW.id_pedido;
    END IF;

    -- Direcciones del mismo cliente (las nuevas o las que cambiaron)
    IF v_nuevo OR NEW.id_login IS DISTINCT FROM OLD.id_login
       OR NEW.id_direccion_envio IS DISTINCT FROM OLD.id_direccion_envio THEN
        PERFORM fn_validar_direccion_pedido(NEW.id_direccion_envio, NEW.id_login, NEW.id_tienda, NEW.id_pedido);
    END IF;
    IF v_nuevo OR NEW.id_login IS DISTINCT FROM OLD.id_login
       OR NEW.id_direccion_facturacion IS DISTINCT FROM OLD.id_direccion_facturacion THEN
        PERFORM fn_validar_direccion_pedido(NEW.id_direccion_facturacion, NEW.id_login, NEW.id_tienda, NEW.id_pedido);
    END IF;

    -- Costo de la entrega según cómo la eligió el cliente (V18):
    --   0 envío por courier: tarifa por región dentro del país, por país hacia el extranjero
    --   1 delivery de la tienda: solo a los distritos que la tienda configuró (zonas_delivery)
    --   2 recojo en tienda: gratis, solo si la tienda lo ofrece
    IF v_envio THEN
        IF NEW.tipo_entrega IN (1, 2) AND v_export THEN
            RAISE EXCEPTION 'El delivery y el recojo en tienda son solo dentro de % (el pedido va a %)',
                v_pais.id_pais, v_destino;
        END IF;

        IF NEW.tipo_entrega = 2 THEN
            SELECT recojo_activo INTO v_recojo FROM tiendas WHERE id_tienda = NEW.id_tienda;
            IF NOT v_recojo THEN
                RAISE EXCEPTION 'La tienda % no ofrece recojo en tienda', NEW.id_tienda;
            END IF;
            SELECT 0::numeric AS costo, NULL::numeric AS envio_gratis_desde INTO v_tarifa;
        ELSIF NEW.tipo_entrega = 1 THEN
            SELECT costo, envio_gratis_desde INTO v_tarifa
              FROM zonas_delivery
             WHERE id_tienda = NEW.id_tienda AND id_division = v_division AND estado = 1;
            IF NOT FOUND THEN
                RAISE EXCEPTION 'La tienda % no hace delivery a ese distrito', NEW.id_tienda;
            END IF;
        ELSIF v_export THEN
            SELECT costo, envio_gratis_desde INTO v_tarifa
              FROM tarifas_internacionales
             WHERE id_tienda = NEW.id_tienda AND id_pais = v_destino AND estado = 1;
            IF NOT FOUND THEN
                RAISE EXCEPTION 'La tienda % no envía a % (no tiene una tarifa internacional activa para ese país)',
                    NEW.id_tienda, v_destino;
            END IF;
        ELSE
            -- una venta nacional necesita la ubicación de la lista oficial (la
            -- dirección pudo escribirse a mano antes de habilitar el país)
            IF v_division IS NULL THEN
                RAISE EXCEPTION 'La dirección % se escribió a mano: para una venta dentro de % registre una dirección elegida de la lista oficial',
                    NEW.id_direccion_envio, v_pais.id_pais;
            END IF;
            SELECT costo, envio_gratis_desde INTO v_tarifa
              FROM tarifas_envio
             WHERE id_tienda = NEW.id_tienda AND id_division = fn_division_nivel1(v_division) AND estado = 1;
            IF NOT FOUND THEN
                RAISE EXCEPTION 'La tienda % no tiene una tarifa de envío activa para esa zona', NEW.id_tienda;
            END IF;
        END IF;

        v_costo := CASE WHEN v_tarifa.envio_gratis_desde IS NOT NULL
                             AND NEW.subtotal >= v_tarifa.envio_gratis_desde
                        THEN 0 ELSE v_tarifa.costo END;
        IF NEW.costo_envio IS NULL THEN
            NEW.costo_envio := v_costo;
        ELSIF NEW.costo_envio <> v_costo THEN
            RAISE EXCEPTION 'El costo de envío debe ser % % según la tarifa de la tienda', v_costo, NEW.moneda;
        END IF;
    END IF;

    -- Total e impuesto incluido (0 en una exportación)
    IF v_montos THEN
        IF NEW.total IS NULL THEN
            NEW.total := NEW.subtotal - NEW.descuento_cupon + NEW.costo_envio;
        END IF;
        IF NEW.id_impuesto IS NULL THEN
            v_tasa := 0;
        ELSE
            SELECT tasa INTO v_tasa FROM impuestos WHERE id_impuesto = NEW.id_impuesto;
        END IF;
        v_imp := round(NEW.total * v_tasa / (100 + v_tasa), v_dec);
        IF NEW.monto_impuestos IS NULL THEN
            NEW.monto_impuestos := v_imp;
        ELSIF NEW.monto_impuestos <> v_imp THEN
            RAISE EXCEPTION 'El impuesto incluido debe ser % % (total × % / %)', v_imp, NEW.moneda, v_tasa, 100 + v_tasa;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
