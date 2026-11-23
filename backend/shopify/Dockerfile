# Imagen del backend. La usan docker-compose.yml y Render.

# Etapa 1: compilar
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Primero solo el pom.xml y las dependencias: esta capa queda en cache y un
# cambio en el codigo Java no vuelve a descargar todo Maven.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# Etapa 2: ejecutar (solo el JRE y el .jar)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
