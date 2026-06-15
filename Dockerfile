FROM eclipse-temurin:17

WORKDIR /app

# Copiar dependencias primero (cacheado hasta que cambie pom.xml)
COPY release-mail-generator/pom.xml .
COPY release-mail-generator/mvnw .
COPY release-mail-generator/.mvn .mvn

RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -q

# Copiar fuente — se invalida con cada cambio de código
COPY release-mail-generator/src src

RUN ./mvnw package -DskipTests

EXPOSE 8080

CMD ["java", "-jar", "target/release-mail-generator-0.0.1-SNAPSHOT.jar"]

CMD ["java", "-jar", "target/release-mail-generator-0.0.1-SNAPSHOT.jar"]