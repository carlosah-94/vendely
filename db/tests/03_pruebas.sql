-- =====================================================================
--  VENDELY · Pruebas del modelo (versión Perú)
--  Ejecutar después de 01_esquema.sql y 02_datos_iniciales.sql.
--  Todo corre dentro de una transacción que al final se deshace
--  (ROLLBACK), así que no deja datos en la base.
--
--  Parte A: un recorrido completo que DEBE funcionar.
--  Parte B: casos que la base DEBE rechazar.
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

-- ---------------------------------------------------------------------
-- PARTE A · Recorrido válido
-- ---------------------------------------------------------------------

-- A1. Carlos crea su cuenta del panel y registra su RUC
INSERT INTO login (id_login, id_rol, usuario, correo, contrasena_hash, nombres, apellido_pat, apellido_mat)
VALUES (1, 2, 'carlos', 'carlos@mail.pe', '$2a$10$hash', 'Carlos', 'Ramos', 'Soto');

INSERT INTO propietarios (id_propietario, id_login, tipo_documento, numero_documento, razon_social)
VALUES (1, 1, 2, '20123456789', 'Zapatillas Carlos S.A.C.');

-- A2. Crea dos tiendas
INSERT INTO tiendas (id_tienda, id_propietario, nombre, subdominio, correo_contacto) VALUES
 (1, 1, 'Zapatillas Carlos', 'zapatillas-carlos', 'ventas@zapatillas.pe'),
 (2, 1, 'Celulares Carlos',  'celulares-carlos',  'ventas@celulares.pe');

-- A3. Tarifas de envío por departamento
INSERT INTO tarifas_envio (id_tienda, id_departamento, costo, envio_gratis_desde, dias_entrega_min, dias_entrega_max)
SELECT 1, id_division, v.costo, v.gratis, v.dmin, v.dmax
  FROM (VALUES ('15', 10.00, 200.00, 1, 3), ('04', 18.00, NULL::NUMERIC, 3, 5)) AS v (cod, costo, gratis, dmin, dmax)
  JOIN divisiones ON codigo_ubigeo = v.cod;
-- Celulares Carlos solo envía a Lima (gratis desde S/ 500). Ninguna tienda envía a Cusco.
INSERT INTO tarifas_envio (id_tienda, id_departamento, costo, envio_gratis_desde, dias_entrega_min, dias_entrega_max)
SELECT 2, id_division, 15.00, 500.00, 1, 2 FROM divisiones WHERE codigo_ubigeo = '15';

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
INSERT INTO impuestos (nombre, tasa, vigente_desde, vigente_hasta) VALUES ('IGV', 19.00, '2004-08-01', '2011-03-01');

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
INSERT INTO tarifas_envio (id_tienda, id_departamento, costo)
SELECT 3, id_division, 10.00 FROM divisiones WHERE codigo_ubigeo = '15';

-- A5. María se registra como clienta en las dos tiendas, con el mismo correo
INSERT INTO login (id_login, id_rol, id_tienda, usuario, correo, contrasena_hash, nombres, apellido_pat) VALUES
 (2, 3, 1, 'maria', 'maria@mail.pe', '$2a$10$hash', 'María', 'Huamán'),
 (3, 3, 2, 'maria', 'maria@mail.pe', '$2a$10$hash', 'María', 'Huamán');

INSERT INTO direcciones (id_direccion, id_login, id_distrito, destinatario, telefono, direccion, predeterminada)
VALUES (1, 2, (SELECT id_division FROM divisiones WHERE codigo_ubigeo = '150122'),
        'María Huamán', '999888777', 'Av. Larco 123', TRUE);

