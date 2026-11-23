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
| GET | `/api/public/tiendas/{subdominio}/productos` | Catálogo |
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

- [ ] **Pago:** el último intento respondió 503 (`El pago en línea no está disponible por ahora`). Revisar `MERCADOPAGO_ACCESS_TOKEN` y probar el flujo completo, incluida la confirmación y el aviso de la pasarela con una dirección pública.
- [ ] **Pruebas automatizadas:** no hay tests Java (JUnit y MockMvc para `AuthService`, `PedidoService`, `PagoService`).
- [ ] **Rol `SUPERADMIN`:** la ruta `/api/admin/**` existe pero no tiene endpoints.
- [ ] **Categorías de productos:** la tabla `categorias` existe, pero la API aún no las expone.
- [ ] **Tablas sin API todavía:** imágenes de producto, variantes y opciones, ofertas, carritos, tarifas de envío y tipos de cambio.
- [ ] **Paginación y filtros** en los listados (productos, clientes, pedidos).
- [ ] **Documentación de la API** con Swagger/OpenAPI.
- [ ] **Despliegue:** configuración de producción (secretos, CORS, HTTPS) y revisión del Dockerfile.
