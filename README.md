# Shopify Clone – Proyecto Full Stack (UTP 2026)

Clon simplificado de un e-commerce, desarrollado como proyecto del curso Full Stack.

## Stack

- **Backend:** Java 21, Spring Boot 3.3.4, Spring Security, JWT (jjwt), Spring Data JPA, PostgreSQL
- **Frontend:** React 19 + Vite, Tailwind CSS v4, React Router, Axios
- **Infraestructura:** Docker / Docker Compose

## Estructura
FullStack/
├── backend/shopify/ # API REST en Spring Boot (proyecto NetBeans/Maven)
├── frontend/ # SPA en React
├── db/init/ # 01_esquema.sql, 02_datos_iniciales.sql y 03_usuarios_prueba.sql (Docker los ejecuta en orden)
├── db/tests/ # 03_pruebas.sql (casos que la base debe aceptar y rechazar)
├── postman/ # Colección de Postman para probar la API (Vendely_API)
└── docker-compose.yml


## Estado actual del proyecto

### ✅ Implementado

- Estructura completa de paquetes del backend: `entity`, `dto`, `repository`, `security`, `service`, `controller`
- Entidades JPA mapeadas a la base final: `Login`, `Propietario`, `Tienda` (las demás se agregan con cada módulo)
- Clase principal `Shopify.java` (`@SpringBootApplication`) funcional
- `pom.xml` con todas las dependencias necesarias (Web, JPA, Security, Validation, PostgreSQL, JWT, DevTools)
- Bean de configuración CORS en `SecurityConfig`
- Frontend inicializado con Vite + React, Tailwind CSS v4 configurado vía plugin de Vite
- Estructura de carpetas del frontend: `components/`, `pages/`, `services/`, `context/`
- Dependencias del frontend instaladas: `react-router-dom`, `axios`, `tailwindcss`

- Base de datos final (30 tablas, multipaís) en `db/init/`, con sus pruebas en `db/tests/`
- Seguridad: registro, inicio de sesión, JWT, roles, CORS y manejo de errores (ver sección **Seguridad**)
- Tiendas: el dueño crea, lista y edita sus tiendas; vitrina pública por subdominio (ver sección **Tiendas**)

### 🚧 Pendiente

- Lógica de negocio en `service/`, `controller/`, `repository/` y la mayoría de `dto/` (actualmente son clases vacías)
- Páginas y componentes reales del frontend (catálogo, login, panel admin)
- `Dockerfile` del frontend (el de `backend/shopify/` sí existe)

## Cómo ejecutar el proyecto

### Opción A — Con Docker (cuando el backend y `Dockerfile` del frontend estén listos)

1. Copia `.env.example` a `.env` y ajusta los valores.
2. Levanta todo:
   ```bash
   docker compose up --build
Backend disponible en http://localhost:8080
Frontend disponible en http://localhost:5173
Opción B — Desarrollo local (sin Docker)
Backend:

Abre la carpeta backend/shopify en NetBeans (proyecto Maven).
Configura src/main/resources/application.yml con los datos de tu base de datos PostgreSQL local.
Ejecuta la clase Shopify.java (botón Run de NetBeans, o F6).
La API queda disponible en http://localhost:8080.
Frontend:

cd frontend
npm install
npm run dev
La app queda disponible en http://localhost:5173.

Nota: mientras el backend no tenga implementados los endpoints y la seguridad, el frontend no podrá autenticarse ni traer datos reales.

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

Para usar el usuario en un controller:

```java
@GetMapping("/api/cliente/carrito")
public CarritoResponse miCarrito(@AuthenticationPrincipal UsuarioAutenticado yo) {
    return carritoService.delCliente(yo.idLogin(), yo.idTienda());
}
```

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
