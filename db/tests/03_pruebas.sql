-- =====================================================================
--  VENDELY · Pruebas del modelo · versión 3 · INTERNACIONAL
--  Ejecutar después de 01_esquema.sql y 02_datos_iniciales.sql.
--  Todo corre dentro de una transacción que al final se deshace
--  (ROLLBACK), así que no deja datos en la base.
--
--  Parte A: un recorrido completo que DEBE funcionar (Perú, Chile, México
--           y ventas al extranjero).
--  Parte B: casos que la base DEBE rechazar (B01–B64 de la versión Perú,
--           B65–B82 de países, B83–B95 de ventas internacionales y
--           B96–B104 de la revisión de la versión 3 y B105–B113 de la
--           revisión final, con latitud y longitud).
-- =====================================================================

BEGIN;

-- Ayuda para la parte B: ejecuta una instrucción y avisa si NO falló.
CREATE FUNCTION pg_temp.debe_fallar(p_caso TEXT, p_sql TEXT) RETURNS TEXT AS $$
BEGIN
    EXECUTE p_sql;
    SET CONSTRAINTS ALL IMMEDIATE;   -- también revisa las reglas que esperan al final (variantes)
    RETURN 'FALLO DE PRUEBA  ' || p_caso || '  (la base lo aceptó)';
EXCEPTION WHEN OTHERS THEN
    RETURN 'OK  ' || p_caso || '  →  ' || SQLERRM;
END;
$$ LANGUAGE plpgsql;

-- Ayudas: id de una división por país y código oficial, y su "ruta" completa
CREATE FUNCTION pg_temp.div(p_pais CHAR(2), p_codigo TEXT) RETURNS BIGINT AS $$
    SELECT id_division FROM divisiones WHERE id_pais = p_pais AND codigo = p_codigo;
$$ LANGUAGE sql STABLE;

CREATE FUNCTION pg_temp.ruta(p_division BIGINT) RETURNS TEXT AS $$
    WITH RECURSIVE sube AS (
        SELECT id_division, id_padre, nombre, nivel FROM divisiones WHERE id_division = p_division
        UNION ALL
        SELECT d.id_division, d.id_padre, d.nombre, d.nivel FROM divisiones d JOIN sube s ON d.id_division = s.id_padre)
    SELECT string_agg(nombre, ' › ' ORDER BY nivel DESC) FROM sube;
$$ LANGUAGE sql STABLE;

-- ---------------------------------------------------------------------
-- PARTE A · Recorrido válido
-- ---------------------------------------------------------------------

-- A1. Carlos crea su cuenta del panel y registra su RUC
INSERT INTO login (id_login, id_rol, usuario, correo, contrasena_hash, nombres, primer_apellido, segundo_apellido)
VALUES (1, 2, 'carlos', 'carlos@mail.pe', '$2a$10$hash', 'Carlos', 'Ramos', 'Soto');

INSERT INTO propietarios (id_propietario, id_login, tipo_documento, numero_documento, razon_social)
VALUES (1, 1, 2, '20123456789', 'Zapatillas Carlos S.A.C.');

-- A2. Crea dos tiendas
--     (la 1 sin indicar país: toma 'PE' por defecto, como hace hoy el backend)
INSERT INTO tiendas (id_tienda, id_propietario, nombre, subdominio, correo_contacto) VALUES
 (1, 1, 'Zapatillas Carlos', 'zapatillas-carlos', 'ventas@zapatillas.pe');
INSERT INTO tiendas (id_tienda, id_propietario, id_pais, nombre, subdominio, correo_contacto) VALUES
 (2, 1, 'PE', 'Celulares Carlos',  'celulares-carlos',  'ventas@celulares.pe');

-- A3. Tarifas de envío por departamento
INSERT INTO tarifas_envio (id_tienda, id_division, costo, envio_gratis_desde, dias_entrega_min, dias_entrega_max)
SELECT 1, id_division, v.costo, v.gratis, v.dmin, v.dmax
  FROM (VALUES ('15', 10.00, 200.00, 1, 3), ('04', 18.00, NULL::NUMERIC, 3, 5)) AS v (cod, costo, gratis, dmin, dmax)
  JOIN divisiones ON id_pais = 'PE' AND codigo = v.cod;
-- Celulares Carlos solo envía a Lima (gratis desde S/ 500). Ninguna tienda envía a Cusco.
INSERT INTO tarifas_envio (id_tienda, id_division, costo, envio_gratis_desde, dias_entrega_min, dias_entrega_max)
SELECT 2, id_division, 15.00, 500.00, 1, 2 FROM divisiones WHERE id_pais = 'PE' AND codigo = '15';

-- A4. Catálogo: un producto en DOS categorías, con 2 opciones y 2 variantes
INSERT INTO categorias (id_categoria, id_tienda, nombre, slug) VALUES
 (1, 1, 'Zapatillas',        'zapatillas'),
 (2, 1, 'Ofertas de verano', 'ofertas-de-verano'),
 (3, 2, 'Zapatillas',        'zapatillas');        -- mismo slug en OTRA tienda: permitido

INSERT INTO productos (id_producto, id_tienda, nombre, slug, marca, estado)
VALUES (1, 1, 'Zapatilla Runner', 'zapatilla-runner', 'Vendely Sport', 1);

INSERT INTO producto_categorias (id_producto, id_categoria) VALUES (1, 1), (1, 2);

INSERT INTO producto_opciones (id_producto, posicion, nombre) VALUES (1, 1, 'Talla'), (1, 2, 'Color');

INSERT INTO variantes (id_variante, id_producto, sku, posicion) VALUES
 (1, 1, 'RUN-42-NEG', 1),
 (2, 1, 'RUN-43-BLA', 2);

INSERT INTO variante_valores (id_variante, id_producto, posicion, valor) VALUES
 (1, 1, 1, '42'), (1, 1, 2, 'Negro'),
 (2, 1, 1, '43'), (2, 1, 2, 'Blanco');

INSERT INTO precios (id_precio, id_variante, monto, monto_comparacion) VALUES
 (1, 1, 145.00, 180.00),
 (2, 2, 145.00, NULL);

INSERT INTO inventario (id_variante, cantidad_disponible, stock_minimo) VALUES (1, 10, 2), (2, 5, 2);

-- A4b. Producto de la otra tienda con la opción "Almacenamiento"
INSERT INTO productos (id_producto, id_tienda, nombre, slug, estado) VALUES (2, 2, 'Celular X', 'celular-x', 1);
INSERT INTO producto_opciones (id_producto, posicion, nombre) VALUES (2, 1, 'Almacenamiento'), (2, 2, 'Color');
INSERT INTO variantes (id_variante, id_producto, sku) VALUES (3, 2, 'CX-256-NEG'), (4, 2, 'CX-128-BLA');
INSERT INTO variante_valores (id_variante, id_producto, posicion, valor) VALUES
 (3, 2, 1, '256 GB'), (3, 2, 2, 'Negro'),
 (4, 2, 1, '128 GB'), (4, 2, 2, 'Blanco');
INSERT INTO precios (id_precio, id_variante, monto) VALUES (3, 3, 1999.00);

-- A4e. Historial del IGV: 19 % hasta febrero de 2011 (tasa ya cerrada)
INSERT INTO impuestos (id_pais, nombre, tasa, vigente_desde, vigente_hasta) VALUES ('PE', 'IGV', 19.00, '2004-08-01', '2011-03-01');

-- A4c. Oferta de verano de la tienda 1 con la Zapatilla Runner
INSERT INTO ofertas (id_oferta, id_tienda, nombre, motivo, tipo_descuento, valor, fecha_inicio)
VALUES (1, 1, 'Verano', 2, 1, 10, now());
INSERT INTO oferta_productos (id_oferta, id_producto) VALUES (1, 1);
-- y una oferta pausada (estado 0) con el mismo producto
INSERT INTO ofertas (id_oferta, id_tienda, nombre, motivo, tipo_descuento, valor, fecha_inicio, estado)
VALUES (2, 1, 'Pausada', 4, 1, 10, now(), 0);
INSERT INTO oferta_productos (id_oferta, id_producto) VALUES (2, 1);

