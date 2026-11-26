-- =============================================================================
--  MIGRACIÓN FLYWAY V26 · Archivos adjuntos en los mensajes con soporte
--
--  Hasta V17 la conversación con soporte era solo texto: para mandar el Excel de productos
--  o las fotos, el dueño tenía que subirlos a Drive y pegar el enlace. Ahora cada mensaje
--  puede llevar UN archivo (Excel, CSV, PDF, Word, fotos o un ZIP, hasta 10 MB) que se
--  guarda en Cloudinary; aquí queda su enlace y sus datos para mostrarlo en el chat.
--
--  Un mensaje con archivo puede ir sin texto ("aquí va el Excel" no siempre hace falta),
--  por eso la regla del texto pasa a ser: texto o archivo, al menos uno de los dos.
-- =============================================================================

ALTER TABLE soporte_mensajes
    ADD COLUMN IF NOT EXISTS adjunto_url        VARCHAR(500)  NULL,   -- enlace de entrega en Cloudinary
    ADD COLUMN IF NOT EXISTS adjunto_public_id  VARCHAR(300)  NULL,   -- para borrarlo si hiciera falta
    ADD COLUMN IF NOT EXISTS adjunto_nombre     VARCHAR(200)  NULL,   -- el nombre original: "productos.xlsx"
    ADD COLUMN IF NOT EXISTS adjunto_tipo       VARCHAR(100)  NULL,   -- tipo MIME declarado
    ADD COLUMN IF NOT EXISTS adjunto_bytes      BIGINT        NULL;

ALTER TABLE soporte_mensajes DROP CONSTRAINT IF EXISTS ck_soporte_mensajes_texto;
ALTER TABLE soporte_mensajes
    ADD CONSTRAINT ck_soporte_mensajes_texto
    CHECK (length(btrim(texto)) > 0 OR adjunto_url IS NOT NULL);
