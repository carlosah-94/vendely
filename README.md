# Vendely: clon de Shopify (Proyecto Full Stack, UTP 2026)

Vendely es una plataforma donde cualquier persona crea su tienda online, registra sus productos y clientes, y gestiona sus ventas. Está inspirada en Shopify y es el proyecto del curso Desarrollo Full Stack.

## Stack

- **Backend:** Java 21, Spring Boot 3.3.4, Spring Security, JWT (jjwt), Spring Data JPA, Jakarta Bean Validation, Lombok
- **Base de datos:** PostgreSQL 16
- **Frontend:** React 19 con Vite, Tailwind CSS v4, React Router y Axios
- **Infraestructura:** Docker y Docker Compose
- **Despliegue:** Neon (base de datos), Render (backend) y Vercel (frontend), en sus planes gratuitos
- **Pruebas de la API:** Postman

## Estructura

```
shopity/
├── backend/shopify/   # API REST en Spring Boot (proyecto Maven, se abre en NetBeans); tiene su propio README
│   └── src/main/java/curso/fullstack/shopify/
│       ├── controller/   # reciben las peticiones HTTP
│       ├── service/      # reglas del negocio y transacciones
│       ├── repository/   # acceso a la base (Spring Data JPA)
│       ├── entity/       # clases que representan las tablas
│       ├── dto/          # datos que entran y salen de la API, con sus validaciones
│       ├── security/     # JWT, roles y verificación de la tienda
│       └── exception/    # formato único de errores
├── frontend/          # aplicación React: panel del dueño y tienda pública del comprador
├── db/init/           # 01_esquema.sql, 02_datos_iniciales.sql y 03_usuarios_prueba.sql
├── db/tests/          # 03_pruebas.sql: casos que la base debe aceptar y rechazar
├── postman/           # colección Vendely_API para probar la API
└── docker-compose.yml
```

## Módulos implementados

| Módulo | Backend | Pantalla en el panel |
|---|---|---|
| Registro, inicio de sesión y roles | Sí | Sí (login, menú por rol, acceso denegado) |
| Tiendas | Sí | Sí (`/panel/tiendas`, "Mis tiendas") |
| Clientes (CRUD) | Sí | Sí (`/panel/clientes`) |
| Productos (CRUD con precio y stock) | Sí | Sí (`/panel/productos`) |
| Ventas (registro, consulta y eliminación con stock) | Sí | Sí (`/panel/ventas`) |
| Tienda pública: vitrina, detalle de producto y carrito | Sí | Sí (`/tienda/:subdominio`) |
| Cliente: ingreso, direcciones, checkout y "Mis pedidos" | Sí | Sí (`/tienda/:subdominio/checkout`, `/mis-pedidos`) |
| Pago con Mercado Pago (modo de prueba) | Sí | Sí (`/tienda/:subdominio/pago/resultado`) |
| Manejo de errores y logs | Sí | Mensajes de error en cada pantalla |

### Pendiente

- Probar de punta a punta el pago con Mercado Pago (necesita `MERCADOPAGO_ACCESS_TOKEN` de prueba; sin él la API responde 503).
- Pruebas automatizadas del backend (JUnit y MockMvc).
- Panel de `SUPERADMIN` (las pantallas `/admin/tiendas` y `/admin/cuentas` están en construcción y la API no tiene endpoints en `/api/admin/**`).
- Categorías, imágenes, variantes, ofertas y tarifas de envío editables: las tablas existen, pero aún no tienen API ni pantalla.
- Paginación y filtros en los listados y documentación Swagger/OpenAPI.

El detalle del backend (endpoints, variables y estado) está en [`backend/shopify/README.md`](backend/shopify/README.md).

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

Abre `.env` y cambia al menos `POSTGRES_PASSWORD`, `SPRING_DATASOURCE_PASSWORD` y `JWT_SECRET` (mínimo 32 caracteres). Para probar el pago, pon también tu Access Token de **prueba** de Mercado Pago en `MERCADOPAGO_ACCESS_TOKEN` (empieza con `TEST-`). El archivo `.env` tiene secretos y nunca se sube al repositorio.

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

## Despliegue en producción

La aplicación está publicada en tres servicios gratuitos:

| Parte | Servicio | Dirección |
|---|---|---|
| Frontend | Vercel | https://shopity-7zya.vercel.app |
| Backend (API) | Render | https://vendely-api.onrender.com/api |
| Base de datos | Neon (PostgreSQL) | proyecto `vendely`, región AWS US East (N. Virginia) |

