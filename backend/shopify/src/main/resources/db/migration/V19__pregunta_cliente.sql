-- V19: una pregunta al cliente por producto, y su respuesta en cada línea del pedido.
--
--   Muchos negocios necesitan un dato para preparar el pedido: el texto que va en la torta,
--   el nombre para grabar en una taza, la talla de anillo. Shopify lo resuelve con apps de
--   "line item properties"; aquí es parte del producto.
--
--   productos.pregunta_cliente  la pregunta ("¿Qué texto va en la torta?"). NULL = no pregunta nada.
--   detalle_pedido.dato_cliente lo que respondió el cliente para esa línea.
ALTER TABLE productos ADD COLUMN pregunta_cliente VARCHAR(120) NULL;
ALTER TABLE detalle_pedido ADD COLUMN dato_cliente VARCHAR(300) NULL;