-- Dirección de un invitado (sin cuenta), la de María en la tienda 2 y otra de María en Cusco
INSERT INTO direcciones (id_direccion, id_login, id_distrito, destinatario, telefono, direccion) VALUES
 (2, NULL, (SELECT id_division FROM divisiones WHERE codigo_ubigeo = '150140'), 'Invitado Pérez', '988777666', 'Av. Primavera 456'),
 (3, 3,    (SELECT id_division FROM divisiones WHERE codigo_ubigeo = '150122'), 'María Huamán', '999888777', 'Av. Larco 123'),
 (4, 2,    (SELECT id_division FROM divisiones WHERE codigo_ubigeo = '080101'), 'María Huamán', '999888777', 'Calle Plateros 10');

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
SELECT 'Pedidos' AS prueba, t.nombre AS tienda, p.numero_pedido, p.subtotal, p.costo_envio, p.total,
       p.monto_impuestos AS igv_incluido, i.nombre || ' ' || i.tasa || ' %' AS impuesto
  FROM pedidos p JOIN tiendas t USING (id_tienda) JOIN impuestos i USING (id_impuesto)
 ORDER BY p.id_pedido;
-- Esperado: Zapatillas #1001 (envío 0, IGV 44.24) y #1002 (envío 10, total 155, IGV 23.64)
--           · Celulares #1001 (IGV 304.93) · IGV 18.00 %

SELECT 'Ubigeo de la dirección' AS prueba,
       dis.nombre AS distrito, pro.nombre AS provincia, dep.nombre AS departamento
  FROM direcciones d
  JOIN divisiones dis ON dis.id_division = d.id_distrito
  JOIN divisiones pro ON pro.id_division = dis.id_padre
  JOIN divisiones dep ON dep.id_division = pro.id_padre;

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
      $q$INSERT INTO login (id_rol, usuario, correo, contrasena_hash, nombres, apellido_pat)
         VALUES (2, 'carlos2', 'carlos@mail.pe', 'x', 'Otro', 'Carlos')$q$)),
 (7,  pg_temp.debe_fallar('B07 cuenta de cliente sin tienda',
      $q$INSERT INTO login (id_rol, usuario, correo, contrasena_hash, nombres, apellido_pat)
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
      $q$INSERT INTO divisiones (nivel, id_padre, codigo_ubigeo, nombre)
         VALUES (3, (SELECT id_division FROM divisiones WHERE codigo_ubigeo = '15'), '159999', 'Mal')$q$)),
 (17, pg_temp.debe_fallar('B17 código de ubigeo con largo incorrecto para su nivel',
      $q$INSERT INTO divisiones (nivel, id_padre, codigo_ubigeo, nombre) VALUES (1, NULL, '150', 'Mal')$q$)),
 (18, pg_temp.debe_fallar('B18 dirección que apunta a una provincia y no a un distrito',
      $q$INSERT INTO direcciones (id_login, id_distrito, destinatario, telefono, direccion)
         VALUES (2, (SELECT id_division FROM divisiones WHERE codigo_ubigeo = '1501'), 'X', '9', 'Calle 1')$q$)),
 (19, pg_temp.debe_fallar('B19 tarifa de envío para un distrito y no para un departamento',
      $q$INSERT INTO tarifas_envio (id_tienda, id_departamento, costo)
         VALUES (1, (SELECT id_division FROM divisiones WHERE codigo_ubigeo = '150122'), 5)$q$)),
 (20, pg_temp.debe_fallar('B20 dos tarifas de la misma tienda para Lima',
      $q$INSERT INTO tarifas_envio (id_tienda, id_departamento, costo)
         VALUES (1, (SELECT id_division FROM divisiones WHERE codigo_ubigeo = '15'), 12)$q$)),
 (21, pg_temp.debe_fallar('B21 dos tasas de IGV vigentes a la vez',
      $q$INSERT INTO impuestos (nombre, tasa, vigente_desde) VALUES ('IGV', 19.00, '2026-01-01')$q$)),
 (22, pg_temp.debe_fallar('B22 editar la tasa del IGV',
      $q$UPDATE impuestos SET tasa = 20.00 WHERE nombre = 'IGV'$q$)),
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
      $q$UPDATE impuestos SET vigente_hasta = '2012-01-01' WHERE tasa = 19.00$q$)),
 (57, pg_temp.debe_fallar('B57 cerrar el IGV vigente antes de un pedido que ya lo usó',
      $q$UPDATE impuestos SET vigente_hasta = '2020-01-01' WHERE vigente_hasta IS NULL$q$)),
 (58, pg_temp.debe_fallar('B58 bajar el total de un pedido que ya tiene un pago aprobado',
      $q$UPDATE pedidos SET subtotal = 200.00, total = 200.00, monto_impuestos = 30.51 WHERE id_pedido = 1$q$)),
 -- Cuarta revisión: el IGV que se aplica es el vigente en la fecha del pedido
 (62, pg_temp.debe_fallar('B62 pedido de hoy con el IGV de 19 % que ya cerró en 2011',
      $q$INSERT INTO pedidos (id_tienda, id_direccion_envio, id_impuesto, correo_contacto, subtotal)
         VALUES (1, 2, (SELECT id_impuesto FROM impuestos WHERE tasa = 19.00), 'otro@mail.pe', 300)$q$)),
 (63, pg_temp.debe_fallar('B63 tasa de IGV cuyo periodo se cruza con otra',
      $q$INSERT INTO impuestos (nombre, tasa, vigente_desde, vigente_hasta) VALUES ('IGV', 17.00, '2010-01-01', '2012-01-01')$q$)),
 (64, pg_temp.debe_fallar('B64 pasar un pedido de hoy a la tasa de 19 % ya cerrada',
      $q$UPDATE pedidos SET id_impuesto = (SELECT id_impuesto FROM impuestos WHERE tasa = 19.00), monto_impuestos = 24.75 WHERE id_pedido = 2$q$))
) AS t (n, resultado)
ORDER BY n;