-- A4d. Una tercera tienda, suspendida, con tarifa para Lima
INSERT INTO tiendas (id_tienda, id_propietario, nombre, subdominio, correo_contacto, estado)
VALUES (3, 1, 'Tienda pausada', 'tienda-pausada', 'pausa@mail.pe', 0);
INSERT INTO tarifas_envio (id_tienda, id_division, costo)
SELECT 3, id_division, 10.00 FROM divisiones WHERE id_pais = 'PE' AND codigo = '15';

-- A5. María se registra como clienta en las dos tiendas, con el mismo correo
INSERT INTO login (id_login, id_rol, id_tienda, usuario, correo, contrasena_hash, nombres, primer_apellido) VALUES
 (2, 3, 1, 'maria', 'maria@mail.pe', '$2a$10$hash', 'María', 'Huamán'),
 (3, 3, 2, 'maria', 'maria@mail.pe', '$2a$10$hash', 'María', 'Huamán');

-- (marcó su casa en el mapa: latitud y longitud)
INSERT INTO direcciones (id_direccion, id_login, id_pais, id_division, destinatario, telefono, direccion, latitud, longitud, predeterminada)
VALUES (1, 2, 'PE', pg_temp.div('PE', '150122'),
        'María Huamán', '+51999888777', 'Av. Larco 123', -12.121100, -77.029700, TRUE);

-- Dirección de un invitado (sin cuenta), la de María en la tienda 2 y otra de María en Cusco
INSERT INTO direcciones (id_direccion, id_login, id_pais, id_division, destinatario, telefono, direccion) VALUES
 (2, NULL, 'PE', pg_temp.div('PE', '150140'), 'Invitado Pérez', '+51988777666', 'Av. Primavera 456'),
 (3, 3,    'PE', pg_temp.div('PE', '150122'), 'María Huamán', '+51999888777', 'Av. Larco 123'),
 (4, 2,    'PE', pg_temp.div('PE', '080101'), 'María Huamán', '+51999888777', 'Calle Plateros 10');

-- A6. Carrito con 2 pares de la variante 1
INSERT INTO carritos (id_carrito, id_tienda, id_login, token) VALUES (1, 1, 2, 'tok-maria-1');
INSERT INTO items_carrito (id_carrito, id_variante, cantidad) VALUES (1, 1, 2);

-- A7. Pedido: 2 × S/145.00 = S/290.00 · envío gratis en Lima (desde S/200)
--     IGV incluido: 290 − 290 / 1.18 = 44.24. El trigger pone el número y el IGV vigente.
INSERT INTO pedidos (id_pedido, id_tienda, id_login, id_direccion_envio, correo_contacto,
                     subtotal, costo_envio, monto_impuestos, total)
VALUES (1, 1, 2, 1, 'maria@mail.pe', 290.00, 0.00, 44.24, 290.00);

INSERT INTO detalle_pedido (id_pedido, id_variante, id_precio, nombre_producto, nombre_variante, cantidad)
VALUES (1, 1, 1, 'Zapatilla Runner', '42 / Negro', 2);

INSERT INTO pagos (id_pedido, proveedor, monto, estado, referencia_transaccion, fecha_pago)
VALUES (1, 1, 290.00, 1, 'chr_test_0001', now());

UPDATE inventario SET cantidad_disponible = cantidad_disponible - 2 WHERE id_variante = 1;

-- A8. Un pedido de un invitado en la tienda 1: el backend solo envía el subtotal
--     y el trigger completa envío (S/ 10, Lima), total (155.00) e IGV (23.64).
--     Y el primer pedido de la tienda 2 (María, con su cuenta de esa tienda).
INSERT INTO pedidos (id_pedido, id_tienda, id_direccion_envio, correo_contacto, subtotal)
VALUES (2, 1, 2, 'invitado@mail.pe', 145.00);
INSERT INTO pedidos (id_pedido, id_tienda, id_login, id_direccion_envio, correo_contacto,
                     subtotal, costo_envio, monto_impuestos, total)
VALUES (3, 2, 3, 3, 'maria@mail.pe', 1999.00, 0.00, 304.93, 1999.00);
INSERT INTO detalle_pedido (id_pedido, id_variante, id_precio, nombre_producto, nombre_variante, cantidad)
VALUES (3, 3, 3, 'Celular X', '256 GB / Negro', 1);

-- A9. Cambio de precio correcto: se cierra el vigente y se crea otro
UPDATE precios SET fecha_fin = now() + interval '1 second' WHERE id_precio = 1;
INSERT INTO precios (id_precio, id_variante, monto, fecha_inicio)
VALUES (4, 1, 139.00, now() + interval '1 second');

-- A11. CHILE · Ana abre una tienda en Chile con su RUT. Todo va en pesos
--      chilenos (CLP) y con el IVA de Chile (19 %).
--      Para la prueba se habilitan Chile y México como si su lista oficial
--      ya estuviera completa (en los datos iniciales están en preparación).
-- (Antes de habilitar México, un invitado guardó allí una dirección escrita a
--  mano, como en cualquier país destino. La usa la prueba B113.)
INSERT INTO direcciones (id_direccion, id_login, id_pais, region, ciudad, destinatario, telefono, direccion, codigo_postal)
VALUES (11, NULL, 'MX', 'CDMX', 'Ciudad de México', 'Invitado México', '+525533334444', 'Av. Reforma 1', '06600');
UPDATE paises SET estado = 1 WHERE id_pais IN ('CL', 'MX');
INSERT INTO login (id_login, id_rol, usuario, correo, contrasena_hash, nombres, primer_apellido)
VALUES (4, 2, 'ana', 'ana@mail.cl', '$2a$10$hash', 'Ana', 'Rojas');
INSERT INTO propietarios (id_propietario, id_login, tipo_documento, numero_documento, razon_social)
VALUES (2, 4, 4, '12345678-5', 'Poleras Ana SpA');
INSERT INTO tiendas (id_tienda, id_propietario, id_pais, nombre, subdominio, correo_contacto)
VALUES (4, 2, 'CL', 'Poleras Ana', 'poleras-ana', 'ventas@poleras.cl');
-- Tarifa para la Región Metropolitana: $3.990, gratis desde $50.000
INSERT INTO tarifas_envio (id_tienda, id_division, costo, envio_gratis_desde)
VALUES (4, pg_temp.div('CL', '13'), 3990.00, 50000.00);

INSERT INTO productos (id_producto, id_tienda, nombre, slug, estado) VALUES (3, 4, 'Polera Básica', 'polera-basica', 1);
INSERT INTO variantes (id_variante, id_producto, sku) VALUES (5, 3, 'POL-UNI');   -- sin opciones: una sola variante
INSERT INTO precios (id_precio, id_variante, monto) VALUES (5, 5, 19990.00);
INSERT INTO inventario (id_variante, cantidad_disponible) VALUES (5, 30);

-- Pedro se registra en la tienda de Ana y guarda una dirección en la comuna de Providencia
INSERT INTO login (id_login, id_rol, id_tienda, usuario, correo, contrasena_hash, nombres, primer_apellido)
VALUES (5, 3, 4, 'pedro', 'pedro@mail.cl', '$2a$10$hash', 'Pedro', 'Soto');
INSERT INTO direcciones (id_direccion, id_login, id_pais, id_division, destinatario, telefono, direccion, codigo_postal)
VALUES (5, 5, 'CL', pg_temp.div('CL', '13123'), 'Pedro Soto', '+56911112222', 'Av. Providencia 1234', '7500000');

