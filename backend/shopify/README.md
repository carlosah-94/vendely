# Vendely: Backend (API REST)

API del proyecto Vendely (clon de Shopify). Permite que un dueño cree su tienda, administre productos, clientes y ventas, y que un comprador vea el catálogo público, haga pedidos y pague.

## Stack

- Java 21, Spring Boot 3.3.4
- Spring Security + JWT (jjwt)
- Spring Data JPA (Hibernate con `ddl-auto: validate`) y PostgreSQL 16
- Flyway: crea y versiona el esquema al arrancar
- Jakarta Bean Validation y Lombok
- Mercado Pago (Checkout Pro, modo de prueba)

## Cómo ejecutarlo

1. Levanta la base desde la raíz del repositorio: `docker compose up -d db`. Queda vacía: las tablas y los datos los crea Flyway al arrancar el backend, en el paso 3.
2. Copia `.env.example` a `.env` y completa los valores (ver abajo).
3. Desde esta carpeta: `mvn spring-boot:run`, o abre el proyecto en NetBeans y usa **Clean and Build** y **Run**.

La API queda en `http://localhost:8080/api`.

### Variables de entorno

| Variable | Para qué sirve | Por defecto |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Conexión a PostgreSQL | `localhost:5433/shopify_db` |
| `JWT_SECRET` | Firma de los tokens (mínimo 32 caracteres) | valor de ejemplo, **cámbialo** |
| `JWT_EXPIRATION` | Vigencia del token en milisegundos | 86400000 (24 h) |
| `CORS_ORIGENES` | Frontends autorizados, separados por coma | `localhost:5173,5174` |
| `MERCADOPAGO_ACCESS_TOKEN` | Token de **prueba** (`TEST-...`). Sin él, el pago responde 503 | vacío |
| `MERCADOPAGO_WEBHOOK_SECRET` | Clave con la que Mercado Pago firma sus avisos. Sin ella, su webhook responde 503 | vacío |
| `PAYPAL_WEBHOOK_ID` | Id del webhook de PayPal, para preguntarle si un aviso es auténtico | vacío |
| `RESEND_API_KEY` | Clave de resend.com. Sin ella los correos se registran como "simulado" | vacío |
| `RESEND_REMITENTE` | Remitente de los correos | `Vendely <onboarding@resend.dev>` |
| `FRONTEND_URL` | Adonde vuelve el cliente tras pagar | `http://localhost:5173` |
| `FLYWAY_LOCATIONS` | Carpetas de migraciones. En el servidor: `classpath:db/migration` (sin los usuarios de prueba) | las dos carpetas |
| `SERVER_PORT` | Puerto de la API | 8080 |
| `JPA_SHOW_SQL` | Muestra el SQL en consola (solo para depurar) | `false` |

## Arquitectura

Paquete base: `curso.fullstack.shopify`

| Capa | Qué hace |
|---|---|
| `controller` | Recibe las peticiones HTTP |
| `service` | Reglas del negocio y transacciones (`AuthService`, `ClienteService`, `ProductoService`, `PedidoService`, `PagoService`, `PagoWebhookService`, `ConciliacionPagosService`, `TiendaService`, `MercadoPagoClient`, `PayPalClient`) |
| `repository` | Acceso a la base con Spring Data JPA |
| `entity` | Clases que representan las tablas |
| `dto` | Datos que entran y salen de la API, con sus validaciones |
| `security` | JWT, filtro de autenticación, roles y `VerificadorTienda` |
| `exception` | Formato único de errores (`ApiError`, `GlobalExceptionHandler`) |

## Seguridad y roles

| Ruta | Quién entra |
|---|---|
| `/api/auth/registro/**` y `/api/auth/login` | Todos |
| `GET /api/public/**` | Todos |
| `POST /api/public/pagos/webhook/*` | Las pasarelas (lo que autoriza es la firma del aviso, no un token) |
| `/api/panel/**` | `ADMIN_TIENDA` (dueño) |
| `/api/cliente/**` | `CLIENTE` |
| `/api/admin/**` | `SUPERADMIN` (reservado, sin endpoints todavía) |
| Cualquier otra | Usuarios con token |

