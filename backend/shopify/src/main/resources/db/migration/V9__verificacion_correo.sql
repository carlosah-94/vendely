-- ---------------------------------------------------------------------
--  MIGRACIÓN FLYWAY V9 · Verificación del correo al registrarse
--
--  Flyway la aplica UNA sola vez y guarda su checksum en la tabla
--  flyway_schema_history. Por eso este archivo ya NO se edita: los
--  cambios van en una migración nueva (V10, V11…).
-- ---------------------------------------------------------------------
--
--  Antes de crear la cuenta de un dueño se le envía un código de 6 dígitos a su correo, como
--  hace Shopify. Solo con ese código se crea la cuenta: así no quedan cuentas con correos que
--  no existen o que son de otra persona.
--
--  Hay una fila por correo con el último código pedido. Todavía no hay cuenta (por eso no
--  apunta a login): la fila se borra cuando la cuenta se crea con el código correcto.
-- =====================================================================

CREATE TABLE verificaciones_correo (
    -- En minúsculas y sin espacios, igual que login.correo.
    correo       VARCHAR(150) PRIMARY KEY,
    -- El código nunca se guarda tal cual: solo su hash (BCrypt, como las contraseñas).
    codigo_hash  VARCHAR(100) NOT NULL,
    -- Vence a los 10 minutos.
    expira_en    TIMESTAMPTZ  NOT NULL,
    -- Cuándo se envió. Sirve para no mandar otro antes de un minuto (no llenar el buzón ni
    -- gastar el cupo del proveedor de correos).
    enviado_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Códigos equivocados. Al quinto se debe pedir uno nuevo: así no se puede probar el
    -- millón de combinaciones.
    intentos     SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT ck_verificaciones_intentos CHECK (intentos >= 0)
);

COMMENT ON TABLE verificaciones_correo IS
    'Codigo de 6 digitos enviado al correo antes de crear la cuenta del dueno. Se borra al usarse.';
