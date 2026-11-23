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
│   ├── src/main/java/curso/fullstack/shopify/
│   │   ├── controller/   # reciben las peticiones HTTP
│   │   ├── service/      # reglas del negocio y transacciones
│   │   ├── repository/   # acceso a la base (Spring Data JPA)
│   │   ├── entity/       # clases que representan las tablas
│   │   ├── dto/          # datos que entran y salen de la API, con sus validaciones
│   │   ├── security/     # JWT, roles y verificación de la tienda
│   │   └── exception/    # formato único de errores
│   └── src/main/resources/db/
│       ├── migration/        # migraciones de Flyway: V1 esquema, V2 datos iniciales, V4 Chile, V5 planes
│       └── migration-local/  # V3 usuarios de prueba (no se aplica en el servidor)
├── frontend/          # aplicación React: panel del dueño y tienda pública del comprador
├── db/tests/          # 03_pruebas.sql: casos que la base debe aceptar y rechazar
├── postman/           # colección Vendely_API para probar la API
└── docker-compose.yml
```

## Módulos implementados

| Módulo | Backend | Pantalla en el panel |
|---|---|---|
| Registro, inicio de sesión y roles | Sí | Sí (login, registro de dueño con su primera tienda, menú por rol, acceso denegado) |
| Tiendas (en Perú o Chile) | Sí | Sí (`/panel/tiendas`, "Mis tiendas") |
| Personalizar la tienda (logo, color, descripción) | Sí | Sí (`/panel/personalizar`) |
| Clientes (CRUD) | Sí | Sí (`/panel/clientes`) |
| Productos: precio, stock, imágenes, precio de comparación, SKU | Sí | Sí (`/panel/productos`) |
| Variantes de producto (talla, color…) con precio, stock y SKU propios | Sí | Sí (dentro del formulario de producto) |
| Categorías de productos | Sí | Sí (`/panel/productos/categorias`) |
| Inventario: reposición, stock mínimo y aviso de stock bajo | Sí | Sí (`/panel/productos/inventario`) |
| Envíos nacionales (por región) e internacionales (por país) | Sí | Sí (`/panel/envios`) |
| Ventas (registro, consulta y eliminación con stock) | Sí | Sí (`/panel/ventas`) |
| Tienda pública: vitrina con filtros por categoría, detalle con variantes y carrito | Sí | Sí (`/tienda/:subdominio`) |
| Catálogo con filtros (texto, categoría, precio, disponibilidad), orden y paginación | Sí | Sí (barra de filtros en la tienda; los filtros quedan en la URL) |
| Cliente: ingreso, direcciones (también en otros países), costo de envío, checkout y "Mis pedidos" | Sí | Sí (`/tienda/:subdominio/checkout`, `/mis-pedidos`) |
| Pago en modo de prueba: PayPal Sandbox o Mercado Pago | Sí | Sí (`/tienda/:subdominio/pago/resultado`) |
| Seguimiento del envío: enviado (con empresa y número), entregado y cancelado | Sí | Sí (en el detalle de la venta y en "Mis pedidos") |
| Correos de confirmación, de envío y de stock bajo, con su registro | Sí | Sí (los correos del pedido, en el detalle de la venta) |
| Comprobante de compra en PDF | Sí | Sí (se descarga desde el panel y desde "Mis pedidos") |
| Panel del superadmin: resumen, tiendas y cuentas (suspender y reactivar) | Sí | Sí (`/admin/tiendas`, `/admin/cuentas`) |
| Manejo de errores y logs | Sí | Mensajes de error en cada pantalla |
| Despliegue (Neon, Render y Vercel) | Sí | https://shopity-7zya.vercel.app |

### Pendiente

- Probar de punta a punta el pago en producción (necesita las credenciales de prueba de PayPal o Mercado Pago en Render; sin ellas la API responde 503). Con ese pago real quedan por comprobar las dos cosas que cuelgan de él: la comisión que se guarda en el pedido y los correos de confirmación.
- Enviar correos de verdad: sin `RESEND_API_KEY` se registran como "simulado" y no salen.
- Pruebas automatizadas del backend (JUnit y MockMvc).
- Ofertas (tablas `ofertas` y `oferta_productos`): existen en la base, pero aún no tienen API ni pantalla.
- Habilitar más países (México, Colombia, Argentina y Ecuador ya tienen su primer nivel territorial; falta cargar su lista completa, como `04_habilitar_chile.sql`).
- Paginación en los listados y documentación Swagger/OpenAPI.

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

Docker crea la base **vacía**, en el puerto `POSTGRES_PORT` de tu computadora (5433 por defecto). Las tablas y los datos los pone Flyway cuando arranca el backend (paso 3): aplica en orden las migraciones de `backend/shopify/src/main/resources/db/migration/` —el esquema (tablas, restricciones y triggers), los datos iniciales (países, divisiones e impuestos), la habilitación de Chile y los planes— y, en desarrollo, los usuarios de prueba de `db/migration-local/`. Ya no hay que ejecutar ningún `.sql` a mano.

Flyway anota en la tabla `flyway_schema_history` las migraciones que aplicó en esa base. Por eso, cuando un compañero agrega una migración, basta con hacer `git pull` y arrancar el backend: se aplica solo lo que falta y los datos de prueba no se pierden. Para ver el estado de una base:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

Para empezar de cero (borra todos los datos de la base):

```bash
docker compose down -v
docker compose up -d db
```

> **Si tu base ya existía antes de Flyway** no hay que hacer nada especial: Flyway la marca en la versión 5 (los cinco scripts que antes se corrían a mano) y de ahí en adelante aplica solo las migraciones nuevas. Eso supone que tiene los cinco aplicados; si nunca corriste `04_habilitar_chile.sql` o `05_planes_plantillas.sql`, lo más simple es `docker compose down -v` y dejar que Flyway la construya entera.

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
2. El esquema y los datos los aplica **Flyway** cuando arranca el backend en Render: no hay que correr ningún script a mano, ni ahora ni cuando se agregue una migración nueva. Dos cosas que hay que respetar en Render:

   - `SPRING_DATASOURCE_URL` con la conexión **directa** de Neon (sin `-pooler`): Flyway toma un bloqueo en la base mientras migra y conviene que sea sobre una conexión estable.
   - `FLYWAY_LOCATIONS=classpath:db/migration`, para que en el servidor no se creen los usuarios de prueba (tienen contraseñas conocidas y son solo para desarrollo).

   La base de Neon se creó antes de Flyway, con los scripts 01 al 05 cargados a mano, así que Flyway la marca en la versión 5 y aplica solo lo que venga después. Antes del primer despliegue conviene confirmar que los cinco estén aplicados:

   ```sql
   SELECT count(*) FROM planes;                           -- 3; si la tabla no existe, falta el script 05
   SELECT count(*) FROM divisiones WHERE id_pais = 'CL';  -- 418; si sale 19, falta el script 04
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
| `MERCADOPAGO_WEBHOOK_SECRET`, `PAYPAL_WEBHOOK_ID` | opcionales, para recibir los avisos de pago |
| `RESEND_API_KEY` | opcional; sin ella los correos se registran como "simulado" y no se envían |
| `RESEND_REMITENTE` | opcional, `Vendely <onboarding@resend.dev>` por defecto |
| `FLYWAY_LOCATIONS` | `classpath:db/migration` (sin los usuarios de prueba) |