-- Pedido: el backend solo envía el subtotal ($19.990); el trigger pone envío
-- ($3.990, Región Metropolitana), total ($23.980) e IVA de Chile (19 %):
-- 23980 × 19 / 119 = 3828.74 → $3.829 (el peso chileno no tiene centavos)
INSERT INTO pedidos (id_pedido, id_tienda, id_login, id_direccion_envio, correo_contacto, subtotal)
VALUES (5, 4, 5, 5, 'pedro@mail.cl', 19990.00);
INSERT INTO detalle_pedido (id_pedido, id_variante, id_precio, nombre_producto, nombre_variante, cantidad)
VALUES (5, 5, 5, 'Polera Básica', 'Única', 1);

-- Dirección de un invitado en Santiago (la usa la prueba B78)
INSERT INTO direcciones (id_direccion, id_login, id_pais, id_division, destinatario, telefono, direccion)
VALUES (6, NULL, 'CL', pg_temp.div('CL', '13101'), 'Invitado Chile', '+56933334444', 'Calle Huérfanos 500');

-- A12. MÉXICO · Carlos abre una tercera tienda, en México. Allí el código
--      postal es obligatorio y tiene 5 dígitos.
INSERT INTO tiendas (id_tienda, id_propietario, id_pais, nombre, subdominio, correo_contacto)
VALUES (5, 1, 'MX', 'Regalos Carlos MX', 'regalos-carlos-mx', 'ventas@regalos.mx');
INSERT INTO tarifas_envio (id_tienda, id_division, costo)
VALUES (5, pg_temp.div('MX', '09'), 99.00);
INSERT INTO login (id_login, id_rol, id_tienda, usuario, correo, contrasena_hash, nombres, primer_apellido)
VALUES (6, 3, 5, 'lucia', 'lucia@mail.mx', '$2a$10$hash', 'Lucía', 'Hernández');
INSERT INTO direcciones (id_direccion, id_login, id_pais, id_division, destinatario, telefono, direccion, codigo_postal)
VALUES (7, 6, 'MX', pg_temp.div('MX', '09015'), 'Lucía Hernández', '+525511112222', 'Calle Durango 100, Roma Norte', '06700');

-- A13. VENTAS AL EXTRANJERO · La tienda de zapatillas (Perú) decide vender a
--      Chile y a Estados Unidos: pone una tarifa internacional para cada país.
--      A los demás países (Japón, Bolivia…) no vende.
INSERT INTO tarifas_internacionales (id_tienda, id_pais, costo, envio_gratis_desde, dias_entrega_min, dias_entrega_max) VALUES
 (1, 'CL', 60.00, 500.00, 5, 8),
 (1, 'US', 90.00, NULL, 7, 12);

-- Tipo de cambio del día que guarda el backend (valores de EJEMPLO)
INSERT INTO tipos_cambio (moneda_origen, moneda_destino, fecha, tasa) VALUES
 ('PEN', 'CLP', current_date, 270.000000),
 ('PEN', 'USD', current_date, 0.290000),
 ('PEN', 'CLP', '2030-01-01', 300.000000);   -- una tasa con fecha futura (la usa B104)

-- Un comprador de Los Ángeles (EE. UU. no tiene lista oficial en Vendely):
-- escribe región y ciudad, y su código postal (en EE. UU. es obligatorio)
INSERT INTO direcciones (id_direccion, id_login, id_pais, region, ciudad, destinatario, telefono, direccion, codigo_postal)
VALUES (8, NULL, 'US', 'California', 'Los Ángeles', 'John Smith', '+12135550123', '200 N Spring St', '90012');
-- Un invitado de Tokio (la usa la prueba B78: la tienda no vende a Japón)
INSERT INTO direcciones (id_direccion, id_login, id_pais, region, ciudad, destinatario, telefono, direccion)
VALUES (9, NULL, 'JP', 'Tokio', 'Shibuya', 'Kenji Sato', '+819012345678', '1-2-3 Jingumae');

-- Pedido 7: un chileno (dirección 6, Santiago) compra 2 pares (S/ 290). Es una
-- EXPORTACIÓN: envío a Chile S/ 60, total S/ 350, SIN impuesto. Vio el precio
-- aproximado en pesos con el tipo de cambio del día: 350 × 270 = $94.500 CLP.
INSERT INTO pedidos (id_pedido, id_tienda, id_direccion_envio, correo_contacto, subtotal, moneda_cliente, fecha_tipo_cambio)
VALUES (7, 1, 6, 'comprador@mail.cl', 290.00, 'CLP', current_date);
-- Pedido 8: el comprador de Los Ángeles compra 2 pares: envío S/ 90, total S/ 380,
-- sin impuesto; vio aprox. 380 × 0.29 = US$ 110.20
INSERT INTO pedidos (id_pedido, id_tienda, id_direccion_envio, correo_contacto, subtotal, moneda_cliente, fecha_tipo_cambio)
VALUES (8, 1, 8, 'john@mail.com', 290.00, 'USD', current_date);

-- A10. Se revisan ya las reglas que esperan al final de la transacción
--      (variantes completas y sin combinaciones repetidas).
SET CONSTRAINTS ALL IMMEDIATE;

-- Como arriba se usaron ids fijos, se adelantan las secuencias.
DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN SELECT table_name, column_name
               FROM information_schema.columns
              WHERE table_schema = 'public' AND column_default LIKE 'nextval%'
    LOOP
        EXECUTE format('SELECT setval(pg_get_serial_sequence(%L, %L), COALESCE((SELECT max(%I) FROM %I), 0) + 1, false)',
                       r.table_name, r.column_name, r.column_name, r.table_name);
    END LOOP;
END $$;

-- Resultados de la parte A
SELECT 'Pedidos' AS prueba, t.nombre AS tienda, t.id_pais AS pais, p.moneda, p.numero_pedido,
       d.id_pais AS destino, p.subtotal, p.costo_envio, p.total,
       p.monto_impuestos AS impuesto_incluido,
       COALESCE(i.nombre || ' ' || i.tasa || ' %', 'exportación (sin impuesto)') AS impuesto
  FROM pedidos p JOIN tiendas t USING (id_tienda)
  JOIN direcciones d ON d.id_direccion = p.id_direccion_envio
  LEFT JOIN impuestos i USING (id_impuesto)
 ORDER BY p.id_pedido;
-- Esperado: Zapatillas #1001 (envío 0, IGV 44.24) y #1002 (envío 10, total 155, IGV 23.64)
--           · Celulares #1001 (IGV 304.93) · IGV 18.00 %
--           · Poleras Ana (CL, CLP) #1001: envío 3990, total 23980, IVA 19 % = 3829 (sin centavos)
--           · Zapatillas #1003 a CL: envío 60, total 350, sin impuesto · #1004 a US: envío 90, total 380

SELECT 'Precio que vio el comprador extranjero' AS prueba, p.numero_pedido, p.total, p.moneda,
       p.moneda_cliente, tc.tasa,
       round(p.total * tc.tasa, m.decimales) AS total_aproximado
  FROM pedidos p
  JOIN tipos_cambio tc ON (tc.moneda_origen, tc.moneda_destino, tc.fecha) = (p.moneda, p.moneda_cliente, p.fecha_tipo_cambio)
  JOIN monedas m ON m.id_moneda = p.moneda_cliente
 ORDER BY p.id_pedido;
-- Esperado: #1003 350.00 PEN → 94500 CLP (sin decimales) · #1004 380.00 PEN → 110.20 USD

SELECT 'Ubicación de la dirección' AS prueba, d.id_direccion, d.id_pais AS pais,
       COALESCE(n.nombre, 'escrita a mano') AS nivel,
       COALESCE(pg_temp.ruta(d.id_division), d.ciudad || ', ' || d.region) AS ubicacion,
       d.latitud, d.longitud
  FROM direcciones d LEFT JOIN divisiones dv USING (id_division)
  LEFT JOIN niveles_division n ON n.id_pais = dv.id_pais AND n.nivel = dv.nivel
 ORDER BY d.id_direccion;

SELECT 'Categorías del producto' AS prueba, string_agg(c.nombre, ', ' ORDER BY c.nombre) AS categorias
  FROM producto_categorias pc JOIN categorias c USING (id_categoria)
 WHERE pc.id_producto = 1;

