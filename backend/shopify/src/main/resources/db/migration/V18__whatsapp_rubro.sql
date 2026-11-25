-- V17: WhatsApp de la tienda y rubro (qué vende).
--   whatsapp: número con código de país (+51987654321). Lo usa el botón "Escríbenos por WhatsApp"
--             de la vitrina. NULL = la tienda no muestra el botón.
--   rubro:    qué vende la tienda. Con él, al crearla, Vendely le arma un "kit" de inicio
--             (categorías sugeridas, descripción y anuncio) pensado para ese negocio.
ALTER TABLE tiendas
    ADD COLUMN whatsapp VARCHAR(16) NULL,
    ADD COLUMN rubro    VARCHAR(20) NULL,
    ADD CONSTRAINT ck_tiendas_whatsapp CHECK (whatsapp ~ '^\+[1-9][0-9]{7,14}$'),
    ADD CONSTRAINT ck_tiendas_rubro CHECK (rubro IN
        ('moda', 'tecnologia', 'comida', 'belleza', 'hogar', 'deporte', 'mascotas', 'otro'));