```
Navegador ──► Vercel (React) ──► Render (Spring Boot) ──► Neon (PostgreSQL)
```

**Por qué estas regiones:** el backend hace varias consultas por cada petición, así que la base y el backend están en la misma zona (Render en Virginia y Neon en N. Virginia). Así cada consulta tarda alrededor de 1 ms en lugar de decenas. Vercel sirve el frontend desde su red mundial.

Render y Vercel se conectan al fork [`AndreeQuispe12/shopity`](https://github.com/AndreeQuispe12/shopity) de este repositorio. Ver [Publicar una versión nueva](#publicar-una-versión-nueva).

### 1. Base de datos en Neon

1. En [neon.tech](https://neon.tech) se creó el proyecto `vendely` (Postgres 18, región AWS US East (N. Virginia)). Neon soporta la extensión `btree_gist` que necesita el esquema.
2. Los scripts se cargan una sola vez, en orden, con la conexión **directa** (sin `-pooler`). Se puede usar el `psql` del contenedor local, que ya tiene los scripts en `/docker-entrypoint-initdb.d/`:

   ```powershell
   $env:NEON = "postgresql://USUARIO:CONTRASEÑA@HOST-SIN-POOLER/neondb?sslmode=require"
   docker compose exec db psql $env:NEON -v ON_ERROR_STOP=1 -f /docker-entrypoint-initdb.d/01_esquema.sql
   docker compose exec db psql $env:NEON -v ON_ERROR_STOP=1 -f /docker-entrypoint-initdb.d/02_datos_iniciales.sql
   docker compose exec db psql $env:NEON -v ON_ERROR_STOP=1 -f /docker-entrypoint-initdb.d/03_usuarios_prueba.sql
   ```

3. Para comprobarla, `db/tests/03_pruebas.sql` se puede correr contra Neon: termina en `ROLLBACK` y no deja datos.

   ```powershell
   docker compose cp db/tests/03_pruebas.sql db:/tmp/03_pruebas.sql
   docker compose exec db psql $env:NEON -q -f /tmp/03_pruebas.sql
   ```

   Se copia el archivo al contenedor en lugar de pasarlo con `Get-Content |`, porque PowerShell 5.1 daña las tildes al enviar texto por una tubería.

La cadena de conexión está en la consola de Neon: **Connect**, con **Connection pooling** desactivado. Usa la directa porque Spring Boot ya tiene su propio pool de conexiones (Hikari). Nunca se sube al repositorio.

### 2. Backend en Render

Servicio **Web Service** en [render.com](https://render.com):

| Campo | Valor |
|---|---|
| Name | `vendely-api` |
| Language | Docker (usa `backend/shopify/Dockerfile`) |
| Branch | `main` |
| Region | Virginia (US East) |
| Root Directory | `backend/shopify` |
| Instance Type | Free |

Variables de entorno (las lee `application.yml`):

| Variable | Valor |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://HOST-DE-NEON-SIN-POOLER/neondb?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | usuario de Neon (`neondb_owner`) |
| `SPRING_DATASOURCE_PASSWORD` | contraseña de Neon |
| `JWT_SECRET` | clave larga y aleatoria, distinta de la local |
| `JWT_EXPIRATION` | `86400000` |
| `PORT` | `8080` (Render envía el tráfico a ese puerto) |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75` (aprovecha los 512 MB del plan gratuito) |
| `CORS_ORIGENES` | `https://shopity-7zya.vercel.app` |
| `FRONTEND_URL` | `https://shopity-7zya.vercel.app` (adonde vuelve el comprador después de pagar) |
| `MERCADOPAGO_ACCESS_TOKEN`, `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` | opcionales, credenciales **de prueba** |

`CORS_ORIGENES` se compara letra por letra: va sin `/` al final y, si hay varias direcciones, separadas solo por comas, sin espacios. Si no coincide, el navegador bloquea las peticiones con un error de CORS.

Para comprobar que el backend responde: https://vendely-api.onrender.com/api/public/paises/PE/distritos?buscar=mira

### 3. Frontend en Vercel

Proyecto en [vercel.com](https://vercel.com) importado desde GitHub:

| Campo | Valor |
|---|---|
| Root Directory | `frontend` |
| Framework Preset | Vite |
| Build Command / Output | `npm run build` / `dist` |
| Variable de entorno | `VITE_API_BASE_URL` = `https://vendely-api.onrender.com/api` |

`VITE_API_BASE_URL` se graba en el frontend al construirlo: si cambia, hay que volver a desplegar.

> **Pendiente:** agregar `frontend/vercel.json` con una regla para que todas las rutas sirvan `index.html`. Sin ella, recargar una página como `/panel/clientes` en Vercel da 404, porque esas rutas las maneja React Router en el navegador.
>
> ```json
> { "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }] }
> ```

### Publicar una versión nueva

1. Se hace merge del PR a `main` en este repositorio.
2. En el fork, **Sync fork → Update branch**.
3. **Render** despliega solo el backend (5 a 10 minutos, por la compilación con Maven).
4. **Vercel** despliega solo el frontend, con una condición: en el plan gratuito, Vercel **bloquea** (estado "Blocked") el despliegue si el último commit es de alguien que no es el dueño del proyecto en Vercel. Para publicar en ese caso hay dos opciones:
   - que el último commit de `main` sea del dueño de Vercel (por ejemplo, que él haga el merge del PR);
   - o publicar desde la computadora del dueño con el CLI, que sube los archivos sin revisar el autor:

     ```bash
     git pull
     npx vercel deploy --prod
     ```

     El CLI crea `.vercel/` y `.env.local` en la carpeta: no se suben al repositorio.

### Límites de los planes gratuitos

- **Render** apaga el backend tras unos 15 minutos sin visitas; la primera petición después tarda de 30 a 60 segundos en despertarlo. Antes de una demostración, conviene abrirlo un minuto antes.
- **Neon** suspende la base tras unos minutos sin uso y la despierta en alrededor de un segundo con la siguiente conexión.
- **Vercel Hobby** solo admite un miembro por proyecto (de ahí el bloqueo de despliegues descrito arriba).

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
   - **5. Clientes, productos y ventas** y **6. Casos que deben fallar**: panel del dueño (CRUD, venta con descuento y devolución de stock, reglas de 409 y permisos entre dueños).
   - **7. Tienda pública y compra del cliente** y **8. Casos que deben fallar**: catálogo, registro del comprador, direcciones, pedido, y permisos por rol y entre clientes.
3. Los tokens y los ids se guardan solos en las variables de la colección.

Las carpetas 5 a 8 usan la tienda que crea la carpeta 3, así que se ejecutan después de ella. La petición **Iniciar pago** acepta 200 (con `MERCADOPAGO_ACCESS_TOKEN`) o 503 (sin él); el pago completo en Mercado Pago se prueba desde la tienda en el navegador.

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

## Tienda pública y pedidos del cliente

Cada tienda tiene su vitrina en `/tienda/{subdominio}` (por ejemplo `/tienda/moda-lima`). El comprador ve el catálogo y arma su carrito sin iniciar sesión. Para comprar, ingresa o se registra como cliente de esa tienda.

| Endpoint | Acceso | Qué hace |
|---|---|---|
| `GET /api/public/tiendas/{subdominio}/productos` | Abierto | Catálogo de la tienda |
| `GET /api/public/tiendas/{subdominio}/productos/{idProducto}` | Abierto | Detalle de un producto |
| `GET /api/cliente/direcciones` | CLIENTE | Mis direcciones |
| `POST /api/cliente/direcciones` | CLIENTE | Agregar una dirección |
| `GET /api/cliente/pedidos` | CLIENTE | Mis pedidos |
| `GET /api/cliente/pedidos/{idPedido}` | CLIENTE | Detalle de un pedido |
| `POST /api/cliente/pedidos` | CLIENTE | Crear un pedido desde el carrito |
| `POST /api/cliente/pedidos/{idPedido}/pago` | CLIENTE | Iniciar el pago en Mercado Pago |
| `POST /api/cliente/pedidos/{idPedido}/pago/confirmar` | CLIENTE | Confirmar el resultado del pago |

**Flujo de compra:** vitrina → carrito → ingresar → checkout (dirección de envío) → pedido → pago en Mercado Pago → vuelve a `/tienda/{subdominio}/pago/resultado` (usa `FRONTEND_URL`) → "Mis pedidos".

- La tienda del cliente sale de su token, no de la petición: un cliente no puede ver ni pagar pedidos de otra tienda.
- El pago usa Mercado Pago Checkout Pro en **modo de prueba**. Sin `MERCADOPAGO_ACCESS_TOKEN`, iniciar el pago responde 503 "El pago en línea no está disponible por ahora".

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