-- ---------------------------------------------------------------------
-- PARTE B · Casos que deben fallar
-- ---------------------------------------------------------------------

SELECT resultado FROM (VALUES
 (1,  pg_temp.debe_fallar('B01 doble clic: misma variante dos veces en el carrito',
      $q$INSERT INTO items_carrito (id_carrito, id_variante, cantidad) VALUES (1, 1, 1)$q$)),
 (2,  pg_temp.debe_fallar('B02 misma variante dos veces en un pedido',
      $q$INSERT INTO detalle_pedido (id_pedido, id_variante, id_precio, nombre_producto, nombre_variante, cantidad)
         VALUES (1, 1, 1, 'Zapatilla Runner', '42 / Negro', 1)$q$)),
 (3,  pg_temp.debe_fallar('B03 línea de pedido con el precio de OTRA variante',
      $q$INSERT INTO detalle_pedido (id_pedido, id_variante, id_precio, nombre_producto, nombre_variante, cantidad)
         VALUES (2, 2, 1, 'Zapatilla Runner', '43 / Blanco', 1)$q$)),
 (4,  pg_temp.debe_fallar('B04 dos precios vigentes para la misma variante',
      $q$INSERT INTO precios (id_variante, monto) VALUES (2, 150.00)$q$)),
 (5,  pg_temp.debe_fallar('B05 editar el monto de un precio',
      $q$UPDATE precios SET monto = 1.00 WHERE id_precio = 2$q$)),
 (6,  pg_temp.debe_fallar('B06 dos cuentas del panel con el mismo correo',
      $q$INSERT INTO login (id_rol, usuario, correo, contrasena_hash, nombres, primer_apellido)
         VALUES (2, 'carlos2', 'carlos@mail.pe', 'x', 'Otro', 'Carlos')$q$)),
 (7,  pg_temp.debe_fallar('B07 cuenta de cliente sin tienda',
      $q$INSERT INTO login (id_rol, usuario, correo, contrasena_hash, nombres, primer_apellido)
         VALUES (3, 'sin_tienda', 'x@mail.pe', 'x', 'X', 'Y')$q$)),
 (8,  pg_temp.debe_fallar('B08 una clienta registrada como propietaria',
      $q$INSERT INTO propietarios (id_login, tipo_documento, numero_documento) VALUES (2, 1, '12345678')$q$)),
 (9,  pg_temp.debe_fallar('B09 DNI con 7 dígitos',
      $q$UPDATE propietarios SET tipo_documento = 1, numero_documento = '1234567' WHERE id_propietario = 1$q$)),
 (10, pg_temp.debe_fallar('B10 RUC que empieza con 30',
      $q$UPDATE propietarios SET numero_documento = '30123456789' WHERE id_propietario = 1$q$)),
 (11, pg_temp.debe_fallar('B11 mismo producto dos veces en la misma categoría',
      $q$INSERT INTO producto_categorias (id_producto, id_categoria) VALUES (1, 1)$q$)),
 (12, pg_temp.debe_fallar('B12 valor para una opción que el producto no tiene',
      $q$INSERT INTO variante_valores (id_variante, id_producto, posicion, valor) VALUES (1, 1, 3, 'Cuero')$q$)),
 (13, pg_temp.debe_fallar('B13 valor de una variante usando OTRO producto',
      $q$UPDATE variante_valores SET id_producto = 2 WHERE id_variante = 1 AND posicion = 1$q$)),
 (14, pg_temp.debe_fallar('B14 carrito de la tienda 1 con la cuenta de cliente de la tienda 2',
      $q$INSERT INTO carritos (id_tienda, id_login, token) VALUES (1, 3, 'tok-x')$q$)),
 (15, pg_temp.debe_fallar('B15 pedido cuyo total no cuadra',
      $q$INSERT INTO pedidos (id_tienda, id_login, id_direccion_envio, correo_contacto, subtotal, costo_envio, total)
         VALUES (1, 2, 1, 'a@mail.pe', 100, 10, 999)$q$)),
 (16, pg_temp.debe_fallar('B16 distrito colgado directo de un departamento',
      $q$INSERT INTO divisiones (id_pais, nivel, id_padre, codigo, nombre)
         VALUES ('PE', 3, pg_temp.div('PE', '15'), '159999', 'Mal')$q$)),
 (17, pg_temp.debe_fallar('B17 código de división repetido en el mismo país',
      $q$INSERT INTO divisiones (id_pais, nivel, id_padre, codigo, nombre) VALUES ('PE', 1, NULL, '15', 'Otra Lima')$q$)),
 (18, pg_temp.debe_fallar('B18 dirección que apunta a una provincia y no a un distrito',
      $q$INSERT INTO direcciones (id_login, id_pais, id_division, destinatario, telefono, direccion)
         VALUES (2, 'PE', pg_temp.div('PE', '1501'), 'X', '+51900000001', 'Calle 1')$q$)),
 (19, pg_temp.debe_fallar('B19 tarifa de envío para un distrito y no para un departamento',
      $q$INSERT INTO tarifas_envio (id_tienda, id_division, costo)
         VALUES (1, pg_temp.div('PE', '150122'), 5)$q$)),
 (20, pg_temp.debe_fallar('B20 dos tarifas de la misma tienda para Lima',
      $q$INSERT INTO tarifas_envio (id_tienda, id_division, costo)
         VALUES (1, pg_temp.div('PE', '15'), 12)$q$)),
 (21, pg_temp.debe_fallar('B21 dos tasas de IGV vigentes a la vez',
      $q$INSERT INTO impuestos (id_pais, nombre, tasa, vigente_desde) VALUES ('PE', 'IGV', 19.00, '2026-01-01')$q$)),
 (22, pg_temp.debe_fallar('B22 editar la tasa del IGV',
      $q$UPDATE impuestos SET tasa = 20.00 WHERE id_pais = 'PE' AND nombre = 'IGV'$q$)),
 (23, pg_temp.debe_fallar('B23 oferta de 150 %',
      $q$INSERT INTO ofertas (id_tienda, nombre, motivo, tipo_descuento, valor, fecha_inicio)
         VALUES (1, 'Mal', 1, 1, 150, now())$q$)),
 (24, pg_temp.debe_fallar('B24 segundo pago aprobado del mismo pedido',
      $q$INSERT INTO pagos (id_pedido, proveedor, monto, estado, fecha_pago) VALUES (1, 1, 290, 1, now())$q$)),
 (25, pg_temp.debe_fallar('B25 stock negativo',
      $q$UPDATE inventario SET cantidad_disponible = -1 WHERE id_variante = 2$q$)),
 (26, pg_temp.debe_fallar('B26 slug repetido en la misma tienda',
      $q$INSERT INTO productos (id_tienda, nombre, slug) VALUES (1, 'Otra', 'zapatilla-runner')$q$)),
 (27, pg_temp.debe_fallar('B27 pago aprobado sin fecha de pago',
      $q$INSERT INTO pagos (id_pedido, proveedor, monto, estado) VALUES (2, 4, 155, 1)$q$)),
 (28, pg_temp.debe_fallar('B28 cuarta opción en un producto (máximo 3)',
      $q$INSERT INTO producto_opciones (id_producto, posicion, nombre) VALUES (1, 4, 'Material')$q$)),
 (29, pg_temp.debe_fallar('B29 número de pedido repetido en la misma tienda',
      $q$UPDATE pedidos SET numero_pedido = 1001 WHERE id_pedido = 2$q$)),
 (30, pg_temp.debe_fallar('B30 subdominio con mayúsculas y espacios',
      $q$INSERT INTO tiendas (id_propietario, nombre, subdominio, correo_contacto)
         VALUES (1, 'Mal', 'Mi Tienda', 'a@mail.pe')$q$)),
 -- Correcciones de la revisión del Avance 02
 (31, pg_temp.debe_fallar('B31 segunda variante "42 / Negro" del mismo producto',
      $q$WITH v AS (INSERT INTO variantes (id_producto, sku) VALUES (1, 'RUN-42-NEG-B') RETURNING id_variante)
         INSERT INTO variante_valores (id_variante, id_producto, posicion, valor)
         SELECT id_variante, 1, 1, '42' FROM v UNION ALL SELECT id_variante, 1, 2, 'negro' FROM v$q$)),
 (32, pg_temp.debe_fallar('B32 variante con talla pero sin color',
      $q$WITH v AS (INSERT INTO variantes (id_producto, sku) VALUES (1, 'RUN-44') RETURNING id_variante)
         INSERT INTO variante_valores (id_variante, id_producto, posicion, valor)
         SELECT id_variante, 1, 1, '44' FROM v$q$)),
 (33, pg_temp.debe_fallar('B33 nueva opción "Material" sin valor en las variantes que ya existen',
      $q$INSERT INTO producto_opciones (id_producto, posicion, nombre) VALUES (1, 3, 'Material')$q$)),
 (34, pg_temp.debe_fallar('B34 producto de la tienda 1 en una categoría de la tienda 2',
      $q$INSERT INTO producto_categorias (id_producto, id_categoria) VALUES (1, 3)$q$)),
 (35, pg_temp.debe_fallar('B35 oferta de la tienda 1 con un producto de la tienda 2',
      $q$INSERT INTO oferta_productos (id_oferta, id_producto) VALUES (1, 2)$q$)),
 (36, pg_temp.debe_fallar('B36 carrito de la tienda 1 con una variante de la tienda 2',
      $q$INSERT INTO items_carrito (id_carrito, id_variante, cantidad) VALUES (1, 3, 1)$q$)),
 (37, pg_temp.debe_fallar('B37 pedido de la tienda 1 con una variante de la tienda 2',
      $q$INSERT INTO detalle_pedido (id_pedido, id_variante, id_precio, nombre_producto, nombre_variante, cantidad)
         VALUES (1, 3, 3, 'Celular X', '256 GB / Negro', 1)$q$)),
 (38, pg_temp.debe_fallar('B38 pedido de invitado con la dirección guardada de María',
      $q$INSERT INTO pedidos (id_tienda, id_direccion_envio, correo_contacto, subtotal)
         VALUES (1, 1, 'otro@mail.pe', 100)$q$)),
 (39, pg_temp.debe_fallar('B39 la tienda 2 reutiliza la dirección de un invitado de la tienda 1',
      $q$INSERT INTO pedidos (id_tienda, id_direccion_envio, correo_contacto, subtotal)
         VALUES (2, 2, 'otro@mail.pe', 100)$q$)),
 (40, pg_temp.debe_fallar('B40 pedido a Cusco, donde la tienda no envía',
      $q$INSERT INTO pedidos (id_tienda, id_login, id_direccion_envio, correo_contacto, subtotal)
         VALUES (1, 2, 4, 'maria@mail.pe', 100)$q$)),
 (41, pg_temp.debe_fallar('B41 envío a Lima cobrado en 0 cuando la compra no llega a S/ 200',
      $q$INSERT INTO pedidos (id_tienda, id_login, id_direccion_envio, correo_contacto, subtotal, costo_envio)
         VALUES (1, 2, 1, 'maria@mail.pe', 100, 0)$q$)),
 (42, pg_temp.debe_fallar('B42 IGV del pedido mal calculado',
      $q$INSERT INTO pedidos (id_tienda, id_login, id_direccion_envio, correo_contacto, subtotal, monto_impuestos)
         VALUES (1, 2, 1, 'maria@mail.pe', 100, 18.00)$q$)),
 (43, pg_temp.debe_fallar('B43 editar una dirección ya usada en un pedido',
      $q$UPDATE direcciones SET direccion = 'Otra calle 999' WHERE id_direccion = 1$q$)),
 (44, pg_temp.debe_fallar('B44 precio antiguo cuyo periodo se cruza con el vigente',
      $q$INSERT INTO precios (id_variante, monto, fecha_inicio, fecha_fin)
         VALUES (2, 120.00, now() - interval '1 day', now() + interval '1 day')$q$)),
 (45, pg_temp.debe_fallar('B45 línea de pedido con un precio que aún no estaba vigente',
      $q$INSERT INTO detalle_pedido (id_pedido, id_variante, id_precio, nombre_producto, nombre_variante, cantidad)
         VALUES (2, 1, 4, 'Zapatilla Runner', '42 / Negro', 1)$q$)),
 (46, pg_temp.debe_fallar('B46 línea de pedido de la tienda 2 con la oferta de la tienda 1',
      $q$UPDATE detalle_pedido SET id_oferta = 1 WHERE id_pedido = 3 AND id_variante = 3$q$)),
 (47, pg_temp.debe_fallar('B47 pasar un producto a otra tienda',
      $q$UPDATE productos SET id_tienda = 2 WHERE id_producto = 1$q$)),
 -- Segunda revisión
 (48, pg_temp.debe_fallar('B48 descuento por unidad de S/ 200 en un producto de S/ 145 (la oferta da S/ 14.50)',
      $q$INSERT INTO detalle_pedido (id_pedido, id_variante, id_precio, id_oferta, nombre_producto, nombre_variante, cantidad, descuento_unitario)
         VALUES (2, 2, 2, 1, 'Zapatilla Runner', '43 / Blanco', 1, 200.00)$q$)),
 (49, pg_temp.debe_fallar('B49 descuento sin ninguna oferta aplicada',
      $q$INSERT INTO detalle_pedido (id_pedido, id_variante, id_precio, nombre_producto, nombre_variante, cantidad, descuento_unitario)
         VALUES (2, 2, 2, 'Zapatilla Runner', '43 / Blanco', 1, 5.00)$q$)),
 (50, pg_temp.debe_fallar('B50 aplicar una oferta pausada',
      $q$INSERT INTO detalle_pedido (id_pedido, id_variante, id_precio, id_oferta, nombre_producto, nombre_variante, cantidad, descuento_unitario)
         VALUES (2, 2, 2, 2, 'Zapatilla Runner', '43 / Blanco', 1, 14.50)$q$)),
 (51, pg_temp.debe_fallar('B51 pago aprobado de S/ 1 en un pedido de S/ 155',
      $q$INSERT INTO pagos (id_pedido, proveedor, monto, estado, fecha_pago) VALUES (2, 4, 1.00, 1, now())$q$)),
 (52, pg_temp.debe_fallar('B52 cambiar la fecha de cierre de un precio ya cerrado',
      $q$UPDATE precios SET fecha_fin = now() - interval '1 day' WHERE id_precio = 1$q$)),
 (53, pg_temp.debe_fallar('B53 cerrar un precio antes de una venta que ya lo usó',
      $q$UPDATE precios SET fecha_fin = now() - interval '1 hour' WHERE id_precio = 3$q$)),
 (54, pg_temp.debe_fallar('B54 pedido en una tienda suspendida',
      $q$INSERT INTO pedidos (id_tienda, id_direccion_envio, correo_contacto, subtotal)
         VALUES (3, 2, 'otro@mail.pe', 100)$q$)),
 -- Tercera revisión
 (55, pg_temp.debe_fallar('B55 editar el precio tachado de un precio ya creado',
      $q$UPDATE precios SET monto_comparacion = 250.00 WHERE id_precio = 1$q$)),
 (56, pg_temp.debe_fallar('B56 mover la fecha de cierre de una tasa de IGV ya cerrada',
      $q$UPDATE impuestos SET vigente_hasta = '2012-01-01' WHERE id_pais = 'PE' AND tasa = 19.00$q$)),
 (57, pg_temp.debe_fallar('B57 cerrar el IGV vigente antes de un pedido que ya lo usó',
      $q$UPDATE impuestos SET vigente_hasta = '2020-01-01' WHERE id_pais = 'PE' AND vigente_hasta IS NULL$q$)),
 (58, pg_temp.debe_fallar('B58 bajar el total de un pedido que ya tiene un pago aprobado',
      $q$UPDATE pedidos SET subtotal = 200.00, total = 200.00, monto_impuestos = 30.51 WHERE id_pedido = 1$q$)),
 -- Cuarta revisión: el IGV que se aplica es el vigente en la fecha del pedido
 (62, pg_temp.debe_fallar('B62 pedido de hoy con el IGV de 19 % que ya cerró en 2011',
      $q$INSERT INTO pedidos (id_tienda, id_direccion_envio, id_impuesto, correo_contacto, subtotal)
         VALUES (1, 2, (SELECT id_impuesto FROM impuestos WHERE id_pais = 'PE' AND tasa = 19.00), 'otro@mail.pe', 300)$q$)),
 (63, pg_temp.debe_fallar('B63 tasa de IGV cuyo periodo se cruza con otra',
      $q$INSERT INTO impuestos (id_pais, nombre, tasa, vigente_desde, vigente_hasta) VALUES ('PE', 'IGV', 17.00, '2010-01-01', '2012-01-01')$q$)),
 (64, pg_temp.debe_fallar('B64 pasar un pedido de hoy a la tasa de 19 % ya cerrada',
      $q$UPDATE pedidos SET id_impuesto = (SELECT id_impuesto FROM impuestos WHERE id_pais = 'PE' AND tasa = 19.00), monto_impuestos = 24.75 WHERE id_pedido = 2$q$)),
 -- Versión 2 · varios países
 (65, pg_temp.debe_fallar('B65 tienda de Chile con una tarifa de envío para Lima',
      $q$INSERT INTO tarifas_envio (id_tienda, id_division, costo) VALUES (4, pg_temp.div('PE', '15'), 5000)$q$)),
 (66, pg_temp.debe_fallar('B66 dirección en Perú (habilitado) escrita a mano, sin elegir el distrito',
      $q$INSERT INTO direcciones (id_login, id_pais, ciudad, destinatario, telefono, direccion)
         VALUES (2, 'PE', 'Lima', 'María', '+51999888777', 'Av. Larco 1')$q$)),
 (67, pg_temp.debe_fallar('B67 pedido de la tienda chilena con el IGV de Perú',
      $q$INSERT INTO pedidos (id_tienda, id_login, id_direccion_envio, id_impuesto, correo_contacto, subtotal)
         VALUES (4, 5, 5, (SELECT id_impuesto FROM impuestos WHERE id_pais = 'PE' AND vigente_hasta IS NULL), 'pedro@mail.cl', 19990)$q$)),
 (68, pg_temp.debe_fallar('B68 dirección en México sin código postal',
      $q$INSERT INTO direcciones (id_login, id_pais, id_division, destinatario, telefono, direccion)
         VALUES (6, 'MX', pg_temp.div('MX', '09015'), 'Lucía', '+525511112222', 'Calle Durango 100')$q$)),
 (69, pg_temp.debe_fallar('B69 código postal mexicano de 4 dígitos',
      $q$INSERT INTO direcciones (id_login, id_pais, id_division, destinatario, telefono, direccion, codigo_postal)
         VALUES (6, 'MX', pg_temp.div('MX', '09015'), 'Lucía', '+525511112222', 'Calle Durango 100', '0670')$q$)),
 (70, pg_temp.debe_fallar('B70 RUT chileno escrito con puntos',
      $q$UPDATE propietarios SET numero_documento = '12.345.678-5' WHERE id_propietario = 2$q$)),
 (71, pg_temp.debe_fallar('B71 comuna chilena colgada de una provincia peruana',
      $q$INSERT INTO divisiones (id_pais, nivel, id_padre, codigo, nombre)
         VALUES ('CL', 3, pg_temp.div('PE', '1501'), '13999', 'Mal')$q$)),
 (72, pg_temp.debe_fallar('B72 nivel 4 en México sin que exista el nivel 3',
      $q$INSERT INTO niveles_division (id_pais, nivel, nombre) VALUES ('MX', 4, 'Colonia')$q$)),
 (73, pg_temp.debe_fallar('B73 división en un nivel que México no tiene',
      $q$INSERT INTO divisiones (id_pais, nivel, id_padre, codigo, nombre)
         VALUES ('MX', 3, pg_temp.div('MX', '09015'), '0901500001', 'Roma Norte')$q$)),
 (74, pg_temp.debe_fallar('B74 dos tasas de IVA vigentes a la vez en Chile',
      $q$INSERT INTO impuestos (id_pais, nombre, tasa, vigente_desde) VALUES ('CL', 'IVA', 20.00, '2026-01-01')$q$)),
 (75, pg_temp.debe_fallar('B75 pasar una tienda de Perú a Chile',
      $q$UPDATE tiendas SET id_pais = 'CL' WHERE id_tienda = 1$q$)),
 (76, pg_temp.debe_fallar('B76 crear una tienda en un país no habilitado (Bolivia)',
      $q$INSERT INTO tiendas (id_propietario, id_pais, nombre, subdominio, correo_contacto)
         VALUES (1, 'BO', 'Tienda Bolivia', 'tienda-bolivia', 'a@mail.bo')$q$)),
 (77, pg_temp.debe_fallar('B77 país con una zona horaria que no existe',
      $q$UPDATE paises SET zona_horaria = 'America/Narnia' WHERE id_pais = 'UY'$q$)),
 (78, pg_temp.debe_fallar('B78 pedido de la tienda peruana a Japón (no tiene tarifa para ese país)',
      $q$INSERT INTO pedidos (id_tienda, id_direccion_envio, correo_contacto, subtotal)
         VALUES (1, 9, 'kenji@mail.jp', 100)$q$)),
 (79, pg_temp.debe_fallar('B79 cambiar el formato del RUC a uno que el RUC de Carlos no cumple',
      $q$UPDATE tipos_documento SET patron = '^[0-9]{8}$' WHERE id_tipo_documento = 2$q$)),
 (80, pg_temp.debe_fallar('B80 dirección chilena que apunta a la región y no a la comuna',
      $q$INSERT INTO direcciones (id_login, id_pais, id_division, destinatario, telefono, direccion)
         VALUES (5, 'CL', pg_temp.div('CL', '13'), 'Pedro', '+56911112222', 'Av. 1')$q$)),
 (81, pg_temp.debe_fallar('B81 tipo de documento con un patrón que no cubre todo el texto',
      $q$INSERT INTO tipos_documento (id_tipo_documento, id_pais, codigo, nombre, patron)
         VALUES (20, 'CL', 'PAS', 'Pasaporte', '[0-9]+')$q$)),
 (82, pg_temp.debe_fallar('B82 país con una moneda que no existe',
      $q$UPDATE paises SET id_moneda = 'XXX' WHERE id_pais = 'UY'$q$)),
 -- Versión 3 · ventas internacionales
 (83, pg_temp.debe_fallar('B83 tarifa internacional de una tienda peruana para el propio Perú',
      $q$INSERT INTO tarifas_internacionales (id_tienda, id_pais, costo) VALUES (1, 'PE', 10)$q$)),
 (84, pg_temp.debe_fallar('B84 venta a Chile (exportación) con IGV',
      $q$INSERT INTO pedidos (id_tienda, id_direccion_envio, id_impuesto, correo_contacto, subtotal)
         VALUES (1, 6, (SELECT id_impuesto FROM impuestos WHERE id_pais = 'PE' AND vigente_hasta IS NULL), 'otro@mail.cl', 100)$q$)),
 (85, pg_temp.debe_fallar('B85 dirección en Japón sin ciudad',
      $q$INSERT INTO direcciones (id_pais, region, destinatario, telefono, direccion)
         VALUES ('JP', 'Tokio', 'Kenji', '+819012345678', '1-2-3 Jingumae')$q$)),
 (86, pg_temp.debe_fallar('B86 dirección marcada en Chile con un distrito de Perú',
      $q$INSERT INTO direcciones (id_pais, id_division, destinatario, telefono, direccion)
         VALUES ('CL', pg_temp.div('PE', '150122'), 'Pedro', '+56911112222', 'Av. 1')$q$)),
 (87, pg_temp.debe_fallar('B87 dirección que elige el distrito y además escribe la ciudad',
      $q$INSERT INTO direcciones (id_pais, id_division, ciudad, destinatario, telefono, direccion)
         VALUES ('PE', pg_temp.div('PE', '150122'), 'Lima', 'María', '+51999888777', 'Av. 1')$q$)),
 (88, pg_temp.debe_fallar('B88 teléfono sin el formato internacional',
      $q$UPDATE login SET telefono = '987654321' WHERE id_login = 2$q$)),
 (89, pg_temp.debe_fallar('B89 pedido con moneda del cliente pero sin fecha del tipo de cambio',
      $q$UPDATE pedidos SET fecha_tipo_cambio = NULL WHERE id_pedido = 7$q$)),
 (90, pg_temp.debe_fallar('B90 pedido que dice haber usado un tipo de cambio que no existe',
      $q$UPDATE pedidos SET fecha_tipo_cambio = '2020-01-01' WHERE id_pedido = 7$q$)),
 (91, pg_temp.debe_fallar('B91 dos tipos de cambio PEN → CLP el mismo día',
      $q$INSERT INTO tipos_cambio (moneda_origen, moneda_destino, fecha, tasa) VALUES ('PEN', 'CLP', current_date, 280)$q$)),
 (92, pg_temp.debe_fallar('B92 cambiar un tipo de cambio que ya se mostró en un pedido',
      $q$UPDATE tipos_cambio SET tasa = 300 WHERE moneda_origen = 'PEN' AND moneda_destino = 'CLP'$q$)),
 (93, pg_temp.debe_fallar('B93 pedido de la tienda peruana cobrado en dólares',
      $q$INSERT INTO pedidos (id_tienda, id_direccion_envio, moneda, correo_contacto, subtotal)
         VALUES (1, 8, 'USD', 'john@mail.com', 100)$q$)),
 (94, pg_temp.debe_fallar('B94 habilitar Japón sin su zona horaria',
      $q$UPDATE paises SET estado = 1 WHERE id_pais = 'JP'$q$)),
 (95, pg_temp.debe_fallar('B95 tipo de cambio de una moneda a sí misma',
      $q$INSERT INTO tipos_cambio (moneda_origen, moneda_destino, fecha, tasa) VALUES ('PEN', 'PEN', current_date, 1)$q$)),
 -- Correcciones de la revisión de la versión 3
 (96, pg_temp.debe_fallar('B96 cambiar la moneda del Perú, que ya tiene tiendas',
      $q$UPDATE paises SET id_moneda = 'USD' WHERE id_pais = 'PE'$q$)),
 (97, pg_temp.debe_fallar('B97 cambiar la zona horaria del Perú, que ya tiene tiendas',
      $q$UPDATE paises SET zona_horaria = 'America/Bogota' WHERE id_pais = 'PE'$q$)),
 (98, pg_temp.debe_fallar('B98 deshabilitar el Perú y dejarlo sin zona horaria',
      $q$UPDATE paises SET estado = 0, zona_horaria = NULL WHERE id_pais = 'PE'$q$)),
 (99, pg_temp.debe_fallar('B99 nuevo formato de código postal de México que una dirección activa no cumple',
      $q$UPDATE paises SET patron_codigo_postal = '^[0-9]{6}$' WHERE id_pais = 'MX'$q$)),
 (100, pg_temp.debe_fallar('B100 habilitar Bolivia sin niveles, impuesto ni divisiones',
      $q$UPDATE paises SET zona_horaria = 'America/La_Paz', estado = 1 WHERE id_pais = 'BO'$q$)),
 (101, pg_temp.debe_fallar('B101 habilitar Argentina sin divisiones en su último nivel',
      $q$UPDATE paises SET estado = 1 WHERE id_pais = 'AR'$q$)),
 (102, pg_temp.debe_fallar('B102 tarifa en pesos chilenos con centavos',
      $q$UPDATE tarifas_envio SET costo = 3990.50 WHERE id_tienda = 4$q$)),
 (103, pg_temp.debe_fallar('B103 pedido en pesos chilenos con centavos',
      $q$INSERT INTO pedidos (id_tienda, id_login, id_direccion_envio, correo_contacto, subtotal)
         VALUES (4, 5, 5, 'pedro@mail.cl', 19990.55)$q$)),
 (104, pg_temp.debe_fallar('B104 pedido que muestra un tipo de cambio de una fecha futura',
      $q$UPDATE pedidos SET fecha_tipo_cambio = '2030-01-01' WHERE id_pedido = 7$q$)),
 -- Revisión final: latitud y longitud
 (105, pg_temp.debe_fallar('B105 latitud fuera de rango (95)',
      $q$INSERT INTO direcciones (id_login, id_pais, id_division, destinatario, telefono, direccion, latitud, longitud)
         VALUES (2, 'PE', pg_temp.div('PE', '150122'), 'María', '+51999888777', 'Av. 1', 95, -77.03)$q$)),
 (106, pg_temp.debe_fallar('B106 latitud sin longitud',
      $q$INSERT INTO direcciones (id_login, id_pais, id_division, destinatario, telefono, direccion, latitud)
         VALUES (2, 'PE', pg_temp.div('PE', '150122'), 'María', '+51999888777', 'Av. 1', -12.12)$q$)),
 (107, pg_temp.debe_fallar('B107 mover el pin de una dirección ya usada en un pedido',
      $q$UPDATE direcciones SET latitud = -12.200000 WHERE id_direccion = 1$q$)),
 -- Revisión final: correcciones
 (108, pg_temp.debe_fallar('B108 deshabilitar el Perú, que tiene tiendas',
      $q$UPDATE paises SET estado = 0 WHERE id_pais = 'PE'$q$)),
 (109, pg_temp.debe_fallar('B109 borrar el IGV vigente del Perú',
      $q$DELETE FROM impuestos WHERE id_pais = 'PE' AND vigente_hasta IS NULL$q$)),
 (110, pg_temp.debe_fallar('B110 habilitar Baréin (su moneda usa 3 decimales)',
      $q$UPDATE paises SET zona_horaria = 'Asia/Bahrain', estado = 1 WHERE id_pais = 'BH'$q$)),
 (111, pg_temp.debe_fallar('B111 cambiar los decimales del peso chileno, que usan tiendas',
      $q$UPDATE monedas SET decimales = 2 WHERE id_moneda = 'CLP'$q$)),
 (112, pg_temp.debe_fallar('B112 pasar el RUC (en uso) a Chile',
      $q$UPDATE tipos_documento SET id_pais = 'CL' WHERE id_tipo_documento = 2$q$)),
 (113, pg_temp.debe_fallar('B113 venta nacional en México a una dirección escrita a mano',
      $q$INSERT INTO pedidos (id_tienda, id_direccion_envio, correo_contacto, subtotal)
         VALUES (5, 11, 'invitado@mail.mx', 500)$q$))
) AS t (n, resultado)
ORDER BY n;