-- Casos permitidos que deben seguir funcionando
-- Cambio de IGV programado: se publica hoy que desde el 2099-01-01 la tasa
-- será 19 %. Se cierra el 18 % en esa fecha y se crea la nueva tasa. Un
-- pedido de hoy debe seguir con 18 %.
UPDATE impuestos SET vigente_hasta = '2099-01-01' WHERE nombre = 'IGV' AND vigente_hasta IS NULL;
INSERT INTO impuestos (nombre, tasa, vigente_desde) VALUES ('IGV', 19.00, '2099-01-01');
INSERT INTO pedidos (id_pedido, id_tienda, id_direccion_envio, correo_contacto, subtotal)
VALUES (4, 1, 2, 'invitado@mail.pe', 300.00);                                          -- envío gratis desde S/ 200
UPDATE direcciones SET estado = 0, predeterminada = FALSE WHERE id_direccion = 1;   -- archivar sí se puede
UPDATE pedidos SET estado = 1, notas = 'Enviado por Olva' WHERE id_pedido = 1;       -- cambiar el estado sí se puede
-- La tienda sube su tarifa de Lima a S/ 12. Corregir la facturación de un pedido
-- antiguo (que pagó S/ 10 de envío) debe seguir funcionando.
UPDATE tarifas_envio SET costo = 12.00
 WHERE id_tienda = 1 AND id_departamento = (SELECT id_division FROM divisiones WHERE codigo_ubigeo = '15');
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

SELECT 'Permitidos' AS prueba,
       (SELECT estado FROM direcciones WHERE id_direccion = 1)    AS direccion_archivada,
       (SELECT estado FROM pedidos WHERE id_pedido = 1)           AS pedido_enviado,
       (SELECT count(*) FROM detalle_pedido WHERE id_oferta = 1)  AS lineas_con_oferta,
       (SELECT costo_envio FROM pedidos WHERE id_pedido = 2)      AS envio_pedido_antiguo,
       (SELECT id_direccion_facturacion FROM pedidos WHERE id_pedido = 2) AS facturacion_corregida,
       (SELECT count(*) FROM pagos WHERE id_pedido = 2 AND estado = 1)    AS pago_exacto,
       (SELECT estado FROM ofertas WHERE id_oferta = 1)                    AS oferta_pausada,
       (SELECT i.tasa FROM pedidos p JOIN impuestos i USING (id_impuesto) WHERE p.id_pedido = 4) AS igv_pedido_hoy;

ROLLBACK;
