package release_mail_generator.util;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración de {@link H2ToPostgresMigrator} contra un H2 real (en memoria) y una
 * PostgreSQL <b>real</b> (binario embebido {@code io.zonky.test:embedded-postgres}, sin Docker).
 *
 * <p>Cubre: importación a destino vacío, idempotencia (segunda corrida idéntica), registros
 * adicionales en destino, conflictos de id con datos distintos (incluyendo diferencias en JSON
 * de casos de prueba), hash de origen corrupto, atomicidad ante error parcial, valores nulos /
 * Unicode / fechas, y que el H2 origen nunca se modifica.</p>
 */
class H2ToPostgresMigratorTest {

    private static final EmbeddedPostgres postgres = startEmbeddedPostgres();
    private static final String PG_URL = postgres.getJdbcUrl("postgres", "postgres");

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

    private Connection h2;
    private Connection pg;

    @BeforeAll
    static void createPostgresSchema() throws Exception {
        try (Connection c = DriverManager.getConnection(PG_URL, "postgres", "postgres")) {
            runScript(c, loadSchemaSql());
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        h2 = DriverManager.getConnection(
                "jdbc:h2:mem:migrator-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        runScript(h2, loadSchemaSql());

        pg = DriverManager.getConnection(PG_URL, "postgres", "postgres");
        try (Statement st = pg.createStatement()) {
            st.execute("TRUNCATE TABLE document_versions, rfc_technical_records");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (h2 != null) h2.close();
        if (pg != null) pg.close();
    }

    // ── Helpers de esquema / fixtures ────────────────────────────────────────

    private static String loadSchemaSql() throws IOException {
        try (InputStream is = H2ToPostgresMigratorTest.class.getResourceAsStream("/db/migration/V1__init_schema.sql")) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void runScript(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            for (String stmt : sql.split(";")) {
                String trimmed = stmt.replaceAll("--.*", "").trim();
                if (!trimmed.isEmpty()) st.execute(trimmed);
            }
        }
    }

    private static final String RFC_COLUMNS = "id, rfc_number, change_name, validation_date, tester_name, " +
            "requester, environment, change_context, main_objective, specific_objectives, modules, " +
            "stored_procedures, jobs, tables, reports, other_components, business_rules, test_cases, " +
            "related_bugs, final_result, observations, risks, recommendations, final_notes, status, " +
            "created_at, updated_at";

    private void insertRfc(Connection c, String id, String changeName, String testCasesJson,
                            LocalDateTime createdAt) throws SQLException {
        String sql = "INSERT INTO rfc_technical_records (" + RFC_COLUMNS + ") " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, id);
            ps.setString(i++, "RFC-" + id);
            ps.setString(i++, changeName);
            ps.setString(i++, "2026-01-01");
            ps.setString(i++, "QA");
            ps.setString(i++, "Pablo");
            ps.setString(i++, "PROD");
            ps.setString(i++, "contexto");
            ps.setString(i++, "objetivo");
            ps.setString(i++, "objetivos especificos");
            ps.setString(i++, "modulos");
            ps.setString(i++, "sps");
            ps.setString(i++, "jobs");
            ps.setString(i++, "tablas");
            ps.setString(i++, "reportes");
            ps.setString(i++, "otros");
            ps.setString(i++, "[]");
            ps.setString(i++, testCasesJson);
            ps.setString(i++, "[]");
            ps.setString(i++, "Cumple");
            ps.setString(i++, "obs");
            ps.setString(i++, "riesgos");
            ps.setString(i++, "recomendaciones");
            ps.setString(i++, "notas");
            ps.setString(i++, "Aprobado");
            Timestamp ts = Timestamp.valueOf(createdAt);
            ps.setTimestamp(i++, ts);
            ps.setTimestamp(i++, ts);
            ps.executeUpdate();
        }
    }

    private void insertDocVersion(Connection c, String id, String ref, String author, String content,
                                   String hash, LocalDateTime createdAt) throws SQLException {
        String sql = "INSERT INTO document_versions (id, document_type, document_ref, title, author, content, " +
                "content_hash, action, format, version_number, created_at, content_size) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, id);
            ps.setString(i++, "RFC");
            ps.setString(i++, ref);
            ps.setString(i++, "titulo");
            ps.setString(i++, author);
            ps.setString(i++, content);
            ps.setString(i++, hash);
            ps.setString(i++, "CREATED");
            ps.setString(i++, "HTML");
            ps.setInt(i++, 1);
            ps.setTimestamp(i++, Timestamp.valueOf(createdAt));
            ps.setLong(i++, content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length);
            ps.executeUpdate();
        }
    }

