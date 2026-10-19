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

## Autenticación

La aplicación requiere login (Spring Security, formulario + sesión). La cuenta se define
por completo mediante variables de entorno — **no hay credenciales por defecto**; si faltan,
la aplicación no arranca.

| Variable | Descripción |
|---|---|
| `APP_SECURITY_USERNAME` | Usuario para iniciar sesión. |
| `APP_SECURITY_PASSWORD_HASH` | Hash **BCrypt** de la contraseña (nunca la contraseña en texto plano). |
| `APP_SECURITY_ROLE` | Opcional, por defecto `USER`. |

### Generar el hash BCrypt

```bash
cd release-mail-generator
./mvnw -q compile
java -cp target/classes release_mail_generator.util.PasswordHashGenerator
# Pide la contraseña de forma interactiva (no queda en el historial de la terminal)
# e imprime: APP_SECURITY_PASSWORD_HASH=$2a$10$...
```

### Ejecutar con las variables definidas

```bash
# PowerShell
$env:APP_SECURITY_USERNAME = "tu-usuario"
$env:APP_SECURITY_PASSWORD_HASH = '$2a$10$...'
./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Todas las páginas y APIs (generación de correos, RFC, historial, búsqueda, exportaciones)
requieren sesión iniciada; solo `/login` y los recursos estáticos (`/css/**`, `/js/**`,
`/images/**`) son públicos. CSRF permanece habilitado: los formularios y llamadas `fetch`
del frontend envían el token automáticamente (ver `/js/csrf.js`).

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

## Persistencia en producción (PostgreSQL)

El perfil `prod` usa **PostgreSQL** (no H2) como base de datos, con el esquema gestionado por
**migraciones versionadas de Flyway** (`src/main/resources/db/migration/`). H2 sigue siendo la
base para desarrollo (`dev`) y pruebas (`test`) — no se requiere PostgreSQL local para trabajar
en el día a día del proyecto.

### Variables de entorno (obligatorias en `prod`, sin valores por defecto)

| Variable | Descripción |
|---|---|
| `DB_URL` | `jdbc:postgresql://host:5432/nombre_bd` |
| `DB_USERNAME` | Usuario de la base de datos |
| `DB_PASSWORD` | Contraseña de la base de datos |

Si falta alguna, la aplicación **no arranca** (mismo patrón fail-fast que `APP_SECURITY_*`).
En `prod`, `spring.jpa.hibernate.ddl-auto=validate`: Hibernate solo verifica que el esquema
coincide con las entidades; nunca genera ni altera DDL. El esquema lo crea/actualiza
exclusivamente Flyway.

### Ejecutar localmente contra PostgreSQL

Requiere una instancia PostgreSQL accesible (local, Docker, o en la nube):

```bash
# PowerShell
$env:DB_URL = "jdbc:postgresql://localhost:5432/release_mail_generator"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "tu-password"
$env:APP_SECURITY_USERNAME = "tu-usuario"
$env:APP_SECURITY_PASSWORD_HASH = '$2a$10$...'

cd release-mail-generator
./mvnw spring-boot:run "-Dspring-boot.run.profiles=prod"
```

Al arrancar, Flyway aplica `V1__init_schema.sql` sobre una base vacía automáticamente.

### Configurar Render manualmente (sin asumir disco persistente ni servicios automáticos)

Render **no** provisiona PostgreSQL ni disco automáticamente con estos cambios — se asume que
administras la base de datos externamente (Render Postgres, Supabase, RDS, etc.) y solo
conectas la app a ella:

1. Crea/obtén una instancia PostgreSQL por tu cuenta (el proveedor que prefieras).
2. En el servicio Web de Render, agrega las variables de entorno:

   | Variable | Valor |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DB_URL` | `jdbc:postgresql://<host>:<puerto>/<bd>` |
   | `DB_USERNAME` | usuario de esa base |
   | `DB_PASSWORD` | contraseña de esa base |
   | `APP_SECURITY_USERNAME` / `APP_SECURITY_PASSWORD_HASH` | igual que antes |

3. Redeploy. En el primer arranque, Flyway crea el esquema sobre la base vacía.
4. Si la base PostgreSQL vive en otro proveedor con red privada/pública, asegúrate de que
   el servicio de Render tenga permitido el acceso (allowlist de IP, SSL según el proveedor,
   etc.) — esto es responsabilidad del proveedor de la base, no de esta aplicación.

### Importación de los datos H2 existentes

`util/H2ToPostgresMigrator` **importa** los datos existentes (RFC técnicos + historial de
documentos, incluyendo contenido) desde el H2 de desarrollo hacia PostgreSQL.

> ⚠️ **Esto es una herramienta de importación, no un respaldo.** Antes de ejecutarla, haz un
> respaldo real de ambas bases: copia el archivo `./data/rfcdb.mv.db` (y `.trace.db` si existe)
> a otra ubicación, y si el destino PostgreSQL ya tiene datos, saca un `pg_dump` de esa base.
> Además, **detén las escrituras en origen y destino mientras corre** — una comparación
> consistente entre ambos requiere que ninguno cambie durante la importación.

**Requisito previo:** el esquema de destino debe existir ya en PostgreSQL (Flyway lo crea al
arrancar la aplicación con el perfil `prod` por primera vez — ver sección anterior). Esta
herramienta no crea tablas, solo inserta filas en tablas que ya existen.

**Garantías de la herramienta:**

- Abre el H2 origen en **modo solo lectura** (`ACCESS_MODE_DATA=r` + `Connection.setReadOnly`)
  — nunca escribe ni borra ahí, con éxito o con error.
- Corre dentro de **una única transacción** en el destino: solo hace `commit` si **todas** las
  filas del origen quedaron insertadas o verificadas como idénticas a lo ya existente. Ante
  cualquier conflicto, hash inválido o error, hace **rollback explícito** y termina con código
  de salida distinto de cero — nunca deja una importación a medias.
- Nunca usa `UPDATE` ni `DELETE` en el destino: solo `INSERT` de filas nuevas. Si un id ya
  existe en el destino, compara **todos los campos** (incluyendo JSON de casos de prueba,
  evidencias, contenido y hashes) contra el origen — si coinciden se cuenta como verificado;
  si difieren, se informa el id, la tabla y los campos distintos, y se aborta toda la
  importación sin sobrescribir nada. Permite sin problema que el destino tenga registros
  adicionales que no existan en el origen.
- Antes de insertar cada versión de documento, recalcula su hash SHA-256 (mismas reglas que
  `DocumentHistoryService`) y lo compara contra `content_hash` del origen; si no corresponde,
  aborta toda la importación sin modificar el origen.
- Ejecutarla dos veces con el mismo origen no duplica ni modifica nada (idempotente).

```bash
cd release-mail-generator
./mvnw -q compile

# La contraseña SIEMPRE por variable de entorno, nunca como argumento -D.
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "tu-password"

java -cp "target/classes;$(./mvnw -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)" `
     -DsourceH2Path=./data/rfcdb `
     -DtargetDbUrl=jdbc:postgresql://localhost:5432/release_mail_generator `
     release_mail_generator.util.H2ToPostgresMigrator
```

(En Windows, separa el classpath con `;`; el driver de PostgreSQL ya está entre las
dependencias del proyecto tras compilar.) El comando imprime un resumen por tabla (leídos,
insertados, existentes idénticos, conflictos, verificados), y un mensaje final que distingue
claramente **"IMPORTACIÓN CONFIRMADA"** (commit) de **"IMPORTACIÓN REVERTIDA"** (rollback, nada
cambió). Nunca imprime contraseñas, cadenas de conexión con credenciales, contenido de
documentos, JSON completo ni imágenes base64 — solo ids, nombres de tabla y nombres de campo.

**Si la importación se revierte** (conflictos o hash inválido en el origen): el destino queda
exactamente como estaba antes de ejecutar la herramienta (no hace falta deshacer nada
manualmente). Revisa los mensajes "Problemas encontrados" para identificar qué id/tabla/campo
causó el conflicto, corrígelo en el origen o decide manualmente cómo conciliarlo, y vuelve a
ejecutar la herramienta.

### Procedimiento de reversión (rollback)

Como el H2 original nunca se modifica ni se borra durante este proceso, volver atrás es
inmediato:

1. Quita o cambia `SPRING_PROFILES_ACTIVE` para que no sea `prod` (o define explícitamente
   las propiedades de datasource de vuelta a H2 si se requiere mantener el nombre de perfil).
2. No hace falta restaurar nada: el archivo `./data/rfcdb.mv.db` sigue intacto en todo momento.
3. Si ya se habían enviado cambios reales a producción contra PostgreSQL después de la
   migración, esos cambios quedarían solo en PostgreSQL — considera eso antes de revertir en un
   entorno con tráfico real.

---

## Perfiles de configuración

| Archivo | Perfil | Uso |
|---|---|---|
| `application.properties` | (compartido) | H2 datasource (dev/test), puerto |
| `application-dev.properties` | `dev` | Cache off, H2 Console on, SQL logs |
| `application-prod.properties` | `prod` | PostgreSQL + Flyway + `ddl-auto=validate`, cache on, H2 Console off |

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

En **dev/test** la base H2 se crea/actualiza automáticamente en `./data/rfcdb.mv.db` (DDL auto
`update`). En **prod** el esquema en PostgreSQL lo crea Flyway a partir de
`src/main/resources/db/migration/V1__init_schema.sql` (Hibernate solo valida, no genera DDL).

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

La mayoría de los tests usan H2 en memoria (`src/test/resources/application.properties`) — no
generan archivos en disco. Adicionalmente, `integration/PostgresPersistenceIntegrationTest`
valida el comportamiento real contra **PostgreSQL** (migraciones Flyway sobre base vacía, CRUD,
historial, búsqueda, dashboard y persistencia tras reiniciar el contexto), y
`util/H2ToPostgresMigratorTest` valida la herramienta de importación (destino vacío,
idempotencia, registros adicionales en destino, conflictos de datos, hash de origen inválido,
atomicidad ante error parcial, y valores nulos/Unicode/fechas) — ambas usan
`io.zonky.test:embedded-postgres`, que descarga y ejecuta un binario real de PostgreSQL sin
necesidad de Docker ni de una instancia PostgreSQL instalada localmente.

---

## Mejoras futuras sugeridas

- [x] Autenticación con Spring Security
- [x] Migración a PostgreSQL para producción (perfil `prod`, ver sección "Persistencia en producción")
- [ ] Exportar RFC a formato Word (.docx)
- [ ] Historial de correos generados
- [ ] Integración con Groq/OpenAI para generación automática de textos
- [ ] Bootstrap y fuentes hospedados localmente (independencia de CDN)
