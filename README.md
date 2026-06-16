# Release Mail Generator

Herramienta interna para el equipo de QA de Megacable. Genera correos HTML estructurados para los procesos de liberación de software: distribución de módulos, reportes RDL, VoBo UAT y RFC Técnico Final.

---

## Módulos

| Módulo | Descripción |
|---|---|
| **Liberación** | Correo de distribución de módulos, Citrix, DLL, WinterX, scripts y SPs |
| **RDL** | Correo de carga de reportes en servidores MEGANG-612 / NTRS02 |
| **VoBo UAT** | Correo de solicitud de validación con evidencias y capturas |
| **RFC Técnico** | Gestión completa (CRUD) de RFC técnicos con exportación a PDF y Markdown |

---

## Requisitos

- **Java 17**
- **Maven 3.8+** (incluido como wrapper `mvnw`)
- **Docker** (opcional, para contenedores)

---

## Ejecución local

### Opción A — Maven (recomendado para desarrollo)

```bash
cd release-mail-generator

# Perfil dev: activa H2 Console, deshabilita caché Thymeleaf
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

La aplicación estará disponible en: http://localhost:8080

**H2 Console** (solo perfil dev): http://localhost:8080/h2-console  
- JDBC URL: `jdbc:h2:file:./data/rfcdb`  
- User: `sa` | Password: *(vacío)*

### Opción B — JAR

```bash
cd release-mail-generator
./mvnw package -DskipTests
java -Dspring.profiles.active=dev -jar target/app.jar
```

---

## Docker

### Build y ejecución (proyecto interno)

```bash
cd release-mail-generator

# Build
docker build -t release-mail-generator .

# Ejecutar (modo producción)
docker run -p 8080:8080 \
  -v $(pwd)/data:/app/data \
  release-mail-generator
```

El volumen `-v $(pwd)/data:/app/data` persiste la base de datos H2 entre reinicios.

### Build desde la raíz del repositorio

```bash
# Desde la carpeta raíz del repositorio
docker build -t release-mail-generator -f Dockerfile .
docker run -p 8080:8080 -v $(pwd)/data:/app/data release-mail-generator
```

---

## Despliegue en Render

### Pasos

1. **Conectar repositorio** en [render.com](https://render.com) → *New Web Service*
2. **Runtime**: Docker
3. **Dockerfile Path**: `./Dockerfile` (raíz del repositorio)
4. **Variables de entorno**:

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `PORT` | `8080` (Render lo inyecta automáticamente) |

5. **Persistent Disk** (para persistencia real de RFC):
   - Configura un disco en Render montado en `/app/data`
   - En `application-prod.properties` cambia la URL H2 a:
     ```properties
     spring.datasource.url=jdbc:h2:file:/app/data/rfcdb;DB_CLOSE_ON_EXIT=FALSE
     ```

> **Importante:** Sin disco persistente, los RFC Técnicos se pierden al reiniciar el servicio. El disco cuesta ~$0.25/GB/mes en Render.

---

## Perfiles de configuración

| Archivo | Perfil | Uso |
|---|---|---|
| `application.properties` | (compartido) | H2 datasource, puerto |
| `application-dev.properties` | `dev` | Cache off, H2 Console on, SQL logs |
| `application-prod.properties` | `prod` | Cache on, H2 Console off |

---

## Arquitectura

```
release-mail-generator/          ← Repositorio Git
├── Dockerfile                   ← Imagen para Render (multi-stage, JDK→JRE)
└── release-mail-generator/      ← Proyecto Spring Boot
    ├── src/main/java/
    │   └── release_mail_generator/
    │       ├── controller/      ← Spring MVC controllers
    │       │   ├── ReleaseController.java   (GET /, POST /generate, POST /generate-rdl)
    │       │   ├── RfcTechnicalController.java  (CRUD /rfc/**)
    │       │   └── UatController.java       (POST /generate-uat, exports)
    │       ├── model/           ← POJOs + entidad JPA (RfcTechnicalRecord)
    │       ├── converter/       ← AttributeConverters JSON para listas JPA
    │       ├── repository/      ← Spring Data JPA (RfcTechnicalRepository)
    │       └── service/
    │           ├── EmailGeneratorService.java   ← HTML para Liberación y RDL
    │           ├── UatEmailService.java         ← HTML/PDF/MD para VoBo UAT
    │           └── RfcTechnicalService.java     ← CRUD + PDF/MD para RFC Técnico
    ├── src/main/resources/
    │   ├── templates/           ← Thymeleaf (UI de formularios y vistas)
    │   ├── static/
    │   │   ├── css/app.css      ← Estilos compartidos
    │   │   └── images/uat/      ← Imágenes de pasos de validación (step1-3)
    │   ├── application.properties
    │   ├── application-dev.properties
    │   └── application-prod.properties
    └── src/test/java/           ← Tests unitarios con JUnit 5 + Mockito
```

### Stack tecnológico

| Capa | Tecnología |
|---|---|
| Backend | Spring Boot 4, Spring MVC, Spring Data JPA |
| Persistencia | H2 Database (file-based) |
| Generación PDF | OpenPDF (LibrePDF) 1.3.x |
| Templates UI | Thymeleaf + Bootstrap 5.3 |
| Fuentes | Google Fonts (Inter) |
| Build | Maven 3 |
| Runtime | Java 17 (eclipse-temurin JRE) |
| Contenedor | Docker multi-stage |

---

## Estructura de la base de datos

La base H2 se crea automáticamente en `./data/rfcdb.mv.db` con DDL auto (`update`).

Tabla principal: `rfc_technical_records`

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | VARCHAR | UUID generado automáticamente |
| `rfc_number` | VARCHAR | Ej: RFC-23752 |
| `status` | VARCHAR | Borrador / En validación / Aprobado / Rechazado |
| `business_rules` | TEXT | JSON: `[{"description":"...","validationStatus":"..."}]` |
| `test_cases` | TEXT | JSON: lista de casos de prueba |
| `related_bugs` | TEXT | JSON: lista de bugs relacionados |
| `created_at` | TIMESTAMP | Fecha de creación (no editable) |
| `updated_at` | TIMESTAMP | Fecha de última modificación |

---

## Tests

```bash
cd release-mail-generator
./mvnw test
```

Los tests usan H2 en memoria (`src/test/resources/application.properties`) — no generan archivos en disco.

---

## Mejoras futuras sugeridas

- [ ] Autenticación básica con Spring Security
- [ ] Migración a PostgreSQL para producción robusta
- [ ] Exportar RFC a formato Word (.docx)
- [ ] Historial de correos generados
- [ ] Integración con Groq/OpenAI para generación automática de textos
- [ ] Bootstrap y fuentes hospedados localmente (independencia de CDN)
