# Vendely: clon de Shopify (Proyecto Full Stack, UTP 2026)

Vendely es una plataforma donde cualquier persona crea su tienda online, registra sus productos y clientes, y gestiona sus ventas. Está inspirada en Shopify y es el proyecto del curso Desarrollo Full Stack.

## Stack

- **Backend:** Java 21, Spring Boot 3.3.4, Spring Security, JWT (jjwt), Spring Data JPA, Jakarta Bean Validation, Lombok
- **Base de datos:** PostgreSQL 16
- **Frontend:** React 19 con Vite, Tailwind CSS v4, React Router y Axios
- **Infraestructura:** Docker y Docker Compose
- **Pruebas de la API:** Postman

## Estructura

```
shopity/
├── backend/shopify/   # API REST en Spring Boot (proyecto Maven, se abre en NetBeans)
│   └── src/main/java/curso/fullstack/shopify/
│       ├── controller/   # reciben las peticiones HTTP
│       ├── service/      # reglas del negocio y transacciones
│       ├── repository/   # acceso a la base (Spring Data JPA)
│       ├── entity/       # clases que representan las tablas
│       ├── dto/          # datos que entran y salen de la API, con sus validaciones
│       ├── security/     # JWT, roles y verificación de la tienda
│       └── exception/    # formato único de errores
├── frontend/          # aplicación React (panel del dueño)
├── db/init/           # 01_esquema.sql, 02_datos_iniciales.sql y 03_usuarios_prueba.sql
├── db/tests/          # 03_pruebas.sql: casos que la base debe aceptar y rechazar
├── postman/           # colección Vendely_API para probar la API
└── docker-compose.yml
```

## Módulos implementados

| Módulo | Backend | Pantalla en el panel |
|---|---|---|
| Registro, inicio de sesión y roles | Sí | Sí (login, menú por rol, acceso denegado) |
| Tiendas | Sí | Pendiente (la sección "Mis tiendas" está en construcción) |
| Clientes (CRUD) | Sí | Sí (`/panel/clientes`) |
| Productos (CRUD con precio y stock) | Sí | Sí (`/panel/productos`) |
| Ventas (registro, consulta y eliminación con stock) | Sí | Sí (`/panel/ventas`) |
| Manejo de errores y logs | Sí | Mensajes de error en cada pantalla |

Pendiente para el siguiente avance: la tienda pública que ve el comprador (vitrina, carrito y pago) y la pantalla "Mis tiendas".

## Cómo ejecutar el proyecto

### Requisitos

- Docker Desktop
- JDK 21 o superior y NetBeans (o Maven)
- Node.js 22 o superior

### 1. Clonar y configurar

```bash
git clone https://github.com/bryancacsire/shopity.git
cd shopity
cp .env.example .env
```

Abre `.env` y cambia al menos `POSTGRES_PASSWORD`, `SPRING_DATASOURCE_PASSWORD` y `JWT_SECRET` (mínimo 32 caracteres). El archivo `.env` tiene secretos y nunca se sube al repositorio.

### 2. Base de datos

```bash
docker compose up -d db
```

La primera vez, Docker crea la base y ejecuta en orden los scripts de `db/init/`: el esquema (tablas, restricciones y triggers), los datos iniciales (países, divisiones e impuestos) y los usuarios de prueba. La base queda disponible en el puerto `POSTGRES_PORT` de tu computadora (5433 por defecto).

Para borrar la base y crearla de nuevo (por ejemplo, después de un cambio en `db/init/`):

```bash
docker compose down -v
docker compose up -d db
```

Para cargar los scripts a mano en una base ya creada:

```bash
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < db/init/03_usuarios_prueba.sql
```

### 3. Backend

Abre `backend/shopify` en NetBeans (proyecto Maven), haz **Clean and Build** y luego **Run**. La API queda en `http://localhost:8080/api`.

Sin NetBeans: `cd backend/shopify` y `mvn spring-boot:run`.

### 4. Frontend

```bash
cd frontend
npm install
npm run dev
```

La aplicación queda en `http://localhost:5173`.

### Todo con Docker (opcional)

```bash
docker compose up --build
```

Levanta la base, el backend (`http://localhost:8080`) y el frontend (`http://localhost:5174`, según `FRONTEND_PORT`).

## Seguridad

