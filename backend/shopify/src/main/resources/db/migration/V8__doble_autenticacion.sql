-- ---------------------------------------------------------------------
--  MIGRACIÓN FLYWAY V8 · Doble autenticación (códigos de 6 dígitos)
--
--  Flyway la aplica UNA sola vez y guarda su checksum en la tabla
--  flyway_schema_history. Por eso este archivo ya NO se edita: cambiar
--  una sola letra cambia el checksum y el backend no arranca. Los
--  cambios al esquema o a los datos van en una migración nueva (V9, V10…).
-- ---------------------------------------------------------------------
--
--  Con la doble autenticación, la contraseña sola ya no basta: además hay que escribir el
--  código de 6 dígitos que muestra una app como Google Authenticator, y que cambia cada 30
--  segundos. Quien robe una contraseña no puede entrar sin el teléfono del dueño.
-- =====================================================================

ALTER TABLE login
    -- El secreto que comparten el servidor y la app del teléfono (en base32, como lo pide la
    -- app). Se guarda al empezar la activación; mientras totp_activo sea FALSE todavía no se
    -- exige en el login, porque el dueño no confirmó que su app lo leyó bien.
    ADD COLUMN totp_secreto     VARCHAR(64)  NULL,
    ADD COLUMN totp_activo      BOOLEAN      NOT NULL DEFAULT FALSE,
    -- El último intervalo de 30 segundos cuyo código ya se usó. Un código no sirve dos veces:
    -- si alguien lo ve por encima del hombro, ya no le sirve para entrar.
    ADD COLUMN totp_ultimo_paso BIGINT       NULL;

-- No puede estar activa sin un secreto con qué comprobar los códigos.
ALTER TABLE login
    ADD CONSTRAINT ck_login_totp CHECK (totp_activo = FALSE OR totp_secreto IS NOT NULL);

COMMENT ON COLUMN login.totp_secreto IS
    'Secreto TOTP (RFC 6238) en base32. Nunca se escribe en los logs ni se devuelve despues de activarlo.';
