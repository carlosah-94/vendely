# Vendely: Backend (API REST)

API del proyecto Vendely (clon de Shopify). Permite que un dueño cree su tienda, administre productos, clientes y ventas, y que un comprador vea el catálogo público, haga pedidos y pague.

## Stack

- Java 21, Spring Boot 3.3.4
- Spring Security + JWT (jjwt)
- Spring Data JPA (Hibernate con `ddl-auto: validate`) y PostgreSQL 16
- Jakarta Bean Validation y Lombok
- Mercado Pago (Checkout Pro, modo de prueba)

## Cómo ejecutarlo

1. Levanta la base desde la raíz del repositorio: `docker compose up -d db`. Los scripts de `db/init/` crean las tablas y los datos de prueba.
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
| `FRONTEND_URL` | Adonde vuelve el cliente tras pagar | `http://localhost:5173` |
| `SERVER_PORT` | Puerto de la API | 8080 |
| `JPA_SHOW_SQL` | Muestra el SQL en consola (solo para depurar) | `false` |

## Arquitectura

Paquete base: `curso.fullstack.shopify`

| Capa | Qué hace |
|---|---|
| `controller` | Recibe las peticiones HTTP |
| `service` | Reglas del negocio y transacciones (`AuthService`, `ClienteService`, `ProductoService`, `PedidoService`, `PagoService`, `TiendaService`, `MercadoPagoClient`) |
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

### Cliente (`CLIENTE`)

| Método | Ruta | Descripción |
|---|---|---|
| GET, POST | `/api/cliente/direcciones` | Lista / agrega direcciones |
| GET, POST | `/api/cliente/pedidos` | Mis pedidos / crea un pedido |
| GET | `/api/cliente/pedidos/{idPedido}` | Detalle de un pedido |
| POST | `/api/cliente/pedidos/{idPedido}/pago` | Inicia el pago en Mercado Pago |
| POST | `/api/cliente/pedidos/{idPedido}/pago/confirmar` | Confirma el resultado del pago |

## Base de datos

El esquema (30 tablas, restricciones y triggers) está en `db/init/01_esquema.sql`. Hibernate solo valida que las entidades coincidan; no crea ni modifica tablas.

`db/tests/03_pruebas.sql` tiene casos que la base debe aceptar y rechazar.

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

- [ ] **Pago:** el último intento respondió 503 (`El pago en línea no está disponible por ahora`). Revisar `MERCADOPAGO_ACCESS_TOKEN` y probar el flujo completo, incluida la confirmación.
- [ ] **Pruebas automatizadas:** no hay tests Java (JUnit y MockMvc para `AuthService`, `PedidoService`, `PagoService`).
- [ ] **Limpieza:** `CategoryController`, `CategoryService`, `UserController` y `UserService` son clases vacías de plantilla; implementarlas o borrarlas (y revisar si `UserRepository`/`UserRequest`/`UserResponse` siguen en uso).
- [ ] **Rol `SUPERADMIN`:** la ruta `/api/admin/**` existe pero no tiene endpoints.
- [ ] **Categorías de productos:** la tabla `categorias` existe, pero la API aún no las expone.
- [ ] **Tablas sin API todavía:** imágenes de producto, variantes y opciones, ofertas, carritos, tarifas de envío y tipos de cambio.
- [ ] **Paginación y filtros** en los listados (productos, clientes, pedidos).
- [ ] **Documentación de la API** con Swagger/OpenAPI.
- [ ] **Despliegue:** configuración de producción (secretos, CORS, HTTPS) y revisión del Dockerfile.