- Contraseñas con BCrypt; la API nunca devuelve el hash.
- El token lleva id de cuenta, rol y tienda. Cambiar la contraseña invalida los tokens anteriores.
- Multi-tienda: en las rutas del cliente, la tienda sale del token, no del cuerpo de la petición. En las del panel, `VerificadorTienda` comprueba que la tienda de la URL pertenezca al dueño.
- Mismo mensaje de error para correo o contraseña incorrectos.
- Sin CSRF, porque el JWT va en la cabecera `Authorization` y no en cookies.

## Endpoints

### Autenticación (`/api/auth`)

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/registro/dueno` | Crea la cuenta de dueño y su primera tienda |
| POST | `/registro/cliente` | Crea un cliente en una tienda (por `subdominio`) |
| POST | `/login` | Sin `subdominio`: panel. Con `subdominio`: cliente de esa tienda |
| GET | `/yo` | Datos de quien inició sesión |
| POST | `/cambiar-contrasena` | Cambia la contraseña y entrega un token nuevo |

### Panel del dueño (`ADMIN_TIENDA`)

| Método | Ruta | Descripción |
|---|---|---|
| GET, POST | `/api/panel/tiendas` | Lista mis tiendas / crea una tienda |
| GET, PUT | `/api/panel/tiendas/{idTienda}` | Consulta / actualiza una tienda |
| GET, POST | `/api/panel/tiendas/{idTienda}/clientes` | Lista / crea clientes |
| GET, PUT, DELETE | `/api/panel/tiendas/{idTienda}/clientes/{idCliente}` | Consulta, actualiza o elimina un cliente |
| GET, POST | `/api/panel/tiendas/{idTienda}/productos` | Lista / crea productos (con precio y stock) |
| GET, PUT, DELETE | `/api/panel/tiendas/{idTienda}/productos/{idProducto}` | Consulta, actualiza o elimina un producto |
| GET, POST | `/api/panel/tiendas/{idTienda}/pedidos` | Lista / registra ventas |
| GET, DELETE | `/api/panel/tiendas/{idTienda}/pedidos/{idPedido}` | Detalle / elimina una venta (devuelve el stock) |

### Tienda pública (sin token)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/public/tiendas/{subdominio}` | Datos de la tienda |
| GET | `/api/public/tiendas/{subdominio}/productos` | Catálogo, con filtros y paginación |
| GET | `/api/public/tiendas/{subdominio}/productos/{idProducto}` | Detalle de un producto |
| GET | `/api/public/paises/{idPais}/distritos` | Distritos para las direcciones |
| POST | `/api/public/pagos/webhook/mercadopago` | Aviso de Mercado Pago (firmado) |
| POST | `/api/public/pagos/webhook/paypal` | Aviso de PayPal (firmado) |

### Cliente (`CLIENTE`)

| Método | Ruta | Descripción |
|---|---|---|
| GET, POST | `/api/cliente/direcciones` | Lista / agrega direcciones |
| GET, POST | `/api/cliente/pedidos` | Mis pedidos / crea un pedido |
| GET | `/api/cliente/pedidos/{idPedido}` | Detalle de un pedido |
| POST | `/api/cliente/pedidos/{idPedido}/pago` | Inicia el pago en Mercado Pago |
| POST | `/api/cliente/pedidos/{idPedido}/pago/confirmar` | Confirma el resultado del pago |
| GET | `/api/cliente/pedidos/{idPedido}/comprobante` | Su comprobante de compra en PDF |

## Catálogo: filtros y paginación

`GET /api/public/tiendas/{subdominio}/productos` acepta estos parámetros, todos opcionales:

| Parámetro | Qué hace |
|---|---|
| `q` | Busca en nombre, marca y descripción (sin distinguir mayúsculas) |
| `categoria` | Id de la categoría |
| `min` / `max` | Rango del precio vigente |
| `stock` | `1` deja solo los que tienen stock |
| `orden` | `nombre` (por defecto), `precio_asc`, `precio_desc`, `novedades` |
| `pagina` | Empieza en 1 |
| `tamano` | 12 por defecto, 48 como máximo |