-- Casos permitidos que deben seguir funcionando
-- Cambio de IGV programado: se publica hoy que desde el 2099-01-01 la tasa
-- será 19 %. Se cierra el 18 % en esa fecha y se crea la nueva tasa. Un
-- pedido de hoy debe seguir con 18 %.
UPDATE impuestos SET vigente_hasta = '2099-01-01' WHERE id_pais = 'PE' AND vigente_hasta IS NULL;
INSERT INTO impuestos (id_pais, nombre, tasa, vigente_desde) VALUES ('PE', 'IGV', 19.00, '2099-01-01');
INSERT INTO pedidos (id_pedido, id_tienda, id_direccion_envio, correo_contacto, subtotal)
VALUES (4, 1, 2, 'invitado@mail.pe', 300.00);                                          -- envío gratis desde S/ 200
UPDATE direcciones SET estado = 0, predeterminada = FALSE WHERE id_direccion = 1;   -- archivar sí se puede
UPDATE pedidos SET estado = 1, notas = 'Enviado por Olva' WHERE id_pedido = 1;       -- cambiar el estado sí se puede
-- La tienda sube su tarifa de Lima a S/ 12. Corregir la facturación de un pedido
-- antiguo (que pagó S/ 10 de envío) debe seguir funcionando.
UPDATE tarifas_envio SET costo = 12.00
 WHERE id_tienda = 1 AND id_division = pg_temp.div('PE', '15');