La API usa **JWT** (JSON Web Token) sin sesiones: al iniciar sesión el backend entrega un token firmado y el frontend lo envía en cada petición con la cabecera `Authorization: Bearer <token>`.

| Endpoint | Acceso | Qué hace |
|---|---|---|
| `POST /api/auth/registro/dueno` | Abierto | Crea la cuenta del panel (ADMIN_TIENDA) y sus datos de propietario |
| `POST /api/auth/registro/cliente` | Abierto | Crea la cuenta de un cliente en una tienda (por `subdominio`) |
| `POST /api/auth/login` | Abierto | Sin `subdominio`: cuenta del panel. Con `subdominio`: cliente de esa tienda |
| `GET /api/auth/yo` | Con token | Datos de quien inició sesión |
| `POST /api/auth/cambiar-contrasena` | Con token | Cambia la contraseña y entrega un token nuevo |

**Reglas de acceso por ruta** (`SecurityConfig`):

| Ruta | Quién entra |
|---|---|
| `/api/auth/**` (registro y login) | Todos |
| `GET /api/public/**` | Todos (catálogo de las tiendas) |
| `/api/panel/**` | `ADMIN_TIENDA` |
| `/api/cliente/**` | `CLIENTE` |
| `/api/admin/**` | `SUPERADMIN` |
| Cualquier otra | Usuarios con token |

**Medidas incluidas:**

- Contraseñas guardadas con **BCrypt** (columna `contrasena_hash`); la API nunca devuelve el hash.
- El token lleva el id de la cuenta, su rol y su tienda, firmado con `JWT_SECRET` (mínimo 32 caracteres) y vence según `JWT_EXPIRATION`.
- En cada petición se verifica que la cuenta siga activa y que el token sea posterior al último cambio de contraseña (`contrasena_cambiada_en`): cambiar la contraseña invalida los tokens anteriores.
- Mismo mensaje para correo o contraseña incorrectos, para no revelar qué correos existen.
- Validación de datos con `@Valid` y errores en un formato JSON único (`GlobalExceptionHandler`).
- **CORS** limitado a los orígenes de `CORS_ORIGENES`.
- **Multi-tienda:** en los endpoints de clientes, la tienda se toma del token (`UsuarioAutenticado.idTienda()`), nunca del cuerpo de la petición.
- **CSRF:** no se usa token CSRF porque la API no usa cookies. El ataque CSRF aprovecha que el navegador envía las cookies solo; el token JWT va en la cabecera `Authorization`, que el navegador nunca envía por su cuenta.

Para usar el usuario en un controller:

```java
@GetMapping("/api/cliente/carrito")
public CarritoResponse miCarrito(@AuthenticationPrincipal UsuarioAutenticado yo) {
    return carritoService.delCliente(yo.idLogin(), yo.idTienda());
}
```

### Seguridad en el frontend

El frontend guarda y usa el token así:

1. **Inicio de sesión** (`pages/Login.jsx`): envía correo y contraseña a `POST /api/auth/login`. No se elige rol: el backend responde con el rol de la cuenta.
2. **Un solo lugar para el token** (`services/api.js`): se guarda en `localStorage` con la clave `vendely_token`, a través de `tokenStorage`.
3. **Envío automático**: un interceptor de axios agrega `Authorization: Bearer <token>` a cada petición; ninguna pantalla lo hace a mano.
4. **Sesión vencida**: si el backend responde 401 (token vencido, alterado o de una contraseña ya cambiada), otro interceptor borra el token y la app vuelve al login.
5. **Sin permiso**: si el backend responde 403 (por ejemplo, la tienda de otro dueño), la app muestra la página "Acceso denegado", sin cerrar la sesión. Desde ahí se puede volver al inicio o cerrar sesión.
6. **Al recargar la página** (`context/AuthContext.jsx`): si hay un token guardado, se valida con `GET /api/auth/yo` antes de mostrar el panel.
7. **Rutas protegidas** (`components/RutaProtegida.jsx`): sin sesión se va al login; con un rol que no corresponde, a "Acceso denegado". Es solo una ayuda visual: la protección real está en el backend (`SecurityConfig`), que rechaza con 401 o 403 aunque alguien salte la pantalla.
8. **Contenido seguro**: React muestra los textos escapados (un `<script>` escrito en un nombre de producto se ve como texto y no se ejecuta). Por eso no se usa `dangerouslySetInnerHTML`.

### Usuarios de prueba

