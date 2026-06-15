FROM eclipse-temurin:17

WORKDIR /app

# 1. Copiar solo pom.xml y mvnw para cachear dependencias
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -q

# 2. Copiar el código fuente — esta capa se invalida con cada cambio en src/
COPY src src

RUN ./mvnw package -DskipTests

EXPOSE 8080

CMD ["java", "-jar", "target/release-mail-generator-0.0.1-SNAPSHOT.jar"]