`CORS_ORIGENES` se compara letra por letra: va sin `/` al final y, si hay varias direcciones, separadas solo por comas, sin espacios. Si no coincide, el navegador bloquea las peticiones con un error de CORS.

**Avisos de pago.** Para que un pedido quede aprobado aunque el comprador cierre la pestaña al pagar, las pasarelas tienen que poder avisarle al backend. Hay que registrar estas dos direcciones, cada una en el panel de su pasarela, y guardar en Render la clave que cada panel entrega:

| Pasarela | Dirección que se registra | Variable con su clave |
|---|---|---|
| Mercado Pago (Webhooks) | `https://vendely-api.onrender.com/api/public/pagos/webhook/mercadopago` | `MERCADOPAGO_WEBHOOK_SECRET` |
| PayPal (Webhooks) | `https://vendely-api.onrender.com/api/public/pagos/webhook/paypal` | `PAYPAL_WEBHOOK_ID` |

En Mercado Pago se suscribe el evento de **pagos**; en PayPal, `CHECKOUT.ORDER.APPROVED` y `PAYMENT.CAPTURE.COMPLETED`. Sin esas variables las dos rutas responden 503 y el pago sigue funcionando por el camino del navegador.

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

`frontend/vercel.json` hace que cualquier ruta sirva `index.html`:

