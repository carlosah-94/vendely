# Imagen de produccion del frontend (issue #12).
#
# Dos etapas: la primera compila con Node y la segunda solo sirve los archivos
# estaticos con nginx. La imagen final no lleva Node ni node_modules.
#
# Para trabajar en local fuera de Docker se sigue usando `npm run dev`.

# Etapa 1: compilar
FROM node:22-alpine AS build
WORKDIR /app

# Primero solo los package*.json: si no cambian, Docker reutiliza la capa de
# dependencias y no vuelve a descargarlas en cada cambio de codigo.
COPY package.json package-lock.json ./
# ci (no install): respeta el package-lock.json y falla si esta desincronizado.
RUN npm ci

COPY . .

# Vite incrusta las variables VITE_* al compilar, no al arrancar el contenedor,
# por eso llega como ARG del build (docker-compose.yml: build.args).
ARG VITE_API_BASE_URL=http://localhost:8080/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# Etapa 2: servir con nginx
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
