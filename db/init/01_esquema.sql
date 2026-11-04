-- =====================================================================
--  VENDELY · Modelo de base de datos · versión PERÚ
--  PostgreSQL 15 o superior · 24 tablas · normalizado hasta 3FN
--  Requiere la extensión btree_gist (viene incluida en PostgreSQL).
--
--  Alcance: las tiendas venden y envían dentro del Perú. Por eso no hay
--  columnas ni tablas de país: todo es Perú y todo es en soles (PEN).
--
--  Convenciones
--    · Llaves: id_<tabla>. Las tablas puente sin hijas usan llave
--      primaria compuesta; las entidades con hijas usan id + UNIQUE.
--    · Estados como SMALLINT con CHECK (los códigos están comentados).
--    · Montos en NUMERIC(12,2). Fechas en TIMESTAMPTZ.
--    · Los precios de los productos INCLUYEN el IGV.
--
--  Bloques: 1 territorio e impuestos · 2 usuarios y tiendas ·
--           3 catálogo · 4 ofertas · 5 carrito · 6 pedidos y pagos
-- =====================================================================

BEGIN;

-- btree_gist permite la restricción EXCLUDE de precios (periodos que no se cruzan)
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------
-- 1. TERRITORIO E IMPUESTOS
-- ---------------------------------------------------------------------

-- 1. Ubigeo del INEI: departamentos (nivel 1), provincias (nivel 2) y
--    distritos (nivel 3) en una sola tabla. Cada fila apunta a su "padre".
CREATE TABLE divisiones (
    id_division     BIGSERIAL     PRIMARY KEY,
    nivel           SMALLINT      NOT NULL,                     -- 1 departamento · 2 provincia · 3 distrito
    id_padre        BIGINT        NULL,                         -- NULL solo en los departamentos
    nivel_padre     SMALLINT      GENERATED ALWAYS AS (nivel - 1) STORED,
    codigo_ubigeo   VARCHAR(6)    NOT NULL UNIQUE,              -- 15 · 1501 · 150122
    nombre          VARCHAR(100)  NOT NULL,
    CONSTRAINT uq_divisiones_nivel UNIQUE (id_division, nivel), -- para las FK compuestas
    -- el padre está exactamente un nivel arriba (FK compuesta):
    CONSTRAINT fk_divisiones_padre
        FOREIGN KEY (id_padre, nivel_padre) REFERENCES divisiones (id_division, nivel),
    CONSTRAINT ck_divisiones_nivel  CHECK (nivel BETWEEN 1 AND 3),
    CONSTRAINT ck_divisiones_padre  CHECK ((nivel = 1) = (id_padre IS NULL)),
    -- 2 dígitos por nivel: departamento 2, provincia 4, distrito 6
    CONSTRAINT ck_divisiones_codigo CHECK (codigo_ubigeo ~ '^[0-9]+$' AND length(codigo_ubigeo) = nivel * 2)
);

-- 2. Impuestos al consumo con su historial (IGV).
--    Una tasa no se edita: se cierra con vigente_hasta y se crea otra.
CREATE TABLE impuestos (
    id_impuesto    SMALLSERIAL   PRIMARY KEY,
    nombre         VARCHAR(20)   NOT NULL,                      -- IGV
    tasa           NUMERIC(5,2)  NOT NULL,                      -- 18.00 = 18 %
    vigente_desde  DATE          NOT NULL,
    vigente_hasta  DATE          NULL,                          -- NULL = tasa actual
    CONSTRAINT ck_impuestos_tasa   CHECK (tasa BETWEEN 0 AND 100),
    CONSTRAINT ck_impuestos_fechas CHECK (vigente_hasta IS NULL OR vigente_hasta > vigente_desde),
    -- los periodos de un mismo impuesto no se cruzan: en cada fecha rige una sola tasa
    CONSTRAINT ex_impuestos_periodo EXCLUDE USING gist (
        nombre WITH =,
        daterange(vigente_desde, vigente_hasta, '[)') WITH &&)
);

-- ---------------------------------------------------------------------
-- 2. USUARIOS Y TIENDAS
-- ---------------------------------------------------------------------

-- 3. Tipo de cuenta en la plataforma.
CREATE TABLE roles (
    id_rol       SMALLINT      PRIMARY KEY,                     -- 1 SUPERADMIN · 2 ADMIN_TIENDA · 3 CLIENTE
    nombre       VARCHAR(30)   NOT NULL UNIQUE,
    descripcion  VARCHAR(255)  NULL,
    estado       SMALLINT      NOT NULL DEFAULT 1,
    CONSTRAINT ck_roles_estado CHECK (estado IN (0, 1))
);

-- 4. Cuentas de acceso. Una cuenta de CLIENTE pertenece a una sola tienda
--    (id_tienda obligatorio); las cuentas del panel no tienen tienda.
--    Una misma persona puede tener varias filas (panel + cliente en cada
--    tienda) con el mismo correo; al iniciar sesión elige con cuál entrar.
CREATE TABLE login (
    id_login                BIGSERIAL     PRIMARY KEY,
    id_rol                  SMALLINT      NOT NULL REFERENCES roles (id_rol),
    id_tienda               BIGINT        NULL,                 -- FK se agrega después de crear tiendas
    usuario                 VARCHAR(50)   NOT NULL,
    correo                  VARCHAR(150)  NOT NULL,
    contrasena_hash         VARCHAR(255)  NOT NULL,             -- BCrypt, nunca la contraseña real
    nombres                 VARCHAR(80)   NOT NULL,
    apellido_pat            VARCHAR(80)   NOT NULL,
    apellido_mat            VARCHAR(80)   NULL,
    telefono                VARCHAR(20)   NULL,
    acepta_marketing        BOOLEAN       NOT NULL DEFAULT FALSE,
    estado                  SMALLINT      NOT NULL DEFAULT 1,   -- 1 activo · 0 inactivo
    contrasena_cambiada_en  TIMESTAMPTZ   NULL,
    fecha_creacion          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_modificacion      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- NULLS NOT DISTINCT: dos cuentas del panel (id_tienda NULL) tampoco
    -- pueden repetir correo ni usuario.
    CONSTRAINT uq_login_correo  UNIQUE NULLS NOT DISTINCT (id_tienda, correo),
    CONSTRAINT uq_login_usuario UNIQUE NULLS NOT DISTINCT (id_tienda, usuario),
    CONSTRAINT uq_login_rol     UNIQUE (id_login, id_rol),      -- para la FK de propietarios
    CONSTRAINT uq_login_tienda  UNIQUE (id_login, id_tienda),   -- para las FK de carritos y pedidos
    CONSTRAINT ck_login_cliente CHECK ((id_rol = 3) = (id_tienda IS NOT NULL)),
    CONSTRAINT ck_login_correo  CHECK (correo = lower(correo) AND correo LIKE '%_@_%._%'),
    CONSTRAINT ck_login_estado  CHECK (estado IN (0, 1))
);

