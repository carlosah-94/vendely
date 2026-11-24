-- ---------------------------------------------------------------------
--  MIGRACIÓN FLYWAY V10 · Empleados de la tienda (cuentas de personal)
--
--  Flyway la aplica UNA sola vez y guarda su checksum en la tabla
--  flyway_schema_history. Por eso este archivo ya NO se edita: cambiar
--  una sola letra cambia el checksum y el backend no arranca. Los
--  cambios al esquema o a los datos van en una migración nueva (V11…).
-- ---------------------------------------------------------------------
--
--  Como las "cuentas de personal" de Shopify: el dueño agrega empleados a su tienda y elige
--  qué partes del panel puede usar cada uno (pedidos, productos, inventario, clientes).
--  Lo que solo hace el dueño sigue igual: pagar el plan, personalizar la tienda, configurar
--  envíos, eliminar ventas y agregar o quitar empleados.
--
--  Es como una tienda física: el dueño tiene todas las llaves y a cada empleado le entrega
--  solo las de las puertas que necesita.
-- =====================================================================

-- 1. Rol nuevo. La cuenta de un empleado es del panel (id_tienda NULL en login, como la del
--    dueño): una misma persona puede trabajar en varias tiendas, y las tiendas donde trabaja
--    están en empleados_tienda.
INSERT INTO roles (id_rol, nombre, descripcion) VALUES
    (4, 'EMPLEADO', 'Empleado de una o varias tiendas: usa el panel con los permisos que le da el dueño');

-- 2. Cuántos empleados permite cada plan (como Shopify: el plan básico trae pocas cuentas
--    de personal y el avanzado más). 0 = solo el dueño.
ALTER TABLE planes
    ADD COLUMN max_empleados SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE planes
    ADD CONSTRAINT ck_planes_max_empleados CHECK (max_empleados BETWEEN 0 AND 1000);

UPDATE planes SET max_empleados = 0  WHERE id_plan = 1;   -- Gratis
UPDATE planes SET max_empleados = 2  WHERE id_plan = 2;   -- Básico
UPDATE planes SET max_empleados = 15 WHERE id_plan = 3;   -- Pro

-- 3. En qué tienda trabaja cada empleado y qué puede hacer ahí.
CREATE TABLE empleados_tienda (
    id_login            BIGINT        NOT NULL,
    -- Siempre 4. Va en la tabla para la FK compuesta (id_login, id_rol): así la base garantiza
    -- que solo una cuenta EMPLEADO sea empleado (no un cliente ni otro dueño), igual que
    -- propietarios lo garantiza para los dueños.
    id_rol              SMALLINT      NOT NULL DEFAULT 4,
    id_tienda           BIGINT        NOT NULL REFERENCES tiendas (id_tienda) ON DELETE CASCADE,
    puede_pedidos       BOOLEAN       NOT NULL DEFAULT FALSE,  -- ver, registrar, enviar, entregar y cancelar pedidos
    puede_productos     BOOLEAN       NOT NULL DEFAULT FALSE,  -- crear y editar productos, fotos y categorías
    puede_inventario    BOOLEAN       NOT NULL DEFAULT FALSE,  -- cambiar el stock
    puede_clientes      BOOLEAN       NOT NULL DEFAULT FALSE,  -- ver, crear y editar clientes
    estado              SMALLINT      NOT NULL DEFAULT 1,      -- 1 activo · 0 suspendido
    fecha_creacion      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_modificacion  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Llave compuesta: la misma persona no se agrega dos veces a la misma tienda,
    -- pero sí puede trabajar en dos tiendas distintas.
    CONSTRAINT pk_empleados_tienda PRIMARY KEY (id_login, id_tienda),
    CONSTRAINT fk_empleados_login
        FOREIGN KEY (id_login, id_rol) REFERENCES login (id_login, id_rol) ON DELETE CASCADE,
    CONSTRAINT ck_empleados_rol     CHECK (id_rol = 4),
    -- Un empleado sin ningún permiso no tiene nada que hacer en el panel.
    CONSTRAINT ck_empleados_permiso CHECK (puede_pedidos OR puede_productos OR puede_inventario OR puede_clientes),
    CONSTRAINT ck_empleados_estado  CHECK (estado IN (0, 1))
);

-- Para "los empleados de esta tienda" (pantalla del dueño y límite del plan).
CREATE INDEX ix_empleados_tienda ON empleados_tienda (id_tienda, estado);

CREATE TRIGGER tg_empleados_fecha BEFORE UPDATE ON empleados_tienda
    FOR EACH ROW EXECUTE FUNCTION fn_fecha_modificacion();

COMMENT ON TABLE empleados_tienda IS
    'Empleados de cada tienda y sus permisos en el panel. El dueño no va aqui: lo es por tiendas.id_propietario.';
COMMENT ON COLUMN planes.max_empleados IS
    'Cuantos empleados activos puede tener una tienda con este plan (0 = solo el dueño).';
