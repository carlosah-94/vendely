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
├── db/init/ # Script de esquema inicial de la base de datos
└── docker-compose.yml


## Estado actual del proyecto

### ✅ Implementado

- Estructura completa de paquetes del backend: `entity`, `dto`, `repository`, `security`, `service`, `controller`
- Entidades JPA con campos y mapeo de relaciones: `Product`, `User`, `Category`
- Clase principal `Shopify.java` (`@SpringBootApplication`) funcional
- `pom.xml` con todas las dependencias necesarias (Web, JPA, Security, Validation, PostgreSQL, JWT, DevTools)
- Bean de configuración CORS en `SecurityConfig`
- Frontend inicializado con Vite + React, Tailwind CSS v4 configurado vía plugin de Vite
- Estructura de carpetas del frontend: `components/`, `pages/`, `services/`, `context/`
- Dependencias del frontend instaladas: `react-router-dom`, `axios`, `tailwindcss`

### 🚧 Pendiente

- Lógica de negocio en `service/`, `controller/`, `repository/` y la mayoría de `dto/` (actualmente son clases vacías)
- `SecurityConfig` incompleto: falta `@Configuration`, `@EnableWebSecurity`, el `securityFilterChain` y el `PasswordEncoder`
- `JwtService`, `JwtAuthFilter`, `CustomUserDetailsService` sin implementar
- `application.yml` sin configurar (falta datasource, puerto, propiedades JWT)
- `db/init/001_schema.sql` (script de esquema inicial)
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