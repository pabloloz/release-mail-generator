package release_mail_generator.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

/**
 * Herramienta de línea de comandos para respaldar/importar los datos existentes en el H2
 * de desarrollo (archivo {@code ./data/rfcdb.mv.db}) hacia una base PostgreSQL de producción.
 *
 * <p><b>Garantías de seguridad:</b></p>
 * <ul>
 *   <li>El origen H2 se abre en modo <b>solo lectura</b> ({@code ACCESS_MODE_DATA=r}): esta
 *       herramienta nunca escribe, borra ni modifica la base original.</li>
 *   <li>La inserción en PostgreSQL usa {@code INSERT ... ON CONFLICT (id) DO NOTHING}: nunca
 *       sobrescribe filas ya existentes en el destino. Es seguro ejecutarla varias veces
 *       (idempotente).</li>
 *   <li>Al final se verifican conteos de filas y, para el historial de documentos, se
 *       recalcula el hash SHA-256 del contenido copiado y se compara contra {@code content_hash}
 *       para confirmar que el contenido llegó íntegro.</li>
 * </ul>
 *
 * <p><b>Uso</b> (tras compilar, con el driver de PostgreSQL en el classpath — por ejemplo con
 * {@code mvn dependency:build-classpath} o ejecutando dentro del proyecto empaquetado):</p>
 * <pre>
 *   java -cp target/classes;&lt;ruta-driver-postgresql.jar&gt; ^
 *        -DsourceH2Path=./data/rfcdb ^
 *        -DtargetDbUrl=jdbc:postgresql://localhost:5432/release_mail_generator ^
 *        -DtargetDbUser=postgres -DtargetDbPassword=postgres ^
 *        release_mail_generator.util.H2ToPostgresMigrator
 * </pre>
 *
 * <p>También puede configurarse con las variables de entorno {@code DB_URL}, {@code DB_USERNAME}
 * y {@code DB_PASSWORD} (las mismas que usa el perfil {@code prod}) para el destino.</p>
 */
public final class H2ToPostgresMigrator {

    private H2ToPostgresMigrator() { }

    public static void main(String[] args) throws Exception {
        String sourceH2Path = prop("sourceH2Path", "SOURCE_H2_PATH", "./data/rfcdb");
        String sourceUrl = "jdbc:h2:file:" + sourceH2Path + ";ACCESS_MODE_DATA=r;AUTO_RECONNECT=TRUE";
        String sourceUser = prop("sourceH2User", "SOURCE_H2_USER", "sa");
        String sourcePassword = prop("sourceH2Password", "SOURCE_H2_PASSWORD", "");

        String targetUrl = prop("targetDbUrl", "DB_URL", null);
        String targetUser = prop("targetDbUser", "DB_USERNAME", null);
        String targetPassword = prop("targetDbPassword", "DB_PASSWORD", null);

        if (targetUrl == null || targetUser == null || targetPassword == null) {
            System.err.println("Faltan datos de conexión al destino PostgreSQL.");
            System.err.println("Define -DtargetDbUrl / -DtargetDbUser / -DtargetDbPassword,");
            System.err.println("o las variables de entorno DB_URL / DB_USERNAME / DB_PASSWORD.");
            System.exit(2);
            return;
        }

        System.out.println("Origen (solo lectura): " + sourceUrl);
        System.out.println("Destino: " + targetUrl);
        System.out.println();

        boolean ok;
        try (Connection src = DriverManager.getConnection(sourceUrl, sourceUser, sourcePassword);
             Connection dst = DriverManager.getConnection(targetUrl, targetUser, targetPassword)) {

            src.setReadOnly(true);
            dst.setAutoCommit(false);

            Result rfcResult = migrateRfcRecords(src, dst);
            Result docResult = migrateDocumentVersions(src, dst);
            dst.commit();

            System.out.println();
            System.out.println("── Verificación post-importación ──────────────────────────");
            boolean rfcOk = verifyCount(dst, "rfc_technical_records", rfcResult);
            boolean docOk = verifyCount(dst, "document_versions", docResult);
            boolean hashOk = verifyContentHashes(dst);

            ok = rfcOk && docOk && hashOk;
        }

        System.out.println();
        System.out.println(ok ? "RESULTADO: OK — datos importados y verificados correctamente."
                               : "RESULTADO: FALLÓ — revisa los mensajes anteriores.");
        if (!ok) System.exit(1);
    }

    private record Result(long sourceCount, int inserted, int skipped) { }

    // ── RFC Técnico ──────────────────────────────────────────────────────────

    private static Result migrateRfcRecords(Connection src, Connection dst) throws SQLException {
        String selectSql = "SELECT id, rfc_number, change_name, validation_date, tester_name, requester, " +
                "environment, change_context, main_objective, specific_objectives, modules, stored_procedures, " +
                "jobs, tables, reports, other_components, business_rules, test_cases, related_bugs, final_result, " +
                "observations, risks, recommendations, final_notes, status, created_at, updated_at " +
                "FROM rfc_technical_records ORDER BY id";

        String insertSql = "INSERT INTO rfc_technical_records (id, rfc_number, change_name, validation_date, " +
                "tester_name, requester, environment, change_context, main_objective, specific_objectives, " +
                "modules, stored_procedures, jobs, tables, reports, other_components, business_rules, test_cases, " +
                "related_bugs, final_result, observations, risks, recommendations, final_notes, status, " +
                "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                "ON CONFLICT (id) DO NOTHING";

        long sourceCount = 0;
        int inserted = 0, skipped = 0;
        try (Statement countStmt = src.createStatement();
             ResultSet countRs = countStmt.executeQuery("SELECT COUNT(*) FROM rfc_technical_records")) {
            if (countRs.next()) sourceCount = countRs.getLong(1);
        }

        try (Statement selectStmt = src.createStatement();
             ResultSet rs = selectStmt.executeQuery(selectSql);
             PreparedStatement ins = dst.prepareStatement(insertSql)) {
            while (rs.next()) {
                int i = 1;
                ins.setString(i++, rs.getString("id"));
                ins.setString(i++, rs.getString("rfc_number"));
                ins.setString(i++, rs.getString("change_name"));
                ins.setString(i++, rs.getString("validation_date"));
                ins.setString(i++, rs.getString("tester_name"));
                ins.setString(i++, rs.getString("requester"));
                ins.setString(i++, rs.getString("environment"));
                ins.setString(i++, rs.getString("change_context"));
                ins.setString(i++, rs.getString("main_objective"));
                ins.setString(i++, rs.getString("specific_objectives"));
                ins.setString(i++, rs.getString("modules"));
                ins.setString(i++, rs.getString("stored_procedures"));
                ins.setString(i++, rs.getString("jobs"));
                ins.setString(i++, rs.getString("tables"));
                ins.setString(i++, rs.getString("reports"));
                ins.setString(i++, rs.getString("other_components"));
                ins.setString(i++, rs.getString("business_rules"));
                ins.setString(i++, rs.getString("test_cases"));
                ins.setString(i++, rs.getString("related_bugs"));
                ins.setString(i++, rs.getString("final_result"));
                ins.setString(i++, rs.getString("observations"));
                ins.setString(i++, rs.getString("risks"));
                ins.setString(i++, rs.getString("recommendations"));
                ins.setString(i++, rs.getString("final_notes"));
                ins.setString(i++, rs.getString("status"));
                ins.setTimestamp(i++, rs.getTimestamp("created_at"));
                ins.setTimestamp(i++, rs.getTimestamp("updated_at"));
                int rows = ins.executeUpdate();
                if (rows > 0) inserted++; else skipped++;
            }
        }
        System.out.println("rfc_technical_records: origen=" + sourceCount + " insertados=" + inserted + " ya-existian=" + skipped);
        return new Result(sourceCount, inserted, skipped);
    }

    // ── Historial de documentos ──────────────────────────────────────────────