```json
{ "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }] }
```

Las pantallas (`/login`, `/panel/clientes`, `/tienda/{subdominio}`…) las maneja React Router en el navegador; en Vercel solo existe `index.html`. Sin esta regla, abrir una de esas rutas directamente (recargar, un enlace, una pestaña nueva) da **404**. Los archivos que sí existen, como `/assets/...`, se siguen sirviendo normalmente.

Para usar la aplicación, entra siempre por la dirección de producción (`https://shopity-7zya.vercel.app`). Cada despliegue tiene además su propia dirección larga (`shopity-7zya-xxxx-….vercel.app`), pero el backend no la acepta por CORS.

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

Al crear la base, la migración `db/migration-local/V3__usuarios_prueba.sql` deja una cuenta lista por cada rol (solo en desarrollo: en el servidor esa carpeta no se aplica):

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
| `PUT /api/panel/tiendas/{idTienda}` | ADMIN_TIENDA (solo si es suya) | Editar nombre, descripción, contacto, logo, color e invitados (pantalla "Personalizar") |
| `GET /api/public/tiendas/{subdominio}` | Abierto | Datos públicos de una tienda activa (vitrina) |
| `GET /api/public/paises` | Abierto | Países, para los selectores de tienda, direcciones y tarifas |

**Reglas:**

- El subdominio solo acepta letras, números y guiones (se guarda en minúsculas), es único y **no se cambia** después de crear la tienda, porque es la dirección que usan los clientes.
- Algunos subdominios están reservados por Vendely (`www`, `api`, `admin`, `panel`, `login`…).
- Una tienda suspendida no aparece en la vitrina pública.
- **País:** al crear la tienda se elige el país (`idPais`, por defecto `PE`). Define la moneda, el impuesto y la lista de ubicaciones, y **no se cambia** después. Solo se aceptan países habilitados: hoy **Perú** (soles, IGV 18 %, ubigeo) y **Chile** (pesos sin decimales, IVA 19 %, comunas).

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

**Datos del producto** (`ProductoRequest`): nombre, descripción, marca, precio, stock y activo, más:

- `precioComparacion`: precio "antes", que la tienda muestra tachado (debe ser mayor que el precio).
- `sku`: código interno del vendedor.
- `imagenes`: hasta 5 enlaces `https://`, en orden (la primera es la principal).
- `categorias`: ids de las categorías de la tienda donde aparece.
- `opciones` y `variantes`: para productos con talla, color, etc. (ver abajo).

**Reglas:**

- El nombre es único dentro de la tienda.
- El precio debe ser mayor que 0 y el stock, mayor o igual a 0.
- Al cambiar el precio no se edita el anterior: se cierra y se crea uno nuevo, así las ventas pasadas conservan el precio con el que se hicieron.
- Un producto con ventas no se puede eliminar (409); se puede desactivar.

### Variantes (talla, color…)

Un producto puede tener hasta 3 **opciones** (por ejemplo `Talla: S, M, L` y `Color: Negro, Blanco`). Cada combinación (`M / Negro`) es una **variante** con su propio precio, precio de comparación, stock y SKU; si no se indican, toma los del producto.

- Una variante necesita un valor por cada opción, y no puede haber dos con la misma combinación (lo revisa la base, trigger T5).
- Una variante que ya se vendió no se borra.
- En el panel se editan dentro del formulario del producto (`EditorVariantes`); en la tienda, el comprador elige la combinación en el detalle del producto.

### Categorías

| Endpoint | Qué hace |
|---|---|
| `GET /api/panel/tiendas/{idTienda}/categorias` | Lista las categorías de la tienda |
| `POST /api/panel/tiendas/{idTienda}/categorias` | Crear (nombre hasta 100, descripción hasta 500, activa) |
| `PUT /api/panel/tiendas/{idTienda}/categorias/{idCategoria}` | Editar |
| `DELETE /api/panel/tiendas/{idTienda}/categorias/{idCategoria}` | Eliminar |
| `GET /api/public/tiendas/{subdominio}/categorias` | Categorías activas con productos, para los filtros de la tienda |

### Inventario

| Endpoint | Qué hace |
|---|---|
| `GET /api/panel/tiendas/{idTienda}/inventario` | Stock de cada variante, con su mínimo y la fecha de la última reposición |
| `PATCH /api/panel/tiendas/{idTienda}/inventario/{idVariante}` | Cambiar el inventario de una variante |

