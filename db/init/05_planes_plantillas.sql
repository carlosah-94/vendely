-- =============================================================================
--  VENDELY · Planes y plantillas (Entregable 3)
--
--  Agrega los 3 planes de Vendely (Gratis, Básico y Pro), el historial de pagos de
--  plan de cada tienda (suscripciones) y, en tiendas, el plan actual, su vencimiento
--  y la plantilla de diseño. Todas las tiendas que ya existen quedan en el plan Gratis.
--
--  Se puede ejecutar más de una vez: lo que ya existe no se vuelve a crear.
--  Base nueva: Docker lo ejecuta solo, después de 04 (los scripts de db/init van en orden).
--  Base que ya existe (local o Neon): ejecutarlo una vez (DBeaver → Ejecutar script).
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- para la restricción EXCLUDE de suscripciones

-- 31. Planes de Vendely que puede contratar una tienda.
CREATE TABLE IF NOT EXISTS planes (
    id_plan                      SMALLSERIAL   PRIMARY KEY,
    nombre                       VARCHAR(30)   NOT NULL UNIQUE,         -- Gratis, Básico, Pro
    precio_usd                   NUMERIC(8,2)  NOT NULL,                -- precio al mes, en dólares
    max_productos                INTEGER       NULL,                    -- NULL = ilimitados
    comision                     NUMERIC(5,2)  NOT NULL,                -- % que cobra Vendely por cada venta
    permite_personalizar         BOOLEAN       NOT NULL DEFAULT FALSE,  -- logo, color y barra de anuncio
    permite_plantillas           BOOLEAN       NOT NULL DEFAULT FALSE,  -- elegir Moderna o Boutique
    permite_envio_internacional  BOOLEAN       NOT NULL DEFAULT FALSE,
    permite_mas_metodos_pago     BOOLEAN       NOT NULL DEFAULT FALSE,  -- además de PayPal
    estado                       SMALLINT      NOT NULL DEFAULT 1,      -- 1 se ofrece · 0 ya no se vende
    fecha_creacion               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_planes_precio    CHECK (precio_usd >= 0),
    CONSTRAINT ck_planes_productos CHECK (max_productos IS NULL OR max_productos > 0),
    CONSTRAINT ck_planes_comision  CHECK (comision BETWEEN 0 AND 100),
    CONSTRAINT ck_planes_estado    CHECK (estado IN (0, 1))
);

INSERT INTO planes (id_plan, nombre, precio_usd, max_productos, comision,
                    permite_personalizar, permite_plantillas, permite_envio_internacional, permite_mas_metodos_pago)
VALUES (1, 'Gratis',  0.00,    5, 5.00, FALSE, FALSE, FALSE, FALSE),
       (2, 'Básico', 15.00,  100, 2.00, TRUE,  FALSE, FALSE, TRUE),
       (3, 'Pro',    39.00, NULL, 0.50, TRUE,  TRUE,  TRUE,  TRUE)
ON CONFLICT (id_plan) DO NOTHING;
SELECT setval(pg_get_serial_sequence('planes', 'id_plan'), (SELECT max(id_plan) FROM planes));

-- tiendas: el plan que tiene HOY, hasta cuándo, y su plantilla.
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS id_plan       SMALLINT     NOT NULL DEFAULT 1;          -- 1 = Gratis
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS plan_vence_el TIMESTAMPTZ  NULL;                        -- NULL en Gratis
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS plantilla     VARCHAR(20)  NOT NULL DEFAULT 'clasica';
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tiendas_plan') THEN
        ALTER TABLE tiendas ADD CONSTRAINT fk_tiendas_plan FOREIGN KEY (id_plan) REFERENCES planes (id_plan);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_tiendas_plantilla') THEN
        ALTER TABLE tiendas ADD CONSTRAINT ck_tiendas_plantilla CHECK (plantilla IN ('clasica', 'moderna', 'boutique'));
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_tiendas_plan ON tiendas (id_plan);

-- 32. Cada pago de plan de una tienda: su historial y su comprobante.
CREATE TABLE IF NOT EXISTS suscripciones (
    id_suscripcion   BIGSERIAL     PRIMARY KEY,
    id_tienda        BIGINT        NOT NULL REFERENCES tiendas (id_tienda) ON DELETE CASCADE,
    id_plan          SMALLINT      NOT NULL REFERENCES planes (id_plan),
    fecha_inicio     TIMESTAMPTZ   NOT NULL,
    fecha_fin        TIMESTAMPTZ   NOT NULL,                     -- inicio + 30 días
    monto_usd        NUMERIC(8,2)  NOT NULL,                     -- "foto" del precio que pagó
    estado           SMALLINT      NOT NULL DEFAULT 0,           -- 0 pendiente · 1 pagada · 2 cancelada
    referencia_pago  VARCHAR(120)  NULL UNIQUE,                  -- código de la orden de PayPal
    fecha_creacion   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_suscripciones_fechas CHECK (fecha_fin > fecha_inicio),
    CONSTRAINT ck_suscripciones_monto  CHECK (monto_usd > 0),
    CONSTRAINT ck_suscripciones_estado CHECK (estado IN (0, 1, 2)),
    CONSTRAINT ck_suscripciones_pagada CHECK (estado <> 1 OR referencia_pago IS NOT NULL),
    -- una tienda no puede tener dos suscripciones PAGADAS que se cruzan en el tiempo
    CONSTRAINT ex_suscripciones_periodo EXCLUDE USING gist (
        id_tienda WITH =,
        tstzrange(fecha_inicio, fecha_fin, '[)') WITH &&) WHERE (estado = 1)
);
CREATE INDEX IF NOT EXISTS ix_suscripciones_plan ON suscripciones (id_plan);
CREATE INDEX IF NOT EXISTS ix_suscripciones_tienda ON suscripciones (id_tienda, fecha_creacion DESC);