-- 5. Datos comerciales del dueño de una o varias tiendas.
CREATE TABLE propietarios (
    id_propietario      BIGSERIAL     PRIMARY KEY,
    id_login            BIGINT        NOT NULL UNIQUE,
    id_rol              SMALLINT      NOT NULL DEFAULT 2,       -- siempre 2: solo una cuenta del panel es dueña
    tipo_documento      SMALLINT      NOT NULL,                 -- 1 DNI · 2 RUC · 3 carné de extranjería
    numero_documento    VARCHAR(12)   NOT NULL,
    razon_social        VARCHAR(150)  NULL,
    telefono_comercial  VARCHAR(20)   NULL,
    estado              SMALLINT      NOT NULL DEFAULT 1,
    fecha_creacion      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_modificacion  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_propietarios_login
        FOREIGN KEY (id_login, id_rol) REFERENCES login (id_login, id_rol),
    CONSTRAINT uq_propietarios_documento UNIQUE (tipo_documento, numero_documento),
    CONSTRAINT ck_propietarios_rol    CHECK (id_rol = 2),
    -- el número debe tener el formato de su tipo de documento
    CONSTRAINT ck_propietarios_documento CHECK (
           (tipo_documento = 1 AND numero_documento ~ '^[0-9]{8}$')                -- DNI: 8 dígitos
        OR (tipo_documento = 2 AND numero_documento ~ '^(10|15|17|20)[0-9]{9}$')   -- RUC: 11 dígitos
        OR (tipo_documento = 3 AND numero_documento ~ '^[0-9]{9}$')),              -- CE: 9 dígitos
    CONSTRAINT ck_propietarios_estado CHECK (estado IN (0, 1))
);