Al crear la base, `db/init/03_usuarios_prueba.sql` deja una cuenta lista por cada rol:

| Rol | Correo | Contraseña | Cómo iniciar sesión |
|---|---|---|---|
| SUPERADMIN | `admin@vendely.pe` | `Admin1234` | Sin `subdominio` |
| ADMIN_TIENDA | `dueno@vendely.pe` | `Dueno1234` | Sin `subdominio` (dueño de la tienda `moda-lima`) |
| CLIENTE | `cliente@vendely.pe` | `Cliente1234` | Con `"subdominio": "moda-lima"` |

Ejemplo (`POST /api/auth/login`):

```json
{ "correo": "cliente@vendely.pe", "contrasena": "Cliente1234", "subdominio": "moda-lima" }
```

Si la base ya existía antes de agregar este archivo, hay que recrearla para que se ejecute: `docker compose down -v` y `docker compose up -d db`.

### Probar con Postman

1. Importa `postman/Vendely_API.postman_collection.json`.
2. Con la base recién creada, ejecuta las carpetas **en orden** (clic derecho en la colección → *Run collection*, o carpeta por carpeta):
   - **1. Registro e inicio de sesión** y **2. Casos que deben fallar**: seguridad.
   - **3. Tiendas** y **4. Tiendas: casos que deben fallar**: módulo de tiendas.
3. Los tokens y los ids se guardan solos en las variables de la colección.

La colección cubre la seguridad y las tiendas. Los módulos de clientes, productos y ventas se prueban desde sus pantallas del panel.

> La carpeta 1 crea sola la tienda `zapatillas-carlos` (petición **Crear tienda Zapatillas Carlos**), que usa el registro de clientes. Si ya existía, responde 409 y la prueba igual pasa.
>
> Desde la terminal, sin instalar Postman (con el backend corriendo):
>
> ```bash
> npx newman run postman/Vendely_API.postman_collection.json
> ```
>
> La colección se puede ejecutar varias veces seguidas: los registros aceptan 409 si la cuenta ya existe y **Restaurar contraseña** devuelve la contraseña a `Clave1234` al final de la carpeta 1.

## Tiendas

Un dueño (rol `ADMIN_TIENDA`) puede tener varias tiendas. Cada tienda se identifica en internet por su **subdominio** (`zapatillas-carlos` → `zapatillas-carlos.vendely.pe`).

| Endpoint | Acceso | Qué hace |
|---|---|---|
| `POST /api/panel/tiendas` | ADMIN_TIENDA | Crea una tienda del dueño |
| `GET /api/panel/tiendas` | ADMIN_TIENDA | Lista **sus** tiendas |
| `GET /api/panel/tiendas/{idTienda}` | ADMIN_TIENDA (solo si es suya) | Ver una tienda |
| `PUT /api/panel/tiendas/{idTienda}` | ADMIN_TIENDA (solo si es suya) | Editar nombre, descripción, contacto, logo, color e invitados |
| `GET /api/public/tiendas/{subdominio}` | Abierto | Datos públicos de una tienda activa (vitrina) |

**Reglas:**

- El subdominio solo acepta letras, números y guiones (se guarda en minúsculas), es único y **no se cambia** después de crear la tienda, porque es la dirección que usan los clientes.
- Algunos subdominios están reservados por Vendely (`www`, `api`, `admin`, `panel`, `login`…).
- Una tienda suspendida no aparece en la vitrina pública.

### Regla para los demás módulos del panel: `VerificadorTienda`

El token del dueño no dice qué tienda está administrando, porque puede tener varias. Por eso **toda ruta del panel que reciba un `idTienda` debe comprobar que esa tienda sea del dueño**, usando la misma pieza:

```java
@GetMapping("/api/panel/tiendas/{idTienda}/productos")
public List<ProductoResponse> listar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                     @PathVariable Long idTienda) {
    Tienda tienda = verificadorTienda.delDueno(yo, idTienda);   // 404 si no existe, 403 si es de otro
    return productoService.deTienda(tienda.getIdTienda());
}
```

Así, si un dueño cambia el número de la URL para entrar a la tienda de otro, la API responde **403 "Esta tienda no es tuya"**.

## Clientes

El dueño gestiona los clientes de cada una de sus tiendas. Un cliente pertenece a una sola tienda: el mismo correo puede ser cliente de dos tiendas distintas.

