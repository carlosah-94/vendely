-- ---------------------------------------------------------------------
--  MIGRACIÓN FLYWAY V7 · Identificador de la foto en Cloudinary
--
--  Flyway la aplica UNA sola vez y guarda su checksum en la tabla
--  flyway_schema_history. Por eso este archivo ya NO se edita: cambiar
--  una sola letra cambia el checksum y el backend no arranca. Los
--  cambios al esquema o a los datos van en una migración nueva (V8, V9…).
-- ---------------------------------------------------------------------
--
--  Hasta ahora una foto era solo un enlace. Con las fotos subidas a Cloudinary hace falta
--  además su identificador allá (public_id): es lo único con lo que se puede PEDIR QUE SE
--  BORRE. Sin esto, quitar una foto del producto la dejaría ocupando espacio en la cuenta
--  para siempre, y el plan gratuito tiene límite.
--
--  Es NULL en las fotos que son solo un enlace pegado (de otra página): esas no se borran
--  porque no son nuestras.
-- =====================================================================

ALTER TABLE imagenes_producto ADD COLUMN public_id VARCHAR(255) NULL;

COMMENT ON COLUMN imagenes_producto.public_id IS
    'Identificador de la foto en Cloudinary, para poder borrarla. NULL si es un enlace externo.';
