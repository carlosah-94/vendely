-- =====================================================================
-- Vendely - Usuarios de prueba
-- Se ejecuta después de 01_esquema.sql y 02_datos_iniciales.sql.
--
-- Crea una cuenta por cada rol para poder entrar al sistema sin
-- registrarse:
--
--   Rol            Correo               Contraseña    Tienda
--   SUPERADMIN     admin@vendely.pe     Admin1234     -
--   ADMIN_TIENDA   dueno@vendely.pe     Dueno1234     moda-lima (es el dueño)
--   CLIENTE        cliente@vendely.pe   Cliente1234   moda-lima
--
-- Las contraseñas se guardan con BCrypt (costo 10), igual que las
-- cuentas que se registran desde la API. El texto de arriba es solo
-- para las pruebas; en la base nunca se guarda la contraseña real.
--
-- Los ids empiezan en 9001 para no chocar con los ids fijos que usa
-- db/tests/03_pruebas.sql ni con las cuentas que crea la API (esas
-- usan la secuencia, que empieza en 1).
-- =====================================================================

BEGIN;

-- 1. Superadmin: cuenta del panel sin tienda (id_tienda NULL)
INSERT INTO login (id_login, id_rol, id_tienda, usuario, correo, contrasena_hash,
                   nombres, primer_apellido, segundo_apellido, telefono)
VALUES (9001, 1, NULL, 'admin', 'admin@vendely.pe',
        '$2a$10$haNo9.WsXTL8WkrqeikMc.BpVyFgbssuZH5BHSpSRf8C9RbbkIH.W',
        'Administrador', 'Vendely', NULL, NULL)
ON CONFLICT DO NOTHING;

-- 2. Dueño de tienda: cuenta del panel + sus datos de propietario
INSERT INTO login (id_login, id_rol, id_tienda, usuario, correo, contrasena_hash,
                   nombres, primer_apellido, segundo_apellido, telefono)
VALUES (9002, 2, NULL, 'dueno', 'dueno@vendely.pe',
        '$2a$10$yNSl/ZsQI2udyA2M0RPlxeh2dsNo1uOc1uQMKr.Uik4uQ0SkyeUuy',
        'Ana', 'Torres', 'Ríos', '+51987654321')
ON CONFLICT DO NOTHING;

INSERT INTO propietarios (id_propietario, id_login, id_rol, tipo_documento, numero_documento, razon_social)
VALUES (9001, 9002, 2, 2, '20601234567', 'Moda Lima S.A.C.')   -- tipo 2 = RUC
ON CONFLICT DO NOTHING;

-- 3. Tienda del dueño (Perú)
INSERT INTO tiendas (id_tienda, id_propietario, id_pais, nombre, subdominio, descripcion,
                     correo_contacto, color_principal, permite_invitados)
VALUES (9001, 9001, 'PE', 'Moda Lima', 'moda-lima',
        'Ropa y accesorios con envío a todo el Perú',
        'ventas@modalima.pe', '#7C3AED', TRUE)
ON CONFLICT DO NOTHING;

-- 4. Cliente registrado en la tienda moda-lima
INSERT INTO login (id_login, id_rol, id_tienda, usuario, correo, contrasena_hash,
                   nombres, primer_apellido, segundo_apellido, telefono, acepta_marketing)
VALUES (9003, 3, 9001, 'cliente', 'cliente@vendely.pe',
        '$2a$10$rIjzbGsqHULR5jDtt1thueyoUL25ErFhKtEZNGhZkBUddDeBdt8sC',
        'Luis', 'Vega', 'Paredes', '+51912345678', TRUE)
ON CONFLICT DO NOTHING;

-- Dirección del cliente de prueba (Miraflores, Lima): una venta necesita dirección de envío
INSERT INTO direcciones (id_direccion, id_login, id_pais, id_division, destinatario, telefono,
                         direccion, referencia, predeterminada)
SELECT 9001, 9003, 'PE', id_division, 'Luis Vega', '+51912345678',
       'Av. Larco 123', 'Frente al parque', TRUE
  FROM divisiones WHERE id_pais = 'PE' AND codigo = '150122'
ON CONFLICT DO NOTHING;

-- Tarifa de envío a todos los departamentos del Perú: sin tarifa, la base rechaza la venta
INSERT INTO tarifas_envio (id_tienda, id_division, costo, envio_gratis_desde, dias_entrega_min, dias_entrega_max)
SELECT 9001, id_division, 10.00, 200.00, 1, 5
  FROM divisiones WHERE id_pais = 'PE' AND nivel = 1
ON CONFLICT DO NOTHING;

COMMIT;