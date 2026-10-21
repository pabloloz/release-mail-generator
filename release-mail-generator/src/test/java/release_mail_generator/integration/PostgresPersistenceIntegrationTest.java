package release_mail_generator.integration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import release_mail_generator.ReleaseMailGeneratorApplication;
import release_mail_generator.model.DocumentVersion;
import release_mail_generator.model.RfcTechnicalRecord;
import release_mail_generator.repository.DocumentVersionRepository;
import release_mail_generator.repository.RfcTechnicalRepository;
import release_mail_generator.service.DocumentHistoryService;
import release_mail_generator.service.RfcTechnicalService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración contra una PostgreSQL <b>real</b> (no H2, no mocks).
 *
 * <p>Usa {@code io.zonky.test:embedded-postgres}, que descarga y ejecuta un binario real de
 * PostgreSQL (sin Docker — funciona también en Windows), para poder validar aquí mismo:</p>
 * <ul>
 *   <li>Que las migraciones Flyway ({@code db/migration/V1__init_schema.sql}) se aplican
 *       limpiamente sobre una base PostgreSQL vacía.</li>
 *   <li>Que {@code spring.jpa.hibernate.ddl-auto=validate} no encuentra discrepancias entre
 *       las entidades JPA y el esquema creado por Flyway.</li>
 *   <li>CRUD de RFC Técnico, historial de documentos, búsqueda y dashboard (incluida la
 *       agregación por día que reemplazó a la función H2 {@code FORMATDATETIME}).</li>
 *   <li>Que los datos persisten después de "reiniciar" la aplicación (se cierra el contexto
 *       de Spring y se levanta uno nuevo apuntando a la misma base).</li>
 * </ul>
 *
 * <p>El servidor PostgreSQL embebido se arranca una única vez en la inicialización estática
 * del campo {@code postgres} (patrón "singleton container"), en lugar de en un {@code @BeforeAll},
 * porque Spring evalúa los métodos {@code @DynamicPropertySource} durante la preparación de la
 * instancia de test — un punto que puede ejecutarse antes de que el cuerpo de un {@code @BeforeAll}
 * corra. Inicializarlo como campo estático garantiza que ya está disponible en ese momento.</p>
 */
class PostgresPersistenceIntegrationTest {

    private static final EmbeddedPostgres postgres = startEmbeddedPostgres();
    private static final String jdbcUrl = postgres.getJdbcUrl("postgres", "postgres");

    private static EmbeddedPostgres startEmbeddedPostgres() {
        try {
            EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { pg.close(); } catch (Exception ignored) { }
            }));
            return pg;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo iniciar PostgreSQL embebido", e);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.security.username", () -> "testadmin");
        registry.add("app.security.password-hash",
                () -> "$2a$10$fIpsCenZP9IH4bNuq1qGhOFsnrKYGcAQCcHKPgeTLBTm7619BbnOy");
    }

    @Nested
    @SpringBootTest
    class CrudHistoriaBusquedaYDashboard {

        @Autowired private RfcTechnicalService rfcService;
        @Autowired private RfcTechnicalRepository rfcRepository;
        @Autowired private DocumentHistoryService historyService;
        @Autowired private DocumentVersionRepository documentVersionRepository;

        @Test
        void migracionYValidacionDeEsquema_sonExitosas() {
            // Si llegamos aquí, Flyway migró sobre PostgreSQL vacío y Hibernate validó el
            // esquema sin lanzar SchemaManagementException al arrancar el contexto.
            assertThat(rfcRepository.count()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void crudDeRfcTecnico_funcionaSobrePostgres() {
            RfcTechnicalRecord record = new RfcTechnicalRecord();
            record.setRfcNumber("RFC-PG-" + UUID.randomUUID().toString().substring(0, 8));
            record.setChangeName("Prueba de integración PostgreSQL");
            record.setTesterName("QA Automatizado");

            RfcTechnicalRecord saved = rfcService.save(record);
            assertThat(saved.getId()).isNotBlank();
            assertThat(saved.getStatus()).isEqualTo("Borrador");

            var found = rfcService.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getChangeName()).isEqualTo("Prueba de integración PostgreSQL");

            rfcService.delete(saved.getId());
            assertThat(rfcService.findById(saved.getId())).isEmpty();
        }

        @Test
        void historialYBusqueda_funcionanSobrePostgres() {
            String ref = "PG-HIST-" + UUID.randomUUID().toString().substring(0, 8);
            historyService.saveVersion("RFC", ref, "Documento de prueba PG", "QA",
                    "<p>contenido de prueba</p>", "CREATED", "HTML");

            List<DocumentVersionRepository.DocumentVersionSummary> versions = historyService.listVersionsOf("RFC", ref);
            assertThat(versions).hasSize(1);
            assertThat(versions.get(0).getContentHash()).isNotBlank();

            List<DocumentVersion> searchResults = historyService.search(ref);
            assertThat(searchResults).anyMatch(v -> v.getDocumentRef().equals(ref));
        }

        @Test
        void dashboard_agregaActividadDiariaCorrectamente_sinFuncionesEspecificasDeMotor() {
            // Dos documentos "hoy" y uno "hace 3 días" insertados directamente para controlar createdAt.
            LocalDateTime today = LocalDateTime.now();
            LocalDateTime threeDaysAgo = today.minusDays(3);

            documentVersionRepository.save(DocumentVersion.builder()
                    .id(UUID.randomUUID().toString()).documentType("RFC").documentRef("PG-DASH-1")
                    .title("t1").author("QA").content("c1").contentHash("hash1").action("CREATED")
                    .format("HTML").versionNumber(1).createdAt(today).contentSize(2).build());
            documentVersionRepository.save(DocumentVersion.builder()
                    .id(UUID.randomUUID().toString()).documentType("UAT").documentRef("PG-DASH-2")
                    .title("t2").author("QA").content("c2").contentHash("hash2").action("CREATED")
                    .format("HTML").versionNumber(1).createdAt(today).contentSize(2).build());
            documentVersionRepository.save(DocumentVersion.builder()
                    .id(UUID.randomUUID().toString()).documentType("RDL").documentRef("PG-DASH-3")
                    .title("t3").author("QA").content("c3").contentHash("hash3").action("CREATED")
                    .format("HTML").versionNumber(1).createdAt(threeDaysAgo).contentSize(2).build());

            Map<String, Object> dashboard = historyService.getDashboardData();
            assertThat(dashboard).containsKeys("counts", "total", "chart", "recent");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chart = (List<Map<String, Object>>) dashboard.get("chart");
            assertThat(chart).isNotEmpty();
            long totalInChart = chart.stream().mapToLong(p -> ((Number) p.get("count")).longValue()).sum();
            assertThat(totalInChart).isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void losDatosPersistenDespuesDeReiniciarLaAplicacion() {
        String marker = "PG-RESTART-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, String> props = Map.of(
                "spring.datasource.url", jdbcUrl,
                "spring.datasource.username", "postgres",
                "spring.datasource.password", "postgres",
                "spring.datasource.driver-class-name", "org.postgresql.Driver",
                "spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect",
                "spring.flyway.enabled", "true",
                "spring.flyway.locations", "classpath:db/migration",
                "spring.jpa.hibernate.ddl-auto", "validate",
                "app.security.username", "testadmin",
                "app.security.password-hash", "$2a$10$fIpsCenZP9IH4bNuq1qGhOFsnrKYGcAQCcHKPgeTLBTm7619BbnOy"
        );

        // Se usan System properties (no SpringApplicationBuilder.properties(), que tiene MENOR
        // precedencia que el application.properties de test) para garantizar que estos valores
        // ganan sobre la configuración H2 del classpath de test.
        props.forEach(System::setProperty);
        try {
            // ── "Arranque" #1: guarda un RFC y cierra el contexto (simula apagar la app) ──
            try (ConfigurableApplicationContext ctx1 = new SpringApplicationBuilder(ReleaseMailGeneratorApplication.class)
                    .web(WebApplicationType.NONE)
                    .run()) {
                RfcTechnicalService svc = ctx1.getBean(RfcTechnicalService.class);
                RfcTechnicalRecord record = new RfcTechnicalRecord();
                record.setRfcNumber(marker);
                record.setChangeName("Sobrevive a un reinicio");
                svc.save(record);
            }

            // ── "Arranque" #2: nuevo contexto contra la MISMA base; el dato debe seguir ahí ──
            try (ConfigurableApplicationContext ctx2 = new SpringApplicationBuilder(ReleaseMailGeneratorApplication.class)
                    .web(WebApplicationType.NONE)
                    .run()) {
                RfcTechnicalService svc = ctx2.getBean(RfcTechnicalService.class);
                List<RfcTechnicalRecord> found = svc.search(marker);
                assertThat(found).anyMatch(r -> marker.equals(r.getRfcNumber()));
            }
        } finally {
            props.keySet().forEach(System::clearProperty);
        }
    }
}