Responde una página, no un arreglo:

```json
{ "contenido": [...], "pagina": 1, "tamano": 12, "total": 18, "paginas": 2, "hayMas": true }
```

Tres decisiones que vale la pena conocer antes de tocar esto:

1. **El precio y el stock se filtran en `HAVING`, no en `WHERE`.** Un producto puede tener varias variantes: su precio es el menor (`min`) y su stock la suma. Son agregados, y `WHERE` se evalúa antes de agrupar.
2. **Cada parámetro opcional va con `CAST(:x AS tipo)`.** En una consulta nativa, PostgreSQL no puede deducir el tipo de un parámetro que llega nulo y la consulta falla con *could not determine data type*.
3. **El orden se elige con un `CASE` por parámetro**, no pegando texto al SQL: lo que llega del navegador nunca se concatena a la consulta. Y `FiltroCatalogo.normalizado()` descarta un orden inventado, ignora un rango de precios al revés y limita el tamaño de la página, así que nadie puede pedir cien mil productos de una vez.

## Pedidos: envío, correos y comprobante

### Seguimiento del envío

| Método | Ruta | Qué hace |
|---|---|---|
| PATCH | `/api/panel/tiendas/{idTienda}/pedidos/{idPedido}/envio` | Marca como enviado (pide empresa y número) |
| PATCH | `/api/panel/tiendas/{idTienda}/pedidos/{idPedido}/entrega` | Marca como entregado |
| PATCH | `/api/panel/tiendas/{idTienda}/pedidos/{idPedido}/cancelacion` | Cancela y devuelve el stock |
| GET | `/api/panel/tiendas/{idTienda}/pedidos/{idPedido}/comprobante` | El comprobante de la venta en PDF |
| GET | `/api/panel/tiendas/{idTienda}/pedidos/{idPedido}/notificaciones` | Los correos de ese pedido |

Los estados son los de la columna `pedidos.estado`: 0 sin enviar, 1 enviado, 2 entregado, 3 cancelado. Las reglas las aplica `PedidoService` y son tres:

1. **Solo un pedido pagado puede enviarse.** Despachar mercadería sin haber cobrado es una pérdida; es la misma razón por la que un pedido pagado tampoco se puede eliminar.
2. **Solo un pedido enviado puede entregarse.**
3. **Cancelar solo antes de enviar.** Devuelve el stock al inventario y, si ya se había cobrado, deja el pago como `REEMBOLSADO` (el dinero lo devuelve el dueño en la pasarela; Vendely solo lo registra). Todo en una transacción: nunca queda un pedido cancelado con el stock sin devolver.

Una acción en el estado equivocado responde 409 diciendo en qué estado está el pedido. Cada cambio publica `PedidoEstadoCambiadoEvent` y guarda su fecha (`fecha_envio`, `fecha_entrega`, `fecha_cancelacion`), que es lo que el cliente ve como línea de tiempo.

Al aprobarse el pago, `PagoService` guarda además `comision_plataforma`: el total por la comisión del plan que la tienda tiene **en ese momento**, para que un cambio de plan posterior no altere lo ya cobrado.

### Correos (`NotificacionService`)

| Cuándo | A quién | Qué lleva |
|---|---|---|
| Pago aprobado | Al cliente | La confirmación, con el comprobante en PDF adjunto |
| Pago aprobado | Al dueño | El aviso de venta nueva |
| Pedido enviado | Al cliente | La empresa y el número de seguimiento |
| Stock bajo el mínimo | Al dueño | Qué producto y cuánto queda |

Tres decisiones que explican el diseño:

- **En segundo plano** (`@Async`), disparados por `NotificacionListener` desde los eventos del negocio con `@TransactionalEventListener`: el correo sale solo si la transacción se guardó de verdad, y el cliente que acaba de pagar no espera al proveedor de correo. `PedidoService` y `PagoService` no saben que el correo existe.
- **Cada intento se registra** en la tabla `notificaciones` con su estado: enviado, fallido (con el motivo) o simulado. Un correo que no llegó y no deja rastro es un problema que nadie puede atender. Si Resend falla, el pedido sigue su curso.
- **Sin `RESEND_API_KEY` no se envía nada**: se registra como `SIMULADO`. Así el proyecto corre completo en cualquier máquina del equipo sin crear una cuenta.

El cuerpo es HTML con estilos en línea (los clientes de correo ignoran las hojas de estilo) y lleva el nombre y el color de la tienda. Todo texto que viene de la base se escapa: un nombre de producto con `<` no puede romper el correo. En el log los correos van enmascarados (`c***@vendely.pe`).

### Comprobante en PDF (`ComprobanteService`)

Se genera con OpenPDF e incluye los datos de la tienda y del cliente, la dirección de envío, los productos con el precio que **se cobró** (se lee por `id_precio`, no el precio actual), el valor sin impuesto, el impuesto incluido con su nombre (IGV o IVA según el país), el total y el método de pago. Lleva impresa la advertencia *"Comprobante interno, no válido como comprobante SUNAT"*: emitir una boleta o factura de verdad exige estar autorizado ante la SUNAT.

Se descarga desde el panel y desde "Mis pedidos", y se adjunta al correo de confirmación. El del cliente se busca con el `idLogin` del token: pedir el comprobante de otro cliente responde 404.

## Avisos de pago (webhook)

Un pago no puede depender de que el comprador vuelva a la pantalla: si cierra la pestaña en la pasarela, el pedido se quedaría pendiente con el dinero ya cobrado. Hay dos respaldos para eso.

**1. El aviso de la pasarela.** Mercado Pago y PayPal llaman a su propia ruta en cuanto el pago cambia de estado:

| Ruta | Quién la llama | Qué la protege |
|---|---|---|
| `POST /api/public/pagos/webhook/mercadopago` | Mercado Pago | Cabecera `x-signature`: un HMAC-SHA256 que se recalcula con `MERCADOPAGO_WEBHOOK_SECRET` |
| `POST /api/public/pagos/webhook/paypal` | PayPal | Se le pregunta a PayPal con las cabeceras `paypal-*` y `PAYPAL_WEBHOOK_ID` |

Del aviso solo se toma un identificador. El estado y el monto se consultan **en la pasarela**, y el pago se aplica por el mismo método que usa el regreso del navegador (`PagoService.aplicarPago`), así que las dos entradas comparten la verificación y la idempotencia: un pedido que ya tiene pago aprobado no se vuelve a tocar, llegue el aviso una vez o diez.

Qué responde y por qué (las pasarelas reintentan cuando reciben un 5xx):

| Respuesta | Cuándo |
|---|---|
| `200` | El aviso se recibió; también cuando no corresponde a un pedido de Vendely o el monto no cuadra (reintentarlo daría lo mismo) |
| `401` | La firma no coincide. No se toca ningún pago |
| `503` | Falta la clave del webhook: sin ella no se puede saber si el aviso es auténtico |
| `502` | No se pudo consultar la pasarela. Que lo reintente |

En el log nunca se escribe el cuerpo del aviso, solo el motivo del rechazo.

**2. La revisión periódica** (`ConciliacionPagosService`). Cada 15 minutos busca los intentos de pago que llevan más de 30 minutos pendientes y le pregunta a la pasarela cómo terminaron. Es la red de seguridad si el aviso nunca llega, y en local es el único camino: sin una dirección pública, las pasarelas no pueden avisar. Se ajusta con `PAGOS_CONCILIACION_CADA_MS` y `PAGOS_CONCILIACION_EDAD_MINUTOS`.

**Probarlo en local.** Hay que exponer el backend con `ngrok http 8080` (o similar) y registrar esa dirección en el panel de la pasarela. Sin eso, lo que sí se puede probar es la firma (un aviso con firma inválida responde 401) y la revisión periódica.