-- 6. Cada tienda (tenant).
CREATE TABLE tiendas (
    id_tienda             BIGSERIAL     PRIMARY KEY,
    id_propietario        BIGINT        NOT NULL REFERENCES propietarios (id_propietario),
    nombre                VARCHAR(150)  NOT NULL,
    subdominio            VARCHAR(63)   NOT NULL UNIQUE,        -- zapatillas-carlos.vendely.pe
    descripcion           TEXT          NULL,
    correo_contacto       VARCHAR(150)  NOT NULL,
    logo_url              VARCHAR(500)  NULL,
    color_principal       VARCHAR(7)    NULL,                   -- #1A2B3C
    permite_invitados     BOOLEAN       NOT NULL DEFAULT TRUE,  -- comprar sin crear cuenta
    ultimo_numero_pedido  INTEGER       NOT NULL DEFAULT 1000,  -- el primer pedido será #1001
    estado                SMALLINT      NOT NULL DEFAULT 1,     -- 1 activa · 0 suspendida
    fecha_creacion        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_modificacion    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_tiendas_subdominio CHECK (subdominio ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$'),
    CONSTRAINT ck_tiendas_color      CHECK (color_principal IS NULL OR color_principal ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_tiendas_numero     CHECK (ultimo_numero_pedido >= 1000),
    CONSTRAINT ck_tiendas_estado     CHECK (estado IN (0, 1))
);

ALTER TABLE login
    ADD CONSTRAINT fk_login_tienda FOREIGN KEY (id_tienda) REFERENCES tiendas (id_tienda);

-- 7. Direcciones de envío y facturación. id_login NULL = compra como invitado.
CREATE TABLE direcciones (
    id_direccion    BIGSERIAL     PRIMARY KEY,
    id_login        BIGINT        NULL REFERENCES login (id_login),
    id_distrito     BIGINT        NOT NULL,
    nivel_distrito  SMALLINT      NOT NULL DEFAULT 3,           -- siempre 3: la dirección apunta a un distrito
    destinatario    VARCHAR(160)  NOT NULL,
    telefono        VARCHAR(20)   NOT NULL,
    direccion       VARCHAR(200)  NOT NULL,                     -- calle, número, interior
    referencia      VARCHAR(200)  NULL,
    codigo_postal   VARCHAR(12)   NULL,
    predeterminada  BOOLEAN       NOT NULL DEFAULT FALSE,
    estado          SMALLINT      NOT NULL DEFAULT 1,           -- 1 activa · 0 eliminada (se conserva por los pedidos)
    fecha_creacion  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- solo se puede elegir un DISTRITO (FK compuesta):
    CONSTRAINT fk_direcciones_distrito
        FOREIGN KEY (id_distrito, nivel_distrito) REFERENCES divisiones (id_division, nivel),
    CONSTRAINT ck_direcciones_nivel  CHECK (nivel_distrito = 3),
    CONSTRAINT ck_direcciones_estado CHECK (estado IN (0, 1))
);

-- 8. Costo de envío de cada tienda a cada departamento.
CREATE TABLE tarifas_envio (
    id_tienda           BIGINT        NOT NULL REFERENCES tiendas (id_tienda) ON DELETE CASCADE,
    id_departamento     BIGINT        NOT NULL,
    nivel_departamento  SMALLINT      NOT NULL DEFAULT 1,       -- siempre 1: la tarifa es por departamento
    costo               NUMERIC(12,2) NOT NULL,
    envio_gratis_desde  NUMERIC(12,2) NULL,                     -- NULL = nunca es gratis
    dias_entrega_min    SMALLINT      NULL,
    dias_entrega_max    SMALLINT      NULL,
    estado              SMALLINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (id_tienda, id_departamento),                   -- llave compuesta
    -- solo se puede elegir un DEPARTAMENTO (FK compuesta):
    CONSTRAINT fk_tarifas_departamento
        FOREIGN KEY (id_departamento, nivel_departamento) REFERENCES divisiones (id_division, nivel),
    CONSTRAINT ck_tarifas_nivel  CHECK (nivel_departamento = 1),
    CONSTRAINT ck_tarifas_costo  CHECK (costo >= 0),
    CONSTRAINT ck_tarifas_gratis CHECK (envio_gratis_desde IS NULL OR envio_gratis_desde > 0),
    CONSTRAINT ck_tarifas_dias   CHECK (dias_entrega_min IS NULL OR dias_entrega_max IS NULL
                                        OR dias_entrega_min <= dias_entrega_max),
    CONSTRAINT ck_tarifas_estado CHECK (estado IN (0, 1))
);

-- ---------------------------------------------------------------------
-- 3. CATÁLOGO
-- ---------------------------------------------------------------------

-- 9. Categorías (colecciones) de cada tienda.
CREATE TABLE categorias (
    id_categoria    BIGSERIAL     PRIMARY KEY,
    id_tienda       BIGINT        NOT NULL REFERENCES tiendas (id_tienda) ON DELETE CASCADE,
    nombre          VARCHAR(100)  NOT NULL,
    slug            VARCHAR(120)  NOT NULL,                     -- texto para la URL: zapatillas-urbanas
    descripcion     TEXT          NULL,
    estado          SMALLINT      NOT NULL DEFAULT 1,
    fecha_creacion  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_categorias_slug   UNIQUE (id_tienda, slug),
    CONSTRAINT ck_categorias_slug   CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_categorias_estado CHECK (estado IN (0, 1))
);

-- 10. Ficha general de cada producto (precio y stock van en la variante).
CREATE TABLE productos (
    id_producto         BIGSERIAL     PRIMARY KEY,
    id_tienda           BIGINT        NOT NULL REFERENCES tiendas (id_tienda) ON DELETE CASCADE,
    nombre              VARCHAR(200)  NOT NULL,
    slug                VARCHAR(220)  NOT NULL,
    descripcion         TEXT          NULL,
    marca               VARCHAR(100)  NULL,
    estado              SMALLINT      NOT NULL DEFAULT 0,       -- 0 borrador · 1 activo · 2 archivado
    fecha_creacion      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_modificacion  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_productos_slug   UNIQUE (id_tienda, slug),
    CONSTRAINT ck_productos_slug   CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_productos_estado CHECK (estado IN (0, 1, 2))
);

-- 11. Un producto puede estar en varias categorías y una categoría
--     tiene muchos productos (muchos a muchos).
CREATE TABLE producto_categorias (
    id_producto   BIGINT  NOT NULL REFERENCES productos (id_producto) ON DELETE CASCADE,
    id_categoria  BIGINT  NOT NULL REFERENCES categorias (id_categoria) ON DELETE CASCADE,
    PRIMARY KEY (id_producto, id_categoria)                     -- llave compuesta
);

-- 12. Opciones de un producto: "Talla", "Color", "Almacenamiento"… (máx. 3).
CREATE TABLE producto_opciones (
    id_producto  BIGINT       NOT NULL REFERENCES productos (id_producto) ON DELETE CASCADE,
    posicion     SMALLINT     NOT NULL,
    nombre       VARCHAR(50)  NOT NULL,
    PRIMARY KEY (id_producto, posicion),                        -- llave compuesta
    CONSTRAINT uq_producto_opciones_nombre UNIQUE (id_producto, nombre),
    CONSTRAINT ck_producto_opciones_pos    CHECK (posicion BETWEEN 1 AND 3)
);

-- 13. Cada combinación vendible de un producto (42 / Negro).
CREATE TABLE variantes (
    id_variante     BIGSERIAL     PRIMARY KEY,
    id_producto     BIGINT        NOT NULL REFERENCES productos (id_producto) ON DELETE CASCADE,
    sku             VARCHAR(60)   NULL,
    posicion        SMALLINT      NOT NULL DEFAULT 1,           -- orden en la ficha del producto
    estado          SMALLINT      NOT NULL DEFAULT 1,           -- 1 activa · 0 inactiva
    fecha_creacion  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_variantes_producto UNIQUE (id_producto, id_variante),  -- para la FK de variante_valores
    CONSTRAINT ck_variantes_posicion CHECK (posicion >= 1),
    CONSTRAINT ck_variantes_estado   CHECK (estado IN (0, 1))
);

-- 14. Valor de cada opción en cada variante: (variante 1, opción 1, "42").
CREATE TABLE variante_valores (
    id_variante  BIGINT       NOT NULL,
    id_producto  BIGINT       NOT NULL,
    posicion     SMALLINT     NOT NULL,
    valor        VARCHAR(50)  NOT NULL,
    PRIMARY KEY (id_variante, posicion),                        -- llave compuesta
    -- la variante es de ese producto…
    CONSTRAINT fk_variante_valores_variante
        FOREIGN KEY (id_producto, id_variante) REFERENCES variantes (id_producto, id_variante)
        ON DELETE CASCADE,
    -- …y la opción existe en ese mismo producto
    CONSTRAINT fk_variante_valores_opcion
        FOREIGN KEY (id_producto, posicion) REFERENCES producto_opciones (id_producto, posicion)
        ON DELETE CASCADE
);

-- 15. Fotos de cada producto.
CREATE TABLE imagenes_producto (
    id_imagen          BIGSERIAL     PRIMARY KEY,
    id_producto        BIGINT        NOT NULL REFERENCES productos (id_producto) ON DELETE CASCADE,
    url                VARCHAR(500)  NOT NULL,
    texto_alternativo  VARCHAR(200)  NULL,
    posicion           SMALLINT      NOT NULL DEFAULT 1,
    CONSTRAINT uq_imagenes_posicion UNIQUE (id_producto, posicion),
    CONSTRAINT ck_imagenes_posicion CHECK (posicion >= 1)
);

-- 16. Historial de precios. Un precio NO se edita: se cierra (fecha_fin)
--     y se crea otro; así los pedidos siempre apuntan al precio cobrado.
CREATE TABLE precios (
    id_precio          BIGSERIAL      PRIMARY KEY,
    id_variante        BIGINT         NOT NULL REFERENCES variantes (id_variante) ON DELETE CASCADE,
    monto              NUMERIC(12,2)  NOT NULL,                 -- en soles, con IGV incluido
    monto_comparacion  NUMERIC(12,2)  NULL,                     -- precio "antes" (tachado)
    fecha_inicio       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    fecha_fin          TIMESTAMPTZ    NULL,                     -- NULL = vigente
    CONSTRAINT uq_precios_variante  UNIQUE (id_variante, id_precio),  -- para la FK de detalle_pedido
    CONSTRAINT ck_precios_monto     CHECK (monto >= 0),
    CONSTRAINT ck_precios_comparar  CHECK (monto_comparacion IS NULL OR monto_comparacion > monto),
    CONSTRAINT ck_precios_fechas    CHECK (fecha_fin IS NULL OR fecha_fin > fecha_inicio),
    -- los periodos de precio de una misma variante no se cruzan
    CONSTRAINT ex_precios_periodo   EXCLUDE USING gist (
        id_variante WITH =,
        tstzrange(fecha_inicio, fecha_fin, '[)') WITH &&)
);

-- 17. Stock de cada variante (relación 1 a 1: la llave es la variante).
CREATE TABLE inventario (
    id_variante              BIGINT       PRIMARY KEY REFERENCES variantes (id_variante) ON DELETE CASCADE,
    cantidad_disponible      INTEGER      NOT NULL DEFAULT 0,
    stock_minimo             INTEGER      NOT NULL DEFAULT 0,   -- alerta de stock bajo
    fecha_ultima_reposicion  TIMESTAMPTZ  NULL,
    fecha_modificacion       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_inventario_cantidad CHECK (cantidad_disponible >= 0),
    CONSTRAINT ck_inventario_minimo   CHECK (stock_minimo >= 0)
);

-- ---------------------------------------------------------------------
-- 4. OFERTAS
-- ---------------------------------------------------------------------

-- 18. Promociones de la tienda.
CREATE TABLE ofertas (
    id_oferta       BIGSERIAL      PRIMARY KEY,
    id_tienda       BIGINT         NOT NULL REFERENCES tiendas (id_tienda) ON DELETE CASCADE,
    nombre          VARCHAR(120)   NOT NULL,
    motivo          SMALLINT       NOT NULL,                    -- 1 liquidación · 2 temporada · 3 lanzamiento · 4 otro
    tipo_descuento  SMALLINT       NOT NULL,                    -- 1 porcentaje · 2 monto fijo
    valor           NUMERIC(12,2)  NOT NULL,
    fecha_inicio    TIMESTAMPTZ    NOT NULL,
    fecha_fin       TIMESTAMPTZ    NULL,
    estado          SMALLINT       NOT NULL DEFAULT 1,
    fecha_creacion  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_ofertas_motivo CHECK (motivo BETWEEN 1 AND 4),
    CONSTRAINT ck_ofertas_tipo   CHECK (tipo_descuento IN (1, 2)),
    CONSTRAINT ck_ofertas_valor  CHECK (valor > 0 AND (tipo_descuento = 2 OR valor <= 100)),
    CONSTRAINT ck_ofertas_fechas CHECK (fecha_fin IS NULL OR fecha_fin > fecha_inicio),
    CONSTRAINT ck_ofertas_estado CHECK (estado IN (0, 1))
);

-- 19. Qué productos entran en cada oferta (tabla puente).
CREATE TABLE oferta_productos (
    id_oferta    BIGINT  NOT NULL REFERENCES ofertas (id_oferta) ON DELETE CASCADE,
    id_producto  BIGINT  NOT NULL REFERENCES productos (id_producto) ON DELETE CASCADE,
    PRIMARY KEY (id_oferta, id_producto)                        -- llave compuesta
);

-- ---------------------------------------------------------------------
-- 5. CARRITO
-- ---------------------------------------------------------------------

-- 20. Carrito de un cliente o de un visitante.
CREATE TABLE carritos (
    id_carrito          BIGSERIAL     PRIMARY KEY,
    id_tienda           BIGINT        NOT NULL REFERENCES tiendas (id_tienda) ON DELETE CASCADE,
    id_login            BIGINT        NULL UNIQUE,              -- NULL = visitante (se identifica con token)
    token               VARCHAR(64)   NOT NULL UNIQUE,
    fecha_creacion      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_modificacion  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- el cliente debe ser de ESTA tienda (FK compuesta):
    CONSTRAINT fk_carritos_login
        FOREIGN KEY (id_login, id_tienda) REFERENCES login (id_login, id_tienda)
);

-- 21. Lo que hay en cada carrito (tabla puente carrito–variante).
CREATE TABLE items_carrito (
    id_carrito      BIGINT       NOT NULL REFERENCES carritos (id_carrito) ON DELETE CASCADE,
    id_variante     BIGINT       NOT NULL REFERENCES variantes (id_variante) ON DELETE CASCADE,
    cantidad        INTEGER      NOT NULL,
    fecha_agregado  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id_carrito, id_variante),                      -- llave compuesta
    CONSTRAINT ck_items_carrito_cantidad CHECK (cantidad > 0)
);

-- ---------------------------------------------------------------------
-- 6. PEDIDOS Y PAGOS
-- ---------------------------------------------------------------------

-- 22. Cada compra. Los montos son una "foto" de la venta.
CREATE TABLE pedidos (
    id_pedido                 BIGSERIAL      PRIMARY KEY,
    id_tienda                 BIGINT         NOT NULL REFERENCES tiendas (id_tienda),
    numero_pedido             INTEGER        NOT NULL,          -- lo asigna el trigger: 1001, 1002…
    id_login                  BIGINT         NULL,              -- NULL = compra como invitado
    id_direccion_envio        BIGINT         NOT NULL REFERENCES direcciones (id_direccion),
    id_direccion_facturacion  BIGINT         NULL REFERENCES direcciones (id_direccion),
    id_impuesto               SMALLINT       NOT NULL REFERENCES impuestos (id_impuesto),  -- IGV aplicado
    correo_contacto           VARCHAR(150)   NOT NULL,
    subtotal                  NUMERIC(12,2)  NOT NULL,          -- suma de las líneas, ya con descuentos
    costo_envio               NUMERIC(12,2)  NOT NULL,          -- si no se envía, el trigger lo toma de la tarifa
    monto_impuestos           NUMERIC(12,2)  NOT NULL,          -- IGV incluido en el total (lo valida el trigger)
    total                     NUMERIC(12,2)  NOT NULL,          -- si no se envía, el trigger lo calcula
    estado                    SMALLINT       NOT NULL DEFAULT 0,  -- 0 sin enviar · 1 enviado · 2 entregado · 3 cancelado
    notas                     TEXT           NULL,
    fecha_creacion            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    fecha_modificacion        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- el cliente debe ser de ESTA tienda (FK compuesta):
    CONSTRAINT fk_pedidos_login
        FOREIGN KEY (id_login, id_tienda) REFERENCES login (id_login, id_tienda),
    CONSTRAINT uq_pedidos_numero   UNIQUE (id_tienda, numero_pedido),   -- como serie + número
    CONSTRAINT ck_pedidos_montos   CHECK (subtotal >= 0 AND costo_envio >= 0 AND monto_impuestos >= 0),
    CONSTRAINT ck_pedidos_total    CHECK (total = subtotal + costo_envio),  -- precios con IGV incluido
    CONSTRAINT ck_pedidos_impuesto CHECK (monto_impuestos <= total),
    CONSTRAINT ck_pedidos_estado   CHECK (estado BETWEEN 0 AND 3)
);

-- 23. Líneas del pedido (tabla puente pedido–variante).
CREATE TABLE detalle_pedido (
    id_pedido           BIGINT         NOT NULL REFERENCES pedidos (id_pedido) ON DELETE CASCADE,
    id_variante         BIGINT         NOT NULL REFERENCES variantes (id_variante),
    id_precio           BIGINT         NOT NULL,
    id_oferta           BIGINT         NULL REFERENCES ofertas (id_oferta),
    nombre_producto     VARCHAR(200)   NOT NULL,                -- foto histórica del nombre
    nombre_variante     VARCHAR(110)   NOT NULL,                -- "42 / Negro"
    cantidad            INTEGER        NOT NULL,
    descuento_unitario  NUMERIC(12,2)  NOT NULL DEFAULT 0,
    PRIMARY KEY (id_pedido, id_variante),                       -- llave compuesta
    -- el precio cobrado debe ser de ESA variante (FK compuesta):
    CONSTRAINT fk_detalle_precio
        FOREIGN KEY (id_variante, id_precio) REFERENCES precios (id_variante, id_precio),
    CONSTRAINT ck_detalle_cantidad  CHECK (cantidad > 0),
    CONSTRAINT ck_detalle_descuento CHECK (descuento_unitario >= 0),
    CONSTRAINT ck_detalle_sin_oferta CHECK (id_oferta IS NOT NULL OR descuento_unitario = 0)  -- sin oferta, sin descuento
);

-- 24. Intentos de pago de cada pedido.
CREATE TABLE pagos (
    id_pago                 BIGSERIAL      PRIMARY KEY,
    id_pedido               BIGINT         NOT NULL REFERENCES pedidos (id_pedido),
    proveedor               SMALLINT       NOT NULL,            -- 1 Culqi · 2 PayPal · 3 Mercado Pago · 4 manual (Yape, transferencia)
    monto                   NUMERIC(12,2)  NOT NULL,
    estado                  SMALLINT       NOT NULL DEFAULT 0,  -- 0 pendiente · 1 aprobado · 2 rechazado · 3 reembolsado
    referencia_transaccion  VARCHAR(120)   NULL UNIQUE,         -- código que devuelve la pasarela
    fecha_creacion          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    fecha_pago              TIMESTAMPTZ    NULL,
    CONSTRAINT ck_pagos_proveedor CHECK (proveedor BETWEEN 1 AND 4),
    CONSTRAINT ck_pagos_monto     CHECK (monto > 0),
    CONSTRAINT ck_pagos_estado    CHECK (estado BETWEEN 0 AND 3),
    CONSTRAINT ck_pagos_fecha     CHECK (estado NOT IN (1, 3) OR fecha_pago IS NOT NULL)
);

-- ---------------------------------------------------------------------
-- ÍNDICES
--   Las PK y los UNIQUE ya crean su índice. PostgreSQL NO indexa las FK
--   automáticamente: se agregan las que la app consulta seguido.
-- ---------------------------------------------------------------------

-- Reglas de negocio con índice único parcial
CREATE UNIQUE INDEX uq_impuesto_vigente  ON impuestos (nombre) WHERE vigente_hasta IS NULL;
CREATE UNIQUE INDEX uq_precio_vigente    ON precios (id_variante) WHERE fecha_fin IS NULL;
CREATE UNIQUE INDEX uq_direccion_predet  ON direcciones (id_login) WHERE predeterminada AND estado = 1;
CREATE UNIQUE INDEX uq_pago_aprobado     ON pagos (id_pedido) WHERE estado = 1;

-- Búsquedas frecuentes
CREATE INDEX ix_divisiones_padre         ON divisiones (id_padre);
CREATE INDEX ix_login_correo             ON login (correo);           -- elegir cuenta al iniciar sesión
CREATE INDEX ix_login_rol                ON login (id_rol);
CREATE INDEX ix_tiendas_propietario      ON tiendas (id_propietario);
CREATE INDEX ix_direcciones_login        ON direcciones (id_login);
CREATE INDEX ix_direcciones_distrito     ON direcciones (id_distrito);
CREATE INDEX ix_productos_tienda_estado  ON productos (id_tienda, estado);
CREATE INDEX ix_producto_categorias_cat  ON producto_categorias (id_categoria);
CREATE INDEX ix_variantes_producto       ON variantes (id_producto);
CREATE INDEX ix_imagenes_producto        ON imagenes_producto (id_producto);
CREATE INDEX ix_ofertas_tienda           ON ofertas (id_tienda);
CREATE INDEX ix_oferta_productos_prod    ON oferta_productos (id_producto);
CREATE INDEX ix_carritos_tienda          ON carritos (id_tienda);
CREATE INDEX ix_items_carrito_variante   ON items_carrito (id_variante);
CREATE INDEX ix_pedidos_tienda_fecha     ON pedidos (id_tienda, fecha_creacion DESC);
CREATE INDEX ix_pedidos_login            ON pedidos (id_login);
CREATE INDEX ix_detalle_variante         ON detalle_pedido (id_variante);
CREATE INDEX ix_pagos_pedido             ON pagos (id_pedido);
-- Índices para las llaves foráneas que no tenían uno (PostgreSQL no los crea solo)
CREATE INDEX ix_tarifas_departamento     ON tarifas_envio (id_departamento);
CREATE INDEX ix_variante_valores_opcion  ON variante_valores (id_producto, posicion);
CREATE INDEX ix_pedidos_dir_envio        ON pedidos (id_direccion_envio);
CREATE INDEX ix_pedidos_dir_facturacion  ON pedidos (id_direccion_facturacion);
CREATE INDEX ix_pedidos_impuesto         ON pedidos (id_impuesto);
CREATE INDEX ix_detalle_precio           ON detalle_pedido (id_variante, id_precio);
CREATE INDEX ix_detalle_oferta           ON detalle_pedido (id_oferta);

-- ---------------------------------------------------------------------
-- TRIGGERS (solo lo que un CHECK o una FK no pueden hacer)
-- ---------------------------------------------------------------------

-- T1. fecha_modificacion se actualiza sola
CREATE FUNCTION fn_fecha_modificacion() RETURNS trigger AS $$
BEGIN
    NEW.fecha_modificacion := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_login_fecha        BEFORE UPDATE ON login        FOR EACH ROW EXECUTE FUNCTION fn_fecha_modificacion();
CREATE TRIGGER tg_propietarios_fecha BEFORE UPDATE ON propietarios FOR EACH ROW EXECUTE FUNCTION fn_fecha_modificacion();
-- En tiendas, el contador de pedidos (T2) no cuenta como "modificar la tienda"
CREATE TRIGGER tg_tiendas_fecha      BEFORE UPDATE ON tiendas      FOR EACH ROW
    WHEN (OLD.ultimo_numero_pedido IS NOT DISTINCT FROM NEW.ultimo_numero_pedido)
    EXECUTE FUNCTION fn_fecha_modificacion();
CREATE TRIGGER tg_productos_fecha    BEFORE UPDATE ON productos    FOR EACH ROW EXECUTE FUNCTION fn_fecha_modificacion();
CREATE TRIGGER tg_inventario_fecha   BEFORE UPDATE ON inventario   FOR EACH ROW EXECUTE FUNCTION fn_fecha_modificacion();
CREATE TRIGGER tg_carritos_fecha     BEFORE UPDATE ON carritos     FOR EACH ROW EXECUTE FUNCTION fn_fecha_modificacion();
CREATE TRIGGER tg_pedidos_fecha      BEFORE UPDATE ON pedidos      FOR EACH ROW EXECUTE FUNCTION fn_fecha_modificacion();

-- T2. Reglas del pedido (al crearlo, y al cambiar su dirección o sus montos)
--     · La tienda debe estar activa (no suspendida).
--     · Un pedido con un pago aprobado ya no cambia sus montos.
--     · Recibe el siguiente número de su tienda (#1001, #1002…). El UPDATE
--       bloquea la fila de la tienda: dos pedidos simultáneos esperan su
--       turno y nunca reciben el mismo número.
--     · Si el backend no indica el IGV, se usa la tasa vigente en la fecha del
--       pedido (no la última creada: un cambio de IGV se registra antes de
--       que empiece a regir). Si lo indica, debe estar vigente en esa fecha.
--       La fecha se toma en hora de Lima: un pedido de las 11 p. m. del 31 de
--       diciembre es del 31, aunque en UTC ya sea 1 de enero.
--     · Las direcciones deben ser del mismo cliente del pedido (o de un
--       invitado de esa misma tienda) y estar activas.
--     · La tienda debe tener una tarifa activa para el departamento de la
--       dirección de envío, y costo_envio debe coincidir con esa tarifa
--       (0 si el subtotal llega al monto de envío gratis).
--     · total y monto_impuestos se calculan si no se envían, y si se envían
--       deben cuadrar: IGV incluido = total × tasa / (100 + tasa).
CREATE FUNCTION fn_validar_direccion_pedido(p_direccion BIGINT, p_login BIGINT,
                                            p_tienda BIGINT, p_pedido BIGINT) RETURNS VOID AS $$
DECLARE d RECORD;
BEGIN
    IF p_direccion IS NULL THEN RETURN; END IF;
    SELECT id_login, estado INTO d FROM direcciones WHERE id_direccion = p_direccion;
    IF NOT FOUND THEN RETURN; END IF;                 -- la FK se encarga
    IF d.id_login IS NOT NULL AND d.id_login IS DISTINCT FROM p_login THEN
        RAISE EXCEPTION 'La dirección % pertenece a otro cliente', p_direccion;
    END IF;
    IF d.id_login IS NULL AND EXISTS (
        SELECT 1 FROM pedidos p
         WHERE p.id_tienda <> p_tienda AND p.id_pedido IS DISTINCT FROM p_pedido
           AND p_direccion IN (p.id_direccion_envio, p.id_direccion_facturacion)) THEN
        RAISE EXCEPTION 'La dirección de invitado % ya se usó en otra tienda', p_direccion;
    END IF;
    IF d.estado <> 1 THEN
        RAISE EXCEPTION 'La dirección % está eliminada', p_direccion;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION fn_preparar_pedido() RETURNS trigger AS $$
DECLARE
    v_nuevo    BOOLEAN := (TG_OP = 'INSERT');
    v_envio    BOOLEAN;   -- ¿hay que revisar la tarifa?
    v_montos   BOOLEAN;   -- ¿hay que revisar total e IGV?
    v_dep      BIGINT;
    v_tarifa   RECORD;
    v_costo    NUMERIC(12,2);
    v_tasa     NUMERIC(5,2);
    v_igv      NUMERIC(12,2);
BEGIN
    IF v_nuevo THEN
        IF (SELECT estado FROM tiendas WHERE id_tienda = NEW.id_tienda) <> 1 THEN
            RAISE EXCEPTION 'La tienda % está suspendida y no puede recibir pedidos', NEW.id_tienda;
        END IF;

        UPDATE tiendas
           SET ultimo_numero_pedido = ultimo_numero_pedido + 1
         WHERE id_tienda = NEW.id_tienda
        RETURNING ultimo_numero_pedido INTO NEW.numero_pedido;

        IF NEW.id_impuesto IS NULL THEN
            SELECT id_impuesto INTO NEW.id_impuesto
              FROM impuestos
             WHERE nombre = 'IGV'
               AND vigente_desde <= (NEW.fecha_creacion AT TIME ZONE 'America/Lima')::date
               AND (vigente_hasta IS NULL OR vigente_hasta > (NEW.fecha_creacion AT TIME ZONE 'America/Lima')::date);
            IF NOT FOUND THEN
                RAISE EXCEPTION 'No hay una tasa de IGV vigente para el %', (NEW.fecha_creacion AT TIME ZONE 'America/Lima')::date;
            END IF;
        END IF;
    END IF;

    -- La tasa aplicada debe estar vigente en la fecha del pedido
    IF v_nuevo OR (NEW.id_impuesto, NEW.fecha_creacion) IS DISTINCT FROM (OLD.id_impuesto, OLD.fecha_creacion) THEN
        IF NOT EXISTS (SELECT 1 FROM impuestos
                        WHERE id_impuesto = NEW.id_impuesto
                          AND vigente_desde <= (NEW.fecha_creacion AT TIME ZONE 'America/Lima')::date
                          AND (vigente_hasta IS NULL OR vigente_hasta > (NEW.fecha_creacion AT TIME ZONE 'America/Lima')::date)) THEN
            RAISE EXCEPTION 'La tasa % no estaba vigente el %', NEW.id_impuesto, (NEW.fecha_creacion AT TIME ZONE 'America/Lima')::date;
        END IF;
    END IF;

    -- Al editar un pedido solo se revisa lo que cambió: si la tienda subió
    -- su tarifa después, cambiar las notas o la facturación de un pedido
    -- antiguo no debe fallar.
    v_envio  := v_nuevo OR (NEW.id_direccion_envio, NEW.subtotal, NEW.costo_envio)
                           IS DISTINCT FROM (OLD.id_direccion_envio, OLD.subtotal, OLD.costo_envio);
    v_montos := v_envio OR (NEW.total, NEW.monto_impuestos, NEW.id_impuesto)
                           IS DISTINCT FROM (OLD.total, OLD.monto_impuestos, OLD.id_impuesto);

    -- Un pedido ya pagado no cambia sus montos (el cobro quedaría distinto del total)
    IF NOT v_nuevo AND (NEW.subtotal, NEW.costo_envio, NEW.total, NEW.monto_impuestos, NEW.id_impuesto)
                       IS DISTINCT FROM (OLD.subtotal, OLD.costo_envio, OLD.total, OLD.monto_impuestos, OLD.id_impuesto)
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

    -- Tarifa de envío: distrito → provincia → departamento
    IF v_envio THEN
        SELECT pro.id_padre INTO v_dep
          FROM direcciones d
          JOIN divisiones dis ON dis.id_division = d.id_distrito
          JOIN divisiones pro ON pro.id_division = dis.id_padre
         WHERE d.id_direccion = NEW.id_direccion_envio;

        SELECT costo, envio_gratis_desde INTO v_tarifa
          FROM tarifas_envio
         WHERE id_tienda = NEW.id_tienda AND id_departamento = v_dep AND estado = 1;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'La tienda % no tiene una tarifa de envío activa para ese departamento', NEW.id_tienda;
        END IF;

        v_costo := CASE WHEN v_tarifa.envio_gratis_desde IS NOT NULL
                             AND NEW.subtotal >= v_tarifa.envio_gratis_desde
                        THEN 0 ELSE v_tarifa.costo END;
        IF NEW.costo_envio IS NULL THEN
            NEW.costo_envio := v_costo;
        ELSIF NEW.costo_envio <> v_costo THEN
            RAISE EXCEPTION 'El costo de envío debe ser S/ % según la tarifa de la tienda', v_costo;
        END IF;
    END IF;

    -- Total e IGV incluido (con la tasa de la tabla impuestos)
    IF v_montos THEN
        IF NEW.total IS NULL THEN
            NEW.total := NEW.subtotal + NEW.costo_envio;
        END IF;
        SELECT tasa INTO v_tasa FROM impuestos WHERE id_impuesto = NEW.id_impuesto;
        v_igv := round(NEW.total * v_tasa / (100 + v_tasa), 2);
        IF NEW.monto_impuestos IS NULL THEN
            NEW.monto_impuestos := v_igv;
        ELSIF NEW.monto_impuestos <> v_igv THEN
            RAISE EXCEPTION 'El IGV incluido debe ser S/ % (total × % / %)', v_igv, v_tasa, 100 + v_tasa;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_pedidos_preparar
    BEFORE INSERT OR UPDATE ON pedidos
    FOR EACH ROW EXECUTE FUNCTION fn_preparar_pedido();

-- T3. Un precio ya creado no se modifica (ni el monto ni el precio tachado):
--     solo se cierra una vez con fecha_fin,
--     y nunca antes de una venta que ya lo usó.
CREATE FUNCTION fn_precio_inmutable() RETURNS trigger AS $$
BEGIN
    IF NEW.monto IS DISTINCT FROM OLD.monto
       OR NEW.monto_comparacion IS DISTINCT FROM OLD.monto_comparacion
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

CREATE TRIGGER tg_precios_inmutable
    BEFORE UPDATE ON precios
    FOR EACH ROW EXECUTE FUNCTION fn_precio_inmutable();

-- T4. Una tasa de impuesto ya creada no se modifica: solo se cierra una vez con
--     vigente_hasta, y nunca antes de un pedido que ya la usó.
CREATE FUNCTION fn_impuesto_inmutable() RETURNS trigger AS $$
BEGIN
    IF NEW.tasa IS DISTINCT FROM OLD.tasa
       OR NEW.nombre IS DISTINCT FROM OLD.nombre
       OR NEW.vigente_desde IS DISTINCT FROM OLD.vigente_desde THEN
        RAISE EXCEPTION 'Una tasa no se edita: ciérrala con vigente_hasta y crea una nueva';
    END IF;
    -- vigente_hasta solo pasa de vacía a una fecha (cerrar); después no cambia
    IF OLD.vigente_hasta IS NOT NULL AND NEW.vigente_hasta IS DISTINCT FROM OLD.vigente_hasta THEN
        RAISE EXCEPTION 'La tasa % ya está cerrada: su periodo no se cambia', OLD.id_impuesto;
    END IF;
    -- y no puede cerrarse antes de un pedido que ya la usó
    IF OLD.vigente_hasta IS NULL AND NEW.vigente_hasta IS NOT NULL AND EXISTS (
        SELECT 1 FROM pedidos
         WHERE id_impuesto = NEW.id_impuesto AND (fecha_creacion AT TIME ZONE 'America/Lima')::date >= NEW.vigente_hasta) THEN
        RAISE EXCEPTION 'La tasa % se usó en un pedido posterior a esa fecha de cierre', NEW.id_impuesto;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_impuestos_inmutable
    BEFORE UPDATE ON impuestos
    FOR EACH ROW EXECUTE FUNCTION fn_impuesto_inmutable();

-- T5. Variantes: cada variante tiene un valor para cada opción de su
--     producto, y dos variantes del mismo producto no pueden tener la misma
--     combinación (dos "42 / Negro"). Se revisa al confirmar la transacción
--     (DEFERRABLE), porque una variante y sus valores se guardan en varios INSERT.
CREATE FUNCTION fn_revisar_variante(p_variante BIGINT) RETURNS VOID AS $$
DECLARE
    v_producto BIGINT;
    v_opciones INTEGER;
    v_valores  INTEGER;
    v_combo    TEXT;
BEGIN
    SELECT id_producto INTO v_producto FROM variantes WHERE id_variante = p_variante;
    IF NOT FOUND THEN RETURN; END IF;                 -- la variante se eliminó

    SELECT count(*) INTO v_opciones FROM producto_opciones WHERE id_producto = v_producto;
    SELECT count(*), string_agg(posicion || '=' || lower(trim(valor)), ' / ' ORDER BY posicion)
      INTO v_valores, v_combo
      FROM variante_valores WHERE id_variante = p_variante;

    IF v_valores <> v_opciones THEN
        RAISE EXCEPTION 'La variante % tiene % valores, pero su producto tiene % opciones', p_variante, v_valores, v_opciones;
    END IF;

    IF EXISTS (
        SELECT 1 FROM variantes v
         WHERE v.id_producto = v_producto AND v.id_variante <> p_variante
           AND COALESCE((SELECT string_agg(vv.posicion || '=' || lower(trim(vv.valor)), ' / ' ORDER BY vv.posicion)
                           FROM variante_valores vv WHERE vv.id_variante = v.id_variante), '')
               = COALESCE(v_combo, '')) THEN
        RAISE EXCEPTION 'Ya existe otra variante del producto % con la combinación (%)', v_producto, COALESCE(v_combo, 'sin opciones');
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION fn_tg_revisar_variante() RETURNS trigger AS $$
DECLARE r RECORD;
BEGIN
    IF TG_TABLE_NAME = 'producto_opciones' THEN
        FOR r IN SELECT id_variante FROM variantes
                  WHERE id_producto = COALESCE(NEW.id_producto, OLD.id_producto) LOOP
            PERFORM fn_revisar_variante(r.id_variante);
        END LOOP;
    ELSE
        IF TG_OP IN ('INSERT', 'UPDATE') THEN PERFORM fn_revisar_variante(NEW.id_variante); END IF;
        IF TG_OP IN ('UPDATE', 'DELETE') THEN PERFORM fn_revisar_variante(OLD.id_variante); END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER tg_variantes_combinacion
    AFTER INSERT ON variantes DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION fn_tg_revisar_variante();
CREATE CONSTRAINT TRIGGER tg_variante_valores_combinacion
    AFTER INSERT OR UPDATE OR DELETE ON variante_valores DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION fn_tg_revisar_variante();
CREATE CONSTRAINT TRIGGER tg_producto_opciones_combinacion
    AFTER INSERT OR DELETE ON producto_opciones DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION fn_tg_revisar_variante();

-- T6. Aislamiento entre tiendas: las tablas puente solo unen filas de la
--     MISMA tienda. (Agregar id_tienda a cada tabla puente repetiría datos;
--     un trigger compara la tienda de ambos lados.)
CREATE FUNCTION fn_tienda_de_producto(p_producto BIGINT) RETURNS BIGINT AS $$
    SELECT id_tienda FROM productos WHERE id_producto = p_producto;
$$ LANGUAGE sql STABLE;

CREATE FUNCTION fn_tienda_de_variante(p_variante BIGINT) RETURNS BIGINT AS $$
    SELECT p.id_tienda FROM variantes v JOIN productos p USING (id_producto) WHERE v.id_variante = p_variante;
$$ LANGUAGE sql STABLE;

CREATE FUNCTION fn_misma_tienda() RETURNS trigger AS $$
DECLARE a BIGINT; b BIGINT;
BEGIN
    CASE TG_TABLE_NAME
    WHEN 'producto_categorias' THEN
        a := fn_tienda_de_producto(NEW.id_producto);
        SELECT id_tienda INTO b FROM categorias WHERE id_categoria = NEW.id_categoria;
    WHEN 'oferta_productos' THEN
        a := fn_tienda_de_producto(NEW.id_producto);
        SELECT id_tienda INTO b FROM ofertas WHERE id_oferta = NEW.id_oferta;
    WHEN 'items_carrito' THEN
        a := fn_tienda_de_variante(NEW.id_variante);
        SELECT id_tienda INTO b FROM carritos WHERE id_carrito = NEW.id_carrito;
    END CASE;
    IF a IS NOT NULL AND b IS NOT NULL AND a <> b THEN       -- si falta una fila, la FK avisa
        RAISE EXCEPTION '%: no se pueden unir datos de la tienda % con datos de la tienda %', TG_TABLE_NAME, a, b;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_producto_categorias_tienda BEFORE INSERT OR UPDATE ON producto_categorias
    FOR EACH ROW EXECUTE FUNCTION fn_misma_tienda();
CREATE TRIGGER tg_oferta_productos_tienda    BEFORE INSERT OR UPDATE ON oferta_productos
    FOR EACH ROW EXECUTE FUNCTION fn_misma_tienda();
CREATE TRIGGER tg_items_carrito_tienda       BEFORE INSERT OR UPDATE ON items_carrito
    FOR EACH ROW EXECUTE FUNCTION fn_misma_tienda();

-- T7. Línea de pedido coherente: la variante es de la tienda del pedido, el
--     precio estaba vigente cuando se hizo el pedido y, si hay oferta, es de
--     esa tienda, está activa, incluye ese producto, estaba vigente y el
--     descuento por unidad es el que da la oferta (porcentaje o monto fijo,
--     nunca más que el precio).
CREATE FUNCTION fn_detalle_consistente() RETURNS trigger AS $$
DECLARE
    v_ped    RECORD;
    v_precio RECORD;
    v_of     RECORD;
    v_prod   BIGINT;
    v_desc   NUMERIC(12,2);
BEGIN
    IF TG_OP = 'UPDATE' AND (NEW.id_pedido, NEW.id_variante, NEW.id_precio, NEW.id_oferta, NEW.descuento_unitario)
                            IS NOT DISTINCT FROM (OLD.id_pedido, OLD.id_variante, OLD.id_precio, OLD.id_oferta, OLD.descuento_unitario) THEN
        RETURN NEW;
    END IF;
    SELECT id_tienda, fecha_creacion INTO v_ped FROM pedidos WHERE id_pedido = NEW.id_pedido;
    IF NOT FOUND THEN RETURN NEW; END IF;             -- la FK se encarga
    SELECT id_producto INTO v_prod FROM variantes WHERE id_variante = NEW.id_variante;

    IF fn_tienda_de_variante(NEW.id_variante) <> v_ped.id_tienda THEN
        RAISE EXCEPTION 'La variante % no es de la tienda del pedido', NEW.id_variante;
    END IF;

    SELECT monto, fecha_inicio, fecha_fin INTO v_precio
      FROM precios WHERE id_precio = NEW.id_precio AND id_variante = NEW.id_variante;
    IF FOUND AND NOT (v_precio.fecha_inicio <= v_ped.fecha_creacion
                      AND (v_precio.fecha_fin IS NULL OR v_precio.fecha_fin > v_ped.fecha_creacion)) THEN
        RAISE EXCEPTION 'El precio % no estaba vigente cuando se hizo el pedido', NEW.id_precio;
    END IF;

    IF NEW.id_oferta IS NOT NULL THEN
        SELECT id_tienda, tipo_descuento, valor, estado, fecha_inicio, fecha_fin INTO v_of FROM ofertas WHERE id_oferta = NEW.id_oferta;
        IF FOUND THEN
            IF v_of.id_tienda <> v_ped.id_tienda THEN
                RAISE EXCEPTION 'La oferta % es de otra tienda', NEW.id_oferta;
            END IF;
            IF NOT EXISTS (SELECT 1 FROM oferta_productos
                            WHERE id_oferta = NEW.id_oferta AND id_producto = v_prod) THEN
                RAISE EXCEPTION 'La oferta % no incluye ese producto', NEW.id_oferta;
            END IF;
            IF NOT (v_of.fecha_inicio <= v_ped.fecha_creacion
                    AND (v_of.fecha_fin IS NULL OR v_of.fecha_fin > v_ped.fecha_creacion)) THEN
                RAISE EXCEPTION 'La oferta % no estaba vigente cuando se hizo el pedido', NEW.id_oferta;
            END IF;
            IF v_of.estado <> 1 THEN
                RAISE EXCEPTION 'La oferta % está pausada', NEW.id_oferta;
            END IF;
            -- el descuento por unidad sale de la oferta y nunca supera el precio
            v_desc := CASE v_of.tipo_descuento
                          WHEN 1 THEN round(v_precio.monto * v_of.valor / 100, 2)
                          ELSE least(v_of.valor, v_precio.monto) END;
            IF v_desc IS NOT NULL AND NEW.descuento_unitario <> v_desc THEN
                RAISE EXCEPTION 'El descuento por unidad debe ser S/ % según la oferta %', v_desc, NEW.id_oferta;
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_detalle_consistente
    BEFORE INSERT OR UPDATE ON detalle_pedido
    FOR EACH ROW EXECUTE FUNCTION fn_detalle_consistente();

-- T8. Una fila no cambia de dueño: un producto, una categoría, una oferta,
--     un carrito o un pedido no se pasan a otra tienda, y una variante no se
--     pasa a otro producto. (Si se permitiera, se saltarían las reglas de T6 y T7.)
CREATE FUNCTION fn_dueno_inmutable() RETURNS trigger AS $$
BEGIN
    IF to_jsonb(NEW) -> TG_ARGV[0] IS DISTINCT FROM to_jsonb(OLD) -> TG_ARGV[0] THEN
        RAISE EXCEPTION '%.% no se puede cambiar', TG_TABLE_NAME, TG_ARGV[0];
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_productos_dueno  BEFORE UPDATE ON productos  FOR EACH ROW EXECUTE FUNCTION fn_dueno_inmutable('id_tienda');
CREATE TRIGGER tg_categorias_dueno BEFORE UPDATE ON categorias FOR EACH ROW EXECUTE FUNCTION fn_dueno_inmutable('id_tienda');
CREATE TRIGGER tg_ofertas_dueno    BEFORE UPDATE ON ofertas    FOR EACH ROW EXECUTE FUNCTION fn_dueno_inmutable('id_tienda');
CREATE TRIGGER tg_carritos_dueno   BEFORE UPDATE ON carritos   FOR EACH ROW EXECUTE FUNCTION fn_dueno_inmutable('id_tienda');
CREATE TRIGGER tg_pedidos_dueno    BEFORE UPDATE ON pedidos    FOR EACH ROW EXECUTE FUNCTION fn_dueno_inmutable('id_tienda');
CREATE TRIGGER tg_variantes_dueno  BEFORE UPDATE ON variantes  FOR EACH ROW EXECUTE FUNCTION fn_dueno_inmutable('id_producto');

-- T9. Una dirección que ya se usó en un pedido no se edita: el pedido debe
--     seguir mostrando la dirección a la que se envió. Solo se puede
--     archivar (estado = 0) o quitar como predeterminada; para cambiarla,
--     el cliente crea una dirección nueva.
CREATE FUNCTION fn_direccion_usada_inmutable() RETURNS trigger AS $$
BEGIN
    IF (to_jsonb(NEW) - 'estado' - 'predeterminada') IS DISTINCT FROM (to_jsonb(OLD) - 'estado' - 'predeterminada')
       AND EXISTS (SELECT 1 FROM pedidos
                    WHERE OLD.id_direccion IN (id_direccion_envio, id_direccion_facturacion)) THEN
        RAISE EXCEPTION 'La dirección % ya se usó en un pedido: archívala y crea una nueva', OLD.id_direccion;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_direcciones_usada
    BEFORE UPDATE ON direcciones
    FOR EACH ROW EXECUTE FUNCTION fn_direccion_usada_inmutable();

-- T10. Un pago aprobado cubre exactamente el total del pedido.
CREATE FUNCTION fn_pago_monto() RETURNS trigger AS $$
DECLARE v_total NUMERIC(12,2);
BEGIN
    IF NEW.estado = 1 THEN
        SELECT total INTO v_total FROM pedidos WHERE id_pedido = NEW.id_pedido;
        IF FOUND AND NEW.monto <> v_total THEN
            RAISE EXCEPTION 'Un pago aprobado debe ser igual al total del pedido (S/ %)', v_total;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_pagos_monto
    BEFORE INSERT OR UPDATE ON pagos
    FOR EACH ROW EXECUTE FUNCTION fn_pago_monto();

-- T11. Una oferta con la que ya se vendió conserva sus condiciones: no se le
--      quita el producto vendido ni se cambia su tipo, su valor o su inicio,
--      y su fin no se adelanta antes de una venta. Sí se puede pausar (estado).
CREATE FUNCTION fn_oferta_usada() RETURNS trigger AS $$
BEGIN
    IF TG_TABLE_NAME = 'oferta_productos' THEN
        IF EXISTS (SELECT 1 FROM detalle_pedido d JOIN variantes v USING (id_variante)
                    WHERE d.id_oferta = OLD.id_oferta AND v.id_producto = OLD.id_producto) THEN
            RAISE EXCEPTION 'Ya se vendió el producto % con la oferta %: no se puede quitar de ella',
                OLD.id_producto, OLD.id_oferta;
        END IF;
        RETURN COALESCE(NEW, OLD);
    END IF;

    -- tabla ofertas
    IF EXISTS (SELECT 1 FROM detalle_pedido WHERE id_oferta = OLD.id_oferta) THEN
        IF (NEW.tipo_descuento, NEW.valor, NEW.fecha_inicio)
           IS DISTINCT FROM (OLD.tipo_descuento, OLD.valor, OLD.fecha_inicio) THEN
            RAISE EXCEPTION 'Ya se vendió con la oferta %: su tipo, valor e inicio no se cambian', OLD.id_oferta;
        END IF;
        IF NEW.fecha_fin IS DISTINCT FROM OLD.fecha_fin AND NEW.fecha_fin IS NOT NULL AND EXISTS (
            SELECT 1 FROM detalle_pedido d JOIN pedidos p USING (id_pedido)
             WHERE d.id_oferta = OLD.id_oferta AND p.fecha_creacion >= NEW.fecha_fin) THEN
            RAISE EXCEPTION 'La oferta % se usó en un pedido posterior a esa fecha de fin', OLD.id_oferta;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_oferta_productos_usada
    BEFORE DELETE OR UPDATE ON oferta_productos
    FOR EACH ROW EXECUTE FUNCTION fn_oferta_usada();
CREATE TRIGGER tg_ofertas_usada
    BEFORE UPDATE ON ofertas
    FOR EACH ROW EXECUTE FUNCTION fn_oferta_usada();

COMMIT;