| Endpoint | Qué hace |
|---|---|
| `GET /api/panel/tiendas/{idTienda}/clientes` | Lista los clientes de la tienda |
| `GET /api/panel/tiendas/{idTienda}/clientes/{idCliente}` | Ver un cliente con su dirección |
| `POST /api/panel/tiendas/{idTienda}/clientes` | Crear un cliente con su dirección de envío |
| `PUT /api/panel/tiendas/{idTienda}/clientes/{idCliente}` | Editar (la contraseña solo cambia si se envía) |
| `DELETE /api/panel/tiendas/{idTienda}/clientes/{idCliente}` | Eliminar |
| `GET /api/public/paises/{idPais}/distritos?buscar=texto` | Buscador de distritos para la dirección |

**Reglas:**

- El correo es único dentro de la tienda (409 si se repite).
- La contraseña se guarda con BCrypt.
- El distrito debe ser del país de la tienda.
- Un cliente con ventas no se puede eliminar: responde 409 "El cliente tiene ventas asociadas".

## Productos

Cada producto se guarda con su variante, su precio vigente y su stock (tablas `productos`, `variantes`, `precios` e `inventario`).

| Endpoint | Qué hace |
|---|---|
| `GET /api/panel/tiendas/{idTienda}/productos` | Lista los productos con precio y stock |
| `GET /api/panel/tiendas/{idTienda}/productos/{idProducto}` | Ver un producto |
| `POST /api/panel/tiendas/{idTienda}/productos` | Crear |
| `PUT /api/panel/tiendas/{idTienda}/productos/{idProducto}` | Editar |
| `DELETE /api/panel/tiendas/{idTienda}/productos/{idProducto}` | Eliminar |

**Reglas:**

- El nombre es único dentro de la tienda.
- El precio debe ser mayor que 0 y el stock, mayor o igual a 0.
- Al cambiar el precio no se edita el anterior: se cierra y se crea uno nuevo, así las ventas pasadas conservan el precio con el que se hicieron.
- Un producto con ventas no se puede eliminar (409); se puede desactivar.

## Ventas

| Endpoint | Qué hace |
|---|---|
| `GET /api/panel/tiendas/{idTienda}/pedidos` | Lista las ventas: número, fecha, cliente y total |
| `GET /api/panel/tiendas/{idTienda}/pedidos/{idPedido}` | Detalle con cada producto, cantidad, precio unitario e importe |
| `POST /api/panel/tiendas/{idTienda}/pedidos` | Registrar una venta |
| `DELETE /api/panel/tiendas/{idTienda}/pedidos/{idPedido}` | Eliminar una venta y devolver su stock |

Ejemplo de venta (`POST`):

```json
{ "idLogin": 9003, "idDireccionEnvio": 9001, "lineas": [ { "idVariante": 1, "cantidad": 2 } ] }
```

**Reglas:**

- **Transaccional:** si un producto no tiene stock suficiente, la venta completa se deshace y responde 409 "Stock insuficiente de ...". Nunca queda una venta a medias.
- Guarda el precio unitario vigente de cada producto en el detalle.
- El total = subtotal + costo de envío. El envío depende del departamento de la dirección; al crear una tienda se crean tarifas por defecto.
- La numeración es por tienda (#1001, #1002...).
- Al eliminar una venta, el stock vuelve al inventario en la misma transacción. Una venta con pago aprobado no se puede eliminar.

En el panel, el formulario de venta permite agregar varios productos con los botones **Añadir producto** y **Eliminar**, y muestra el total antes de guardar.

## Manejo de errores y logs

- **Formato único:** todos los errores de la API llegan con el mismo formato JSON (`GlobalExceptionHandler`), con el código, el mensaje y, si son errores de validación, el campo que falló:

  ```json
  { "estado": 400, "mensaje": "Hay datos inválidos", "campos": { "correo": "Correo inválido" } }
  ```

- **Archivo de log:** el backend escribe en `backend/shopify/logs/vendely.log`, rotando el archivo cada 10 MB y guardando 7 días. Registra:
  - inicios de sesión (incluidos los fallidos);
  - tiendas creadas;
  - ventas registradas, rechazadas por falta de stock y eliminadas;
  - tokens rechazados.
- **Datos que nunca se registran:** contraseñas, hashes ni tokens. Los correos se enmascaran (`c***@vendely.pe`).