El `PATCH` acepta al menos uno de estos campos:

- `recibir`: unidades que llegaron. Se **suman** al stock con un solo `UPDATE` en la base (si en ese momento entra una venta, ninguna pisa a la otra) y se anota la fecha de reposición.
- `cantidad`: corregir el stock a un valor exacto (por ejemplo, después de contar el almacén).
- `stockMinimo`: debajo de ese número, el producto se marca con **stock bajo** en el panel.

## Envíos

Cada tienda define cuánto cobra por enviar: **por región** dentro de su país y **por país** hacia el extranjero. Si no hay tarifa para un destino, la tienda no vende ahí.

| Endpoint | Qué hace |
|---|---|
| `GET /api/panel/tiendas/{idTienda}/envios` | Tarifas nacionales e internacionales de la tienda |
| `PUT /api/panel/tiendas/{idTienda}/envios/nacional/{idDivision}` | Crear o editar la tarifa a una región del país (departamento en Perú, región en Chile) |
| `PUT /api/panel/tiendas/{idTienda}/envios/internacional/{idPais}` | Crear o editar la tarifa a otro país |
| `DELETE /api/panel/tiendas/{idTienda}/envios/internacional/{idPais}` | Dejar de vender a ese país |
| `GET /api/public/tiendas/{subdominio}/envios` | Países a los que vende la tienda |

Cada tarifa tiene `costo` (0 = gratis), `envioGratisDesde` (opcional), días de entrega mínimo y máximo, y `activa`. Los montos van en la moneda de la tienda.

- Una **venta al extranjero** es una exportación: **no lleva el impuesto** de la tienda (IGV o IVA); los impuestos de importación los paga el comprador al recibir.
- La base calcula el costo de envío y el impuesto de cada pedido (trigger T2), así que el total siempre coincide con las tarifas.

## Panel del superadmin

Solo para la cuenta `SUPERADMIN` (`admin@vendely.pe`). Las pantallas están en `/admin/tiendas` y `/admin/cuentas`.

| Endpoint | Qué hace |
|---|---|
| `GET /api/admin/resumen` | Números de la plataforma: tiendas activas y suspendidas, dueños, clientes, pedidos y ventas pagadas **por moneda** |
| `GET /api/admin/tiendas` | Todas las tiendas |
| `PATCH /api/admin/tiendas/{idTienda}/estado` | Suspender o reactivar una tienda (`{ "activa": false }`) |
| `GET /api/admin/cuentas` | Todas las cuentas |
| `PATCH /api/admin/cuentas/{idLogin}/estado` | Desactivar o reactivar una cuenta |

Las ventas se suman por moneda (soles, pesos chilenos…) porque cada tienda cobra en la moneda de su país: sumarlas sin convertir daría un número falso.

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
| `GET /api/public/tiendas/{subdominio}/productos` | Abierto | Catálogo de la tienda (con imagen, precio tachado y variantes) |
| `GET /api/public/tiendas/{subdominio}/productos/{idProducto}` | Abierto | Detalle de un producto |
| `GET /api/public/tiendas/{subdominio}/categorias` | Abierto | Categorías para filtrar la vitrina |
| `GET /api/public/tiendas/{subdominio}/envios` | Abierto | Países a los que vende la tienda |
| `GET /api/cliente/direcciones` | CLIENTE | Mis direcciones |
| `POST /api/cliente/direcciones` | CLIENTE | Agregar una dirección (en el país de la tienda o en otro país con tarifa) |
| `GET /api/cliente/direcciones/{idDireccion}/envio` | CLIENTE | Costo y plazo de envío a esa dirección, antes de pagar |
| `GET /api/cliente/pedidos` | CLIENTE | Mis pedidos |
| `GET /api/cliente/pedidos/{idPedido}` | CLIENTE | Detalle de un pedido |
| `POST /api/cliente/pedidos` | CLIENTE | Crear un pedido desde el carrito |
| `POST /api/cliente/pedidos/{idPedido}/pago` | CLIENTE | Iniciar el pago (PayPal o Mercado Pago) |
| `POST /api/cliente/pedidos/{idPedido}/pago/confirmar` | CLIENTE | Confirmar el resultado del pago |
| `POST /api/public/pagos/webhook/mercadopago` | Mercado Pago | Aviso de la pasarela (firmado; no lo llama el navegador) |
| `POST /api/public/pagos/webhook/paypal` | PayPal | Aviso de la pasarela (firmado; no lo llama el navegador) |
| `GET /api/public/tiendas/{subdominio}/productos` | Todos | Catálogo, con filtros y paginación (ver abajo) |
| `GET /api/cliente/pedidos/{idPedido}/comprobante` | CLIENTE | Descarga su comprobante en PDF |
| `PATCH /api/panel/tiendas/{idTienda}/pedidos/{idPedido}/envio` | ADMIN_TIENDA | Marca el pedido como enviado (empresa y número) |
| `PATCH /api/panel/tiendas/{idTienda}/pedidos/{idPedido}/entrega` | ADMIN_TIENDA | Marca el pedido como entregado |
| `PATCH /api/panel/tiendas/{idTienda}/pedidos/{idPedido}/cancelacion` | ADMIN_TIENDA | Cancela el pedido y devuelve el stock |
| `GET /api/panel/tiendas/{idTienda}/pedidos/{idPedido}/comprobante` | ADMIN_TIENDA | Descarga el comprobante de la venta |
| `GET /api/panel/tiendas/{idTienda}/pedidos/{idPedido}/notificaciones` | ADMIN_TIENDA | Los correos enviados por ese pedido |

