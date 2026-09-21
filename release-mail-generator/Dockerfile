# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Copiar solo pom.xml y mvnw primero para cachear dependencias
COPY pom.xml mvnw ./
COPY .mvn .mvn

RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copiar fuente — esta capa se invalida solo cuando cambia src/
COPY src src

RUN ./mvnw package -DskipTests

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/target/app.jar app.jar

EXPOSE 8080

# Límites de memoria explícitos: sin esto, la JVM puede intentar usar más RAM
# de la que el plan de Render permite, provocando OOM kill y reinicios.
# MaxRAMPercentage limita el heap a un % de la memoria detectada del contenedor;
# MaxMetaspaceSize acota las clases cargadas; SerialGC reduce el overhead de
# memoria del recolector en instancias pequeñas (1 vCPU o menos).
CMD ["java", "-XX:MaxRAMPercentage=65.0", "-XX:MaxMetaspaceSize=192m", "-XX:+UseSerialGC", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
