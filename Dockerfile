# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Copiar solo pom.xml y mvnw primero para cachear dependencias
COPY release-mail-generator/pom.xml .
COPY release-mail-generator/mvnw .
COPY release-mail-generator/.mvn .mvn

RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copiar fuente — esta capa se invalida solo cuando cambia src/
COPY release-mail-generator/src src

RUN ./mvnw package -DskipTests

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/target/app.jar app.jar

EXPOSE 8080

CMD ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