UPDATE pedidos SET id_direccion_facturacion = 2 WHERE id_pedido = 2;
INSERT INTO pagos (id_pedido, proveedor, monto, estado, referencia_transaccion, fecha_pago)
VALUES (2, 4, 155.00, 1, 'yape-0002', now());                                         -- pago exacto sí se puede
INSERT INTO detalle_pedido (id_pedido, id_variante, id_precio, id_oferta, nombre_producto, nombre_variante, cantidad, descuento_unitario)
VALUES (1, 2, 2, 1, 'Zapatilla Runner', '43 / Blanco', 1, 14.50);                    -- oferta válida de su tienda
-- Con la línea anterior ya se vendió con la oferta 1
SELECT resultado FROM (VALUES
 (59, pg_temp.debe_fallar('B59 quitar de la oferta un producto que ya se vendió con ella',
      $q$DELETE FROM oferta_productos WHERE id_oferta = 1 AND id_producto = 1$q$)),
 (60, pg_temp.debe_fallar('B60 subir el descuento de una oferta con la que ya se vendió',
      $q$UPDATE ofertas SET valor = 50 WHERE id_oferta = 1$q$)),
 (61, pg_temp.debe_fallar('B61 adelantar el fin de una oferta antes de una venta',
      $q$UPDATE ofertas SET fecha_fin = now() - interval '1 second' WHERE id_oferta = 1$q$))
) AS t (n, resultado) ORDER BY n;
UPDATE ofertas SET estado = 0 WHERE id_oferta = 1;                                   -- pausarla sí se puede