    private static Result migrateDocumentVersions(Connection src, Connection dst) throws SQLException {
        String selectSql = "SELECT id, document_type, document_ref, title, author, content, content_hash, " +
                "action, format, version_number, created_at, content_size FROM document_versions ORDER BY id";

        String insertSql = "INSERT INTO document_versions (id, document_type, document_ref, title, author, " +
                "content, content_hash, action, format, version_number, created_at, content_size) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";

        long sourceCount = 0;
        int inserted = 0, skipped = 0;
        int hashMismatchesInSource = 0;

        try (Statement countStmt = src.createStatement();
             ResultSet countRs = countStmt.executeQuery("SELECT COUNT(*) FROM document_versions")) {
            if (countRs.next()) sourceCount = countRs.getLong(1);
        }

        try (Statement selectStmt = src.createStatement();
             ResultSet rs = selectStmt.executeQuery(selectSql);
             PreparedStatement ins = dst.prepareStatement(insertSql)) {
            while (rs.next()) {
                String content = rs.getString("content");
                String storedHash = rs.getString("content_hash");
                String recomputed = sha256(content);
                if (storedHash != null && !storedHash.equals(recomputed)) {
                    hashMismatchesInSource++;
                    System.out.println("  AVISO: hash no coincide en origen para id=" + rs.getString("id")
                            + " (dato preexistente, no generado por esta migración)");
                }

                int i = 1;
                ins.setString(i++, rs.getString("id"));
                ins.setString(i++, rs.getString("document_type"));
                ins.setString(i++, rs.getString("document_ref"));
                ins.setString(i++, rs.getString("title"));
                ins.setString(i++, rs.getString("author"));
                ins.setString(i++, content);
                ins.setString(i++, storedHash);
                ins.setString(i++, rs.getString("action"));
                ins.setString(i++, rs.getString("format"));
                ins.setInt(i++, rs.getInt("version_number"));
                Timestamp createdAt = rs.getTimestamp("created_at");
                ins.setTimestamp(i++, createdAt);
                ins.setLong(i++, rs.getLong("content_size"));
                int rows = ins.executeUpdate();
                if (rows > 0) inserted++; else skipped++;
            }
        }
        System.out.println("document_versions: origen=" + sourceCount + " insertados=" + inserted + " ya-existian=" + skipped
                + (hashMismatchesInSource > 0 ? " (avisos de hash en origen=" + hashMismatchesInSource + ")" : ""));
        return new Result(sourceCount, inserted, skipped);
    }

    // ── Verificación ─────────────────────────────────────────────────────────

    private static boolean verifyCount(Connection dst, String table, Result expected) throws SQLException {
        long targetCount;
        try (Statement st = dst.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            targetCount = rs.getLong(1);
        }
        // El destino puede tener más filas que "insertados+ya-existian" de ESTA corrida si ya
        // tenía datos propios; lo que debe cumplirse siempre es: todo lo del origen debe estar
        // presente en el destino (insertados + ya-existian == origen).
        boolean ok = (expected.inserted() + expected.skipped()) == expected.sourceCount();
        System.out.println(table + ": filas en destino tras importar=" + targetCount
                + " (origen cubierto=" + (expected.inserted() + expected.skipped()) + "/" + expected.sourceCount() + ") "
                + (ok ? "OK" : "FALLÓ"));
        return ok;
    }

    /** Recalcula el SHA-256 de cada `content` en el DESTINO y lo compara contra `content_hash`. */
    private static boolean verifyContentHashes(Connection dst) throws SQLException {
        int checked = 0, mismatches = 0;
        try (Statement st = dst.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, content, content_hash FROM document_versions")) {
            while (rs.next()) {
                String content = rs.getString("content");
                String storedHash = rs.getString("content_hash");
                if (storedHash == null) continue;
                checked++;
                if (!storedHash.equals(sha256(content))) {
                    mismatches++;
                    System.out.println("  MISMATCH: content_hash no coincide en destino para id=" + rs.getString("id"));
                }
            }
        }
        System.out.println("document_versions: hashes verificados en destino=" + checked + " mismatches=" + mismatches
                + (mismatches == 0 ? " OK" : " FALLÓ"));
        return mismatches == 0;
    }

    private static String sha256(String input) {
        if (input == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String prop(String sysPropName, String envName, String defaultValue) {
        String v = System.getProperty(sysPropName);
        if (v == null || v.isBlank()) v = System.getenv(envName);
        if (v == null || v.isBlank()) v = defaultValue;
        return v;
    }
}