**Flujo de compra:** vitrina → carrito → ingresar → checkout (dirección y costo de envío) → pedido → pago → vuelve a `/tienda/{subdominio}/pago/resultado` (usa `FRONTEND_URL`) → "Mis pedidos".

- La tienda del cliente sale de su token, no de la petición: un cliente no puede ver ni pagar pedidos de otra tienda.
- **Catálogo:** el filtrado y la paginación los hace la base, no el navegador: la tienda pide solo los doce productos que va a mostrar. Los filtros viajan en la URL (`?q=polo&categoria=3&min=20&max=80&stock=1&orden=precio_asc&pagina=2`), así que un enlace con filtros abierto en otra pestaña muestra exactamente lo mismo.
- **Direcciones:** en un país con lista oficial (Perú, Chile) el distrito o la comuna se elige de la lista; en otros países se escriben región, ciudad y código postal. Si la tienda no tiene tarifa para ese destino, el checkout lo avisa y no deja crear el pedido.
- **Pago en modo de prueba:** se usa **PayPal Sandbox** si están `PAYPAL_CLIENT_ID` y `PAYPAL_CLIENT_SECRET`; si no, **Mercado Pago Checkout Pro** si está `MERCADOPAGO_ACCESS_TOKEN`. PayPal cobra en dólares, así que el monto en soles se convierte con `PAYPAL_TIPO_CAMBIO` (3.75 soles por dólar por defecto). Sin credenciales, iniciar el pago responde 503 "El pago en línea no está disponible por ahora".
- Al confirmar, el backend consulta el pago en la pasarela y compara estado, pedido y monto: nunca confía en lo que diga el navegador.
- **Después del pago:** el pedido pasa de *sin enviar* a *enviado* (el dueño escribe la empresa y el número de seguimiento) y de ahí a *entregado*. Cancelar solo se puede antes de enviar: devuelve el stock al inventario y, si ya se había cobrado, deja el pago como reembolsado. Cada cambio queda con su fecha, y el cliente ve esa línea de tiempo en "Mis pedidos".
- **Correos:** al aprobarse el pago salen dos (la confirmación al cliente, con el comprobante en PDF adjunto, y el aviso de venta nueva al dueño); al marcar el envío, uno al cliente con el número de seguimiento; y cuando a un producto le queda menos que su stock mínimo, un aviso al dueño. Se envían en segundo plano y cada intento queda registrado (enviado, fallido con su motivo, o simulado si no hay `RESEND_API_KEY`): un correo que no sale no afecta a la venta.
- **El pago no depende de que el comprador vuelva.** Si cierra la pestaña o se le corta el internet en la pasarela, el pedido quedaría pagado y pendiente. Para eso hay dos respaldos: el **aviso** que manda la pasarela (webhook), que entra por su propia ruta y pasa por la misma verificación que el regreso del navegador, y una **revisión cada 15 minutos** que le pregunta a la pasarela por los pagos que llevan más de 30 minutos pendientes. En local, sin una dirección pública donde recibir avisos, esa revisión es la que cierra los pagos.

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