-- Cambio de IVA en Chile y zona horaria del país: Chile pasa a 20 % desde el
-- 2099-01-01. Un pedido de la tienda chilena del 2099-01-01 a las 04:30 UTC
-- ya es 1 de enero en Santiago (UTC−3), aunque en Lima (UTC−5) todavía sea
-- 31 de diciembre: le corresponde el 20 %.
UPDATE impuestos SET vigente_hasta = '2099-01-01' WHERE id_pais = 'CL' AND vigente_hasta IS NULL;
INSERT INTO impuestos (id_pais, nombre, tasa, vigente_desde) VALUES ('CL', 'IVA', 20.00, '2099-01-01');
INSERT INTO pedidos (id_pedido, id_tienda, id_login, id_direccion_envio, correo_contacto, subtotal, fecha_creacion)
VALUES (6, 4, 5, 5, 'pedro@mail.cl', 60000.00, '2099-01-01 04:30+00');            -- envío gratis desde $50.000
-- Habilitar un país para crear tiendas se hace solo con datos (Uruguay ya
-- está en paises como destino: se le agrega su hora, niveles, impuesto y lista)
UPDATE paises SET zona_horaria = 'America/Montevideo', patron_codigo_postal = '^[0-9]{5}$' WHERE id_pais = 'UY';
INSERT INTO niveles_division (id_pais, nivel, nombre) VALUES ('UY', 1, 'Departamento'), ('UY', 2, 'Localidad');
INSERT INTO impuestos (id_pais, nombre, tasa, vigente_desde) VALUES ('UY', 'IVA', 22.00, '1995-01-01');
INSERT INTO divisiones (id_pais, nivel, id_padre, codigo, nombre) VALUES ('UY', 1, NULL, '10', 'Montevideo');
INSERT INTO divisiones (id_pais, nivel, id_padre, codigo, nombre)
SELECT 'UY', 2, id_division, '1001', 'Montevideo' FROM divisiones WHERE id_pais = 'UY' AND codigo = '10';
INSERT INTO tipos_documento (id_tipo_documento, id_pais, codigo, nombre, patron)
VALUES (13, 'UY', 'CI', 'Cédula de identidad', '^[0-9]{7,8}$');
UPDATE paises SET estado = 1 WHERE id_pais = 'UY';
-- Pedro (cliente de la tienda chilena) guarda también una dirección en Lima: ahora sí se puede
INSERT INTO direcciones (id_direccion, id_login, id_pais, id_division, destinatario, telefono, direccion)
VALUES (10, 5, 'PE', pg_temp.div('PE', '150122'), 'Pedro Soto', '+56911112222', 'Av. Larco 500');
-- La dirección del invitado de Surco (ya usada en el pedido 2) no tenía
-- coordenadas: el backend las obtiene después y las llena UNA vez (T9 lo permite)
UPDATE direcciones SET latitud = -12.145000, longitud = -76.991000 WHERE id_direccion = 2;
-- Un mismo correo es cliente en dos tiendas distintas (María, logins 2 y 3) y puede abrir una tercera cuenta en la de Chile
INSERT INTO login (id_login, id_rol, id_tienda, usuario, correo, contrasena_hash, nombres, primer_apellido)
VALUES (20, 3, 4, 'maria', 'maria@mail.pe', '$2a$10$hash', 'María', 'Huamán');

