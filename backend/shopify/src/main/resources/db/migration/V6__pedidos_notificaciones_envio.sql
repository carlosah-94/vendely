-- ---------------------------------------------------------------------
--  MIGRACIÓN FLYWAY V6 · Notificaciones, comprobante y seguimiento del envío
--
--  Flyway la aplica UNA sola vez y guarda su checksum en la tabla
--  flyway_schema_history. Por eso este archivo ya NO se edita: cambiar
--  una sola letra cambia el checksum y el backend no arranca. Los
--  cambios al esquema o a los datos van en una migración nueva (V7, V8…).
-- ---------------------------------------------------------------------
--
--  Qué agrega:
--    1. En pedidos, el seguimiento del envío (empresa, número y fechas) y la
--       comisión que Vendely cobra por esa venta según el plan de la tienda.
--    2. La tabla notificaciones: el registro de cada correo que el sistema
--       intentó enviar, con su resultado. Sirve para que el dueño vea qué se
--       envió y para no perder el rastro cuando el envío falla.
-- =====================================================================

-- 1. Para que notificaciones pueda apuntar al pedido CON su tienda (FK compuesta, como
--    el resto del esquema) hace falta este UNIQUE: una FK solo puede apuntar a columnas
--    con unicidad declarada. id_pedido ya es único por ser la llave primaria, pero el par
--    (id_pedido, id_tienda) necesita decirlo explícitamente.
ALTER TABLE pedidos ADD CONSTRAINT uq_pedidos_tienda UNIQUE (id_pedido, id_tienda);

-- 2. Seguimiento del envío y comisión de la plataforma.
--    Las fechas son de cada paso: el pedido pasa de sin enviar (0) a enviado (1) y de ahí a
--    entregado (2); desde sin enviar también puede cancelarse (3). Esas reglas de transición
--    las aplica PedidoService: un CHECK no puede comparar el estado anterior con el nuevo.
ALTER TABLE pedidos
    ADD COLUMN empresa_envio       VARCHAR(60)   NULL,   -- Olva, Shalom, Chilexpress…
    ADD COLUMN numero_seguimiento  VARCHAR(60)   NULL,   -- el código con el que el cliente rastrea
    ADD COLUMN fecha_envio         TIMESTAMPTZ   NULL,
    ADD COLUMN fecha_entrega       TIMESTAMPTZ   NULL,
    ADD COLUMN fecha_cancelacion   TIMESTAMPTZ   NULL,
    -- Lo que Vendely cobra por esta venta: total × comisión del plan. Se guarda al aprobarse
    -- el pago, no se calcula después, porque la tienda puede cambiar de plan más adelante.
    ADD COLUMN comision_plataforma NUMERIC(12,2) NULL;

-- Un pedido enviado o entregado tiene que tener empresa, número y fecha de envío; y solo un
-- pedido cancelado tiene fecha de cancelación.
--
-- NOT VALID: las reglas se aplican de aquí en adelante (a todo INSERT y UPDATE), pero no se
-- revisan las filas que ya existen. Es a propósito: si en alguna base hay un pedido viejo
-- marcado como enviado sin número de seguimiento, la migración fallaría y el backend no
-- arrancaría. Cuando se confirme que una base está limpia se puede ejecutar a mano:
--   ALTER TABLE pedidos VALIDATE CONSTRAINT ck_pedidos_envio;
ALTER TABLE pedidos
    ADD CONSTRAINT ck_pedidos_envio CHECK (
        estado NOT IN (1, 2)
        OR (empresa_envio IS NOT NULL AND numero_seguimiento IS NOT NULL AND fecha_envio IS NOT NULL)
    ) NOT VALID,
    ADD CONSTRAINT ck_pedidos_cancelacion CHECK ((fecha_cancelacion IS NULL) = (estado <> 3)) NOT VALID,
    ADD CONSTRAINT ck_pedidos_entrega CHECK (fecha_entrega IS NULL OR fecha_envio IS NOT NULL) NOT VALID,
    ADD CONSTRAINT ck_pedidos_comision CHECK (comision_plataforma IS NULL OR comision_plataforma >= 0);

COMMENT ON COLUMN pedidos.comision_plataforma IS
    'Comision de Vendely por esta venta, en la moneda del pedido. Se calcula al aprobarse el pago.';

-- 3. Cada correo que el sistema intentó enviar.
--    Se guarda el intento aunque falle: así el dueño ve que el correo no salió, y el pedido
--    nunca se queda sin completar por un problema del proveedor de correo.
CREATE TABLE notificaciones (
    id_notificacion  BIGSERIAL      PRIMARY KEY,
    id_tienda        BIGINT         NOT NULL REFERENCES tiendas (id_tienda),
    -- NULL en los avisos que no son de un pedido (por ejemplo, el de stock bajo)
    id_pedido        BIGINT         NULL,
    destinatario     VARCHAR(150)   NOT NULL,
    /* 1 pago aprobado (al cliente) · 2 nuevo pedido (al dueño) · 3 pedido enviado (al cliente)
       4 stock bajo (al dueño) */
    tipo             SMALLINT       NOT NULL,
    asunto           VARCHAR(200)   NOT NULL,
    -- 1 enviado · 2 fallido · 3 simulado (sin RESEND_API_KEY: se registra sin enviar)
    estado           SMALLINT       NOT NULL,
    error            TEXT           NULL,       -- el motivo, solo cuando falló
    fecha_creacion   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    fecha_envio      TIMESTAMPTZ    NULL,       -- cuándo salió; NULL si falló

    -- El pedido tiene que ser de ESA tienda (FK compuesta, igual que en el resto del esquema).
    -- Con id_pedido NULL la FK no se comprueba, que es justo lo que se quiere para el stock bajo.
    CONSTRAINT fk_notificaciones_pedido
        FOREIGN KEY (id_pedido, id_tienda) REFERENCES pedidos (id_pedido, id_tienda),
    CONSTRAINT ck_notificaciones_tipo   CHECK (tipo BETWEEN 1 AND 4),
    CONSTRAINT ck_notificaciones_estado CHECK (estado BETWEEN 1 AND 3),
    -- el motivo del error solo tiene sentido si falló, y solo lo fallido no tiene fecha de envío
    CONSTRAINT ck_notificaciones_error  CHECK (estado = 2 OR error IS NULL),
    CONSTRAINT ck_notificaciones_envio  CHECK ((estado = 2) = (fecha_envio IS NULL))
);

-- Para "los correos de este pedido" (detalle de la venta en el panel) y para la lista por tienda.
CREATE INDEX ix_notificaciones_pedido ON notificaciones (id_pedido, fecha_creacion DESC);
CREATE INDEX ix_notificaciones_tienda ON notificaciones (id_tienda, fecha_creacion DESC);

COMMENT ON TABLE notificaciones IS
    'Registro de los correos que el sistema intento enviar, con su resultado (issue #3).';
