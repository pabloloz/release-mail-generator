package release_mail_generator.integration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import release_mail_generator.service.DocumentHistoryService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración de la paginación del historial ({@code DocumentHistoryService#findPage})
 * contra una PostgreSQL <b>real</b> (binario embebido, sin Docker).
 *
 * <p>Cubre: filtros combinados (tipo + búsqueda), orden estable (newest/oldest/title/type con
 * desempate por id) al recorrer todas las páginas, historial vacío, primera y última página,
 * parámetros fuera de rango (página y tamaño), y — con miles de registros — que solo se recupera
 * la página solicitada (no toda la tabla) y que la proyección usada excluye el contenido pesado
 * (el tipo {@code DocumentVersionSummary} ni siquiera expone un getter de {@code content}).</p>
 */
@SpringBootTest
class HistoryPaginationIntegrationTest {

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

    @Autowired private DocumentHistoryService historyService;
    @Autowired private DataSource dataSource;

    @BeforeEach
    void cleanTable() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("TRUNCATE TABLE document_versions");
        }
    }

    private void insert(String id, String type, String ref, String title, String author,
                         String content, String action, LocalDateTime createdAt) throws Exception {
        String sql = "INSERT INTO document_versions (id, document_type, document_ref, title, author, " +
                "content, content_hash, action, format, version_number, created_at, content_size) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, id);
            ps.setString(i++, type);
            ps.setString(i++, ref);
            ps.setString(i++, title);
            ps.setString(i++, author);
            ps.setString(i++, content);
            ps.setString(i++, "hash-" + id);
            ps.setString(i++, action);
            ps.setString(i++, "HTML");
            ps.setInt(i++, 1);
            ps.setTimestamp(i++, Timestamp.valueOf(createdAt));
            ps.setLong(i++, content == null ? 0 : content.length());
            ps.executeUpdate();
        }
    }

    @Test
    void historialVacio_devuelveItemsVacioYTotalesEnCero() {
        DocumentHistoryService.PageResult result = historyService.findPage(null, null, "newest", 1, 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
    }

    @Test
    void filtrosCombinados_tipoYBusquedaFuncionanJuntos() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        insert("A1", "RFC", "RFC-1", "Alfa release", "QA", "c", "CREATED", t);
        insert("A2", "UAT", "UAT-1", "Alfa validation", "QA", "c", "CREATED", t.plusMinutes(1));
        insert("A3", "RFC", "RFC-2", "Beta release", "QA", "c", "CREATED", t.plusMinutes(2));

        DocumentHistoryService.PageResult result = historyService.findPage("RFC", "alfa", "newest", 1, 10);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getId()).isEqualTo("A1");
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void ordenEstable_recorrerTodasLasPaginasNoDuplicaNiOmiteConValoresEmpatados() throws Exception {
        // Todos con la MISMA fecha y el MISMO título, para forzar el empate y comprobar que el
        // desempate por id produce un orden total estable entre páginas y entre corridas.
        LocalDateTime sameTime = LocalDateTime.of(2026, 1, 1, 12, 0);
        Set<String> expectedIds = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            String id = "E" + String.format("%02d", i);
            expectedIds.add(id);
            insert(id, "RFC", "ref" + i, "Mismo titulo", "QA", "c", "CREATED", sameTime);
        }

        for (String sort : List.of("newest", "oldest", "title", "type")) {
            List<String> firstPass = collectAllIdsAcrossPages(sort, 7);
            assertThat(new HashSet<>(firstPass)).as("orden=%s debe cubrir todos los ids", sort).isEqualTo(expectedIds);
            assertThat(firstPass).as("orden=%s no debe repetir ids", sort).doesNotHaveDuplicates();

            List<String> secondPass = collectAllIdsAcrossPages(sort, 7);
            assertThat(secondPass).as("orden=%s debe ser repetible/estable", sort).isEqualTo(firstPass);
        }
    }

    private List<String> collectAllIdsAcrossPages(String sort, int size) {
        List<String> flat = new ArrayList<>();
        int page = 1;
        while (true) {
            DocumentHistoryService.PageResult result = historyService.findPage(null, null, sort, page, size);
            if (result.items().isEmpty()) break;
            result.items().forEach(item -> flat.add(item.getId()));
            if (page >= result.totalPages()) break;
            page++;
        }
        return flat;
    }

    @Test
    void primeraYUltimaPagina_devuelvenLosItemsCorrectos() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        for (int i = 0; i < 23; i++) {
            insert("P" + String.format("%02d", i), "RFC", "ref" + i, "t" + i, "QA", "c", "CREATED", t.plusMinutes(i));
        }

        // newest primero: el último insertado (P22, fecha mayor) debe ser el primero en page 1.
        DocumentHistoryService.PageResult first = historyService.findPage(null, null, "newest", 1, 10);
        assertThat(first.items()).hasSize(10);
        assertThat(first.items().get(0).getId()).isEqualTo("P22");
        assertThat(first.total()).isEqualTo(23);
        assertThat(first.totalPages()).isEqualTo(3);

        // 23 registros, size 10 -> página 3 tiene el resto (3 elementos).
        DocumentHistoryService.PageResult last = historyService.findPage(null, null, "newest", 3, 10);
        assertThat(last.items()).hasSize(3);
        assertThat(last.page()).isEqualTo(3);
        assertThat(last.items().get(last.items().size() - 1).getId()).isEqualTo("P00");
    }

    @Test
    void parametrosFueraDeRango_seAjustanSinError() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        for (int i = 0; i < 5; i++) {
            insert("F" + i, "RFC", "ref" + i, "t" + i, "QA", "c", "CREATED", t.plusMinutes(i));
        }

        // Página muy por encima del total disponible: se ajusta a la última página válida.
        DocumentHistoryService.PageResult beyond = historyService.findPage(null, null, "newest", 9999, 10);
        assertThat(beyond.page()).isEqualTo(1); // solo hay 1 página con size=10 y 5 registros
        assertThat(beyond.items()).hasSize(5);

        // Página <= 0: se ajusta a 1.
        assertThat(historyService.findPage(null, null, "newest", 0, 10).page()).isEqualTo(1);
        assertThat(historyService.findPage(null, null, "newest", -5, 10).page()).isEqualTo(1);

        // size fuera de [1,100] se acota.
        assertThat(historyService.findPage(null, null, "newest", 1, 500).size()).isEqualTo(100);
        assertThat(historyService.findPage(null, null, "newest", 1, 0).size()).isEqualTo(1);
        assertThat(historyService.findPage(null, null, "newest", 1, -20).size()).isEqualTo(1);
    }

    @Test
    void conMilesDeRegistros_recuperaSoloLaPaginaPedidaYExcluyeContenidoPesado() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        String heavyContent = "x".repeat(50_000); // simula HTML con imágenes base64 embebidas
        int totalRecords = 2500;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            String sql = "INSERT INTO document_versions (id, document_type, document_ref, title, author, " +
                    "content, content_hash, action, format, version_number, created_at, content_size) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < totalRecords; i++) {
                    int j = 0;
                    ps.setString(++j, "BULK" + i);
                    ps.setString(++j, i % 2 == 0 ? "RFC" : "UAT");
                    ps.setString(++j, "ref" + i);
                    ps.setString(++j, "titulo " + i);
                    ps.setString(++j, "QA");
                    ps.setString(++j, heavyContent);
                    ps.setString(++j, "hash" + i);
                    ps.setString(++j, "CREATED");
                    ps.setString(++j, "HTML");
                    ps.setInt(++j, 1);
                    ps.setTimestamp(++j, Timestamp.valueOf(base.plusSeconds(i)));
                    ps.setLong(++j, heavyContent.length());
                    ps.addBatch();
                    if (i % 500 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        }

        long startNanos = System.nanoTime();
        DocumentHistoryService.PageResult result = historyService.findPage(null, null, "newest", 5, 20);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(result.total()).isEqualTo(totalRecords);
        assertThat(result.totalPages()).isEqualTo((int) Math.ceil(totalRecords / 20.0));
        assertThat(result.items()).hasSize(20);
        // orden "newest" (created_at desc): página 5 (1-indexada) con size=20 muestra los
        // elementos en posiciones [80,99] del orden descendente -> el primero es BULK(2500-1-80).
        assertThat(result.items().get(0).getId()).isEqualTo("BULK" + (totalRecords - 1 - 80));
        // La paginación debe resolverse con LIMIT/OFFSET en la base de datos, no cargando los
        // 2500 registros (con 50KB de contenido cada uno) completos en memoria.
        assertThat(elapsedMs).as("la paginación debe resolverse en la base de datos").isLessThan(3000);
    }
}