SELECT 'Permitidos' AS prueba,
       (SELECT estado FROM direcciones WHERE id_direccion = 1)    AS direccion_archivada,
       (SELECT estado FROM pedidos WHERE id_pedido = 1)           AS pedido_enviado,
       (SELECT count(*) FROM detalle_pedido WHERE id_oferta = 1)  AS lineas_con_oferta,
       (SELECT costo_envio FROM pedidos WHERE id_pedido = 2)      AS envio_pedido_antiguo,
       (SELECT id_direccion_facturacion FROM pedidos WHERE id_pedido = 2) AS facturacion_corregida,
       (SELECT count(*) FROM pagos WHERE id_pedido = 2 AND estado = 1)    AS pago_exacto,
       (SELECT estado FROM ofertas WHERE id_oferta = 1)                    AS oferta_pausada,
       (SELECT i.tasa FROM pedidos p JOIN impuestos i USING (id_impuesto) WHERE p.id_pedido = 4) AS igv_pedido_hoy,
       (SELECT i.tasa FROM pedidos p JOIN impuestos i USING (id_impuesto) WHERE p.id_pedido = 6) AS iva_chile_2099,
       (SELECT monto_impuestos FROM pedidos WHERE id_pedido = 6)          AS iva_chile_monto,
       (SELECT count(*) FROM direcciones WHERE id_direccion = 7)         AS direccion_mexico_ok,
       (SELECT string_agg(id_pais, ' ' ORDER BY id_pais) FROM divisiones WHERE codigo = '13') AS paises_con_codigo_13,
       (SELECT estado FROM paises WHERE id_pais = 'UY')                  AS uruguay_habilitado,
       (SELECT count(*) FROM direcciones WHERE id_direccion = 10)        AS direccion_en_otro_pais,
       (SELECT count(*) FROM login WHERE correo = 'maria@mail.pe')       AS cuentas_de_maria,
       (SELECT latitud || ', ' || longitud FROM direcciones WHERE id_direccion = 1) AS pin_de_maria,
       (SELECT latitud || ', ' || longitud FROM direcciones WHERE id_direccion = 2) AS pin_llenado_despues;
-- Esperado: … igv_pedido_hoy 18.00 · iva_chile_2099 20.00 · iva_chile_monto 10000.00
--           · direccion_mexico_ok 1 · paises_con_codigo_13 "CL CO EC MX PE" · uruguay_habilitado 1
--           · direccion_en_otro_pais 1 · cuentas_de_maria 3
--           · pin_de_maria -12.121100, -77.029700 · pin_llenado_despues -12.145000, -76.991000

ROLLBACK;
