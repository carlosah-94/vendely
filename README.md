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
├── db/init/ # 01_esquema.sql y 02_datos_iniciales.sql (Docker los ejecuta en orden)
├── db/tests/ # 03_pruebas.sql (casos que la base debe aceptar y rechazar)
├── postman/ # Colecciones de Postman para probar la API
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

- Base de datos final (24 tablas, Perú) en `db/init/`, con sus pruebas en `db/tests/`
- Seguridad: registro, inicio de sesión, JWT, roles, CORS y manejo de errores (ver sección **Seguridad**)

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

### Probar con Postman

1. Importa `postman/Vendely_Seguridad.postman_collection.json`.
2. Ejecuta **Registrar dueño de tienda**: el token se guarda solo en la variable `{{token}}`.
3. Para registrar clientes hace falta una tienda. Mientras no exista el endpoint de tiendas, créala en la base (el dueño del paso 2 tiene `id_propietario = 1` si la base está vacía):

   ```sql
   INSERT INTO tiendas (id_propietario, nombre, subdominio, correo_contacto)
   VALUES (1, 'Zapatillas Carlos', 'zapatillas-carlos', 'ventas@zapatillas.pe');
   ```

4. Ejecuta el resto de peticiones en orden. La carpeta **Casos que deben fallar** comprueba los errores 400, 401, 404 y 409.