## Base de datos

La base la construye **Flyway** al arrancar, antes de que Hibernate valide las entidades. Las migraciones están en `src/main/resources/db/`:

| Migración | Qué hace |
|---|---|
| `migration/V1__esquema.sql` | El esquema: 30 tablas, restricciones y triggers |
| `migration/V2__datos_iniciales.sql` | Monedas, 248 países, divisiones e impuestos |
| `migration-local/V3__usuarios_prueba.sql` | Una cuenta por rol. **Solo en desarrollo** |
| `migration/V4__habilitar_chile.sql` | Provincias y comunas de Chile; lo habilita |
| `migration/V5__planes_plantillas.sql` | Planes, suscripciones y plantillas |
| `migration/V6__pedidos_notificaciones_envio.sql` | Seguimiento del envío, comisión y tabla `notificaciones` |

Flyway aplica cada una una sola vez y guarda su huella en `flyway_schema_history`. De ahí salen dos reglas:

1. **Una migración aplicada no se edita.** Cambiar el archivo cambia su checksum y el backend deja de arrancar (`Migration checksum mismatch`). Si hay que corregir algo, se agrega una migración nueva.
2. **Un cambio en la base = un archivo nuevo**, con el número siguiente y un nombre que diga qué hace: `V6__notificaciones.sql`, `V7__filtros_productos.sql`. Van dos guiones bajos entre la versión y la descripción.

Hibernate sigue en `ddl-auto: validate`: solo comprueba que las entidades coincidan con las tablas, no las crea ni las modifica.

`db/tests/03_pruebas.sql` (en la raíz del repositorio) tiene casos que la base debe aceptar y rechazar; no es una migración y no lo aplica Flyway.

## Errores y logs

- Todos los errores salen con el mismo formato JSON (`ApiError`).
- Los logs van a la consola y a `logs/vendely.log` (rotación a 10 MB, 7 días de historial). Los correos se enmascaran y no se registran contraseñas.

## Estado del backend

### Hecho

- Registro, login, roles y cambio de contraseña con JWT
- Tiendas: crear, listar, consultar y actualizar
- CRUD de clientes y de productos (con precio y stock)
- Ventas desde el panel: registro, consulta y eliminación con ajuste de stock
- Catálogo público y distritos
- Direcciones del cliente y pedidos desde la tienda
- Integración con Mercado Pago en modo de prueba
- Manejo de errores unificado y logs con rotación
- Colección de Postman en `postman/`

### Pendiente

- [ ] **Pago:** el último intento respondió 503 (`El pago en línea no está disponible por ahora`). Revisar `MERCADOPAGO_ACCESS_TOKEN` y probar el flujo completo, incluida la confirmación y el aviso de la pasarela con una dirección pública. De ese pago real cuelgan dos cosas que todavía no se pudieron comprobar: la `comision_plataforma` que se guarda en el pedido y los dos correos de pago aprobado.
- [ ] **Correos de verdad:** con `RESEND_API_KEY`, comprobar que llegan y que el PDF viaja como adjunto. Sin la clave quedan registrados como `SIMULADO`.
- [ ] **Pruebas automatizadas:** no hay tests Java (JUnit y MockMvc para `AuthService`, `PedidoService`, `PagoService`).
- [ ] **Rol `SUPERADMIN`:** la ruta `/api/admin/**` existe pero no tiene endpoints.
- [ ] **Categorías de productos:** la tabla `categorias` existe, pero la API aún no las expone.
- [ ] **Tablas sin API todavía:** imágenes de producto, variantes y opciones, ofertas, carritos, tarifas de envío y tipos de cambio.
- [ ] **Paginación** en los listados del panel (productos, clientes, pedidos): el catálogo público ya la tiene, el panel todavía trae la lista completa y filtra en la pantalla.
- [ ] **Documentación de la API** con Swagger/OpenAPI.
- [ ] **Despliegue:** configuración de producción (secretos, CORS, HTTPS) y revisión del Dockerfile.
