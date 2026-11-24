-- ---------------------------------------------------------------------
--  MIGRACIÓN FLYWAY V10 · Intentos de pago vencidos
--
--  Flyway la aplica UNA sola vez y guarda su checksum en la tabla
--  flyway_schema_history. Por eso este archivo ya NO se edita: los
--  cambios van en una migración nueva (V11, V12…).
-- ---------------------------------------------------------------------
--
--  Cada vez que el cliente pulsa "Pagar" se guarda un intento PENDIENTE. Si va a Mercado Pago y
--  no paga, Mercado Pago no registra ningún pago, así que la conciliación (cada 15 minutos) no
--  tenía con qué cerrarlo: el intento quedaba pendiente para siempre y se volvía a consultar
--  en cada revisión, ocupando uno de los 50 lugares de la lista.
--
--  Nuevo estado 4 = VENCIDO: un intento que se deja de revisar porque nadie lo pagó (después de
--  unos días), porque la orden ya no existe en la pasarela, o porque el pedido ya se pagó con
--  otro intento o se canceló. No es un rechazo: el pedido sigue sin pagar y el cliente puede
--  volver a pagarlo (eso crea un intento nuevo). Si el pago llegara tarde, igual se registra.
-- =====================================================================

ALTER TABLE pagos DROP CONSTRAINT ck_pagos_estado;
ALTER TABLE pagos ADD CONSTRAINT ck_pagos_estado CHECK (estado BETWEEN 0 AND 4);

COMMENT ON COLUMN pagos.estado IS
    '0 pendiente · 1 aprobado · 2 rechazado · 3 reembolsado · 4 vencido (intento abandonado, se dejo de revisar)';