    private static String sha256(String input) throws Exception {
        String safe = input == null ? "" : input;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(safe.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private long count(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String readRfcChangeName(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT change_name FROM rfc_technical_records WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String readDocHash(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT content_hash FROM document_versions WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void importaADestinoVacio_insertaTodoYNoTocaElOrigen() throws Exception {
        insertRfc(h2, "R1", "Cambio uno", "[{\"name\":\"caso1\"}]", LocalDateTime.of(2026, 1, 1, 10, 0));
        insertRfc(h2, "R2", "Cambio dos", "[]", LocalDateTime.of(2026, 1, 2, 10, 0));
        insertDocVersion(h2, "D1", "R1", "QA", "contenido uno", sha256("contenido uno"),
                LocalDateTime.of(2026, 1, 1, 11, 0));

        H2ToPostgresMigrator.MigrationReport report = H2ToPostgresMigrator.migrate(h2, pg);

        assertThat(report.committed()).isTrue();
        assertThat(report.issues()).isEmpty();
        assertThat(report.rfcRecords().read()).isEqualTo(2);
        assertThat(report.rfcRecords().inserted()).isEqualTo(2);
        assertThat(report.documentVersions().inserted()).isEqualTo(1);

        assertThat(count(pg, "rfc_technical_records")).isEqualTo(2);
        assertThat(count(pg, "document_versions")).isEqualTo(1);

        // El H2 origen no debe haberse modificado.
        assertThat(count(h2, "rfc_technical_records")).isEqualTo(2);
        assertThat(readRfcChangeName(h2, "R1")).isEqualTo("Cambio uno");
    }

    @Test
    void segundaEjecucionIdentica_noDuplicaNiModificaNada() throws Exception {
        insertRfc(h2, "R1", "Cambio uno", "[]", LocalDateTime.of(2026, 1, 1, 10, 0));
        insertDocVersion(h2, "D1", "R1", "QA", "contenido", sha256("contenido"),
                LocalDateTime.of(2026, 1, 1, 11, 0));

        H2ToPostgresMigrator.MigrationReport first = H2ToPostgresMigrator.migrate(h2, pg);
        assertThat(first.committed()).isTrue();

        H2ToPostgresMigrator.MigrationReport second = H2ToPostgresMigrator.migrate(h2, pg);

        assertThat(second.committed()).isTrue();
        assertThat(second.issues()).isEmpty();
        assertThat(second.rfcRecords().inserted()).isZero();
        assertThat(second.rfcRecords().identicalExisting()).isEqualTo(1);
        assertThat(second.documentVersions().inserted()).isZero();
        assertThat(second.documentVersions().identicalExisting()).isEqualTo(1);

        assertThat(count(pg, "rfc_technical_records")).isEqualTo(1);
        assertThat(count(pg, "document_versions")).isEqualTo(1);
    }

    @Test
    void destinoConRegistrosAdicionales_losConservaSinTocarlos() throws Exception {
        insertRfc(h2, "A", "Desde H2", "[]", LocalDateTime.of(2026, 1, 1, 10, 0));
        insertRfc(pg, "EXTRA", "Solo en Postgres", "[]", LocalDateTime.of(2026, 1, 1, 10, 0));

        H2ToPostgresMigrator.MigrationReport report = H2ToPostgresMigrator.migrate(h2, pg);

        assertThat(report.committed()).isTrue();
        assertThat(report.rfcRecords().inserted()).isEqualTo(1);
        assertThat(count(pg, "rfc_technical_records")).isEqualTo(2);
        assertThat(readRfcChangeName(pg, "EXTRA")).isEqualTo("Solo en Postgres");
    }

    @Test
    void idExistenteConContenidoDiferente_abortaSinSobrescribirElDestino() throws Exception {
        insertRfc(h2, "X", "Valor del origen", "[]", LocalDateTime.of(2026, 1, 1, 10, 0));
        insertRfc(pg, "X", "Valor distinto ya en destino", "[]", LocalDateTime.of(2026, 1, 1, 10, 0));

        H2ToPostgresMigrator.MigrationReport report = H2ToPostgresMigrator.migrate(h2, pg);

        assertThat(report.committed()).isFalse();
        assertThat(report.rfcRecords().conflicts()).isEqualTo(1);
        assertThat(report.issues()).anyMatch(issue -> issue.contains("id=X") && issue.contains("change_name"));

        // El destino conserva su valor original, no se sobrescribió.
        assertThat(readRfcChangeName(pg, "X")).isEqualTo("Valor distinto ya en destino");
    }

    @Test
    void jsonConOrdenDePropiedadesDistinto_noSeConsideraConflicto() throws Exception {
        insertRfc(h2, "J1", "Igual", "[{\"name\":\"caso1\",\"result\":\"OK\"}]", LocalDateTime.of(2026, 1, 1, 10, 0));
        insertRfc(pg, "J1", "Igual", "[{\"result\":\"OK\",\"name\":\"caso1\"}]", LocalDateTime.of(2026, 1, 1, 10, 0));

        H2ToPostgresMigrator.MigrationReport report = H2ToPostgresMigrator.migrate(h2, pg);

        assertThat(report.committed()).isTrue();
        assertThat(report.rfcRecords().identicalExisting()).isEqualTo(1);
        assertThat(report.rfcRecords().conflicts()).isZero();
    }

    @Test
    void diferenciasEnCasosDePrueba_seReportanComoConflictoDeTestCases() throws Exception {
        insertRfc(h2, "J2", "Igual", "[{\"name\":\"caso1\"}]", LocalDateTime.of(2026, 1, 1, 10, 0));
        insertRfc(pg, "J2", "Igual", "[{\"name\":\"caso2\"}]", LocalDateTime.of(2026, 1, 1, 10, 0));

        H2ToPostgresMigrator.MigrationReport report = H2ToPostgresMigrator.migrate(h2, pg);

        assertThat(report.committed()).isFalse();
        assertThat(report.issues()).anyMatch(issue -> issue.contains("id=J2") && issue.contains("test_cases"));
    }

    @Test
    void hashInvalidoEnOrigen_abortaSinModificarOrigenNiDestino() throws Exception {
        insertDocVersion(h2, "D1", "R1", "QA", "contenido real", "hash-invalido-deliberado",
                LocalDateTime.of(2026, 1, 1, 11, 0));

        H2ToPostgresMigrator.MigrationReport report = H2ToPostgresMigrator.migrate(h2, pg);

        assertThat(report.committed()).isFalse();
        assertThat(report.issues()).anyMatch(issue -> issue.contains("id=D1") && issue.toLowerCase().contains("hash"));
        assertThat(count(pg, "document_versions")).isZero();

        // El origen conserva el hash corrupto tal cual estaba — no se "arregló" ni se tocó.
        assertThat(readDocHash(h2, "D1")).isEqualTo("hash-invalido-deliberado");
    }

    @Test
    void errorDespuesDeInsertarAlgunasFilas_noPersisteNingunaInsercionNueva() throws Exception {
        insertRfc(h2, "OK1", "Valido uno", "[]", LocalDateTime.of(2026, 1, 1, 10, 0));
        insertRfc(h2, "OK2", "Valido dos", "[]", LocalDateTime.of(2026, 1, 2, 10, 0));
        insertDocVersion(h2, "BAD", "R1", "QA", "contenido", "hash-corrupto",
                LocalDateTime.of(2026, 1, 1, 11, 0));

        H2ToPostgresMigrator.MigrationReport report = H2ToPostgresMigrator.migrate(h2, pg);

        assertThat(report.committed()).isFalse();
        // Las 2 filas de RFC se procesaron e insertaron dentro de la transacción antes de
        // encontrar el problema en document_versions, pero el rollback debe deshacerlas.
        assertThat(count(pg, "rfc_technical_records")).isZero();
        assertThat(count(pg, "document_versions")).isZero();
    }

    @Test
    void valoresNulosUnicodeYFechas_seMigranYSeReconocenComoIdenticosEnSegundaCorrida() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 15, 8, 30, 0).withNano(123_000_000);
        insertRfc(h2, "U1", "Versión 日本語 ñ é 中文 🎉", "[]", createdAt);
        insertDocVersion(h2, "UD1", null, null, "Contenido únîcödé 测试 ñ", sha256("Contenido únîcödé 测试 ñ"), createdAt);

        H2ToPostgresMigrator.MigrationReport first = H2ToPostgresMigrator.migrate(h2, pg);
        assertThat(first.committed()).isTrue();
        assertThat(first.rfcRecords().inserted()).isEqualTo(1);
        assertThat(first.documentVersions().inserted()).isEqualTo(1);
        assertThat(readRfcChangeName(pg, "U1")).isEqualTo("Versión 日本語 ñ é 中文 🎉");

        // Segunda corrida: debe reconocer todo como idéntico (sin falsos conflictos por
        // nulos, Unicode o precisión de fecha entre H2 y PostgreSQL).
        H2ToPostgresMigrator.MigrationReport second = H2ToPostgresMigrator.migrate(h2, pg);
        assertThat(second.committed()).isTrue();
        assertThat(second.issues()).isEmpty();
        assertThat(second.rfcRecords().identicalExisting()).isEqualTo(1);
        assertThat(second.documentVersions().identicalExisting()).isEqualTo(1);
    }

    @Test
    void alteracionSilenciosaTrasInsertar_sedetectaYRevierteTodaLaImportacion() throws Exception {
        // Simula una alteración silenciosa de datos en el destino (p. ej. un trigger de base de
        // datos preexistente, una regla de collation o un bug de esquema) usando un trigger real
        // de PostgreSQL que modifica la fila justo al insertarla. La verificación post-inserción
        // del migrador debe detectar la discrepancia al releerla y revertir TODA la importación.
        try (Statement st = pg.createStatement()) {
            st.execute("CREATE OR REPLACE FUNCTION test_mutar_change_name() RETURNS trigger AS $$ " +
                    "BEGIN NEW.change_name := NEW.change_name || ' (alterado)'; RETURN NEW; END; $$ LANGUAGE plpgsql");
            st.execute("CREATE TRIGGER trg_mutar_change_name BEFORE INSERT ON rfc_technical_records " +
                    "FOR EACH ROW EXECUTE FUNCTION test_mutar_change_name()");
        }
        try {
            insertRfc(h2, "M1", "Valor original", "[]", LocalDateTime.of(2026, 1, 1, 10, 0));

            H2ToPostgresMigrator.MigrationReport report = H2ToPostgresMigrator.migrate(h2, pg);

            assertThat(report.committed()).isFalse();
            assertThat(report.issues()).anyMatch(issue -> issue.contains("id=M1")
                    && issue.contains("no coincide") && issue.contains("change_name"));

            // Rollback real: no debe quedar ninguna fila nueva en el destino.
            assertThat(count(pg, "rfc_technical_records")).isZero();
            // El H2 origen tampoco se tocó.
            assertThat(readRfcChangeName(h2, "M1")).isEqualTo("Valor original");
        } finally {
            try (Statement st = pg.createStatement()) {
                st.execute("DROP TRIGGER IF EXISTS trg_mutar_change_name ON rfc_technical_records");
                st.execute("DROP FUNCTION IF EXISTS test_mutar_change_name()");
            }
        }
    }

    @Test
    void laSalidaNuncaExponeContenidoNiUrlsCompletasNiMensajesSqlCrudos() throws Exception {
        insertRfc(h2, "S1", "Cambio con informacion sensible", "[]", LocalDateTime.of(2026, 1, 1, 10, 0));
        insertDocVersion(h2, "S2", "S1", "QA", "contenido-muy-secreto-no-debe-imprimirse",
                sha256("contenido-muy-secreto-no-debe-imprimirse"), LocalDateTime.of(2026, 1, 1, 11, 0));
        // Conflicto deliberado para forzar también la rama de mensajes de "issues".
        insertRfc(pg, "S1", "Otro valor ya en destino", "[]", LocalDateTime.of(2026, 1, 1, 10, 0));

        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        H2ToPostgresMigrator.MigrationReport report;
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            report = H2ToPostgresMigrator.migrate(h2, pg);
        } finally {
            System.setOut(originalOut);
        }

        String output = captured.toString(StandardCharsets.UTF_8);

        assertThat(report.committed()).isFalse();
        assertThat(output).doesNotContain("contenido-muy-secreto-no-debe-imprimirse");
        assertThat(output).doesNotContain("jdbc:postgresql://");
        assertThat(output).doesNotContain("jdbc:h2:");
        // Los nombres de campo en conflicto sí pueden aparecer, pero nunca los valores.
        assertThat(output).doesNotContain("Otro valor ya en destino");
        assertThat(output).doesNotContain("Cambio con informacion sensible");
    }
}
