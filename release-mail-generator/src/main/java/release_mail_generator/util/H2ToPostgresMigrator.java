package release_mail_generator.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Herramienta de línea de comandos para <b>importar</b> los datos existentes en el H2 de
 * desarrollo (archivo {@code ./data/rfcdb.mv.db}) hacia una base PostgreSQL de producción.
 *
 * <p><b>Esto es una herramienta de importación, no un respaldo.</b> No reemplaza hacer un
 * respaldo previo de ambas bases (ver README.md → "Persistencia en producción (PostgreSQL)")
 * antes de ejecutarla.</p>
 *
 * <p><b>Garantías de seguridad:</b></p>
 * <ul>
 *   <li>El origen H2 se abre en modo <b>solo lectura</b> ({@code ACCESS_MODE_DATA=r} en la URL,
 *       más {@link Connection#setReadOnly(boolean)} a nivel JDBC): esta herramienta nunca
 *       escribe, borra ni modifica la base original, con éxito o con error.</li>
 *   <li>Toda la importación corre dentro de <b>una única transacción</b> en el destino. Solo se
 *       hace {@code commit} si absolutamente todas las filas del origen quedaron insertadas o
 *       verificadas como idénticas a lo ya existente. Ante cualquier conflicto, hash inválido o
 *       error, se hace {@code rollback} explícito — nunca queda una importación a medias.</li>
 *   <li>Nunca se usa {@code UPDATE} ni {@code DELETE} sobre el destino: solo {@code INSERT} de
 *       filas cuyo id no existe aún. Si el id ya existe, se compara campo por campo contra el
 *       origen; si son iguales se cuenta como verificado, si difieren se aborta la importación
 *       completa sin tocar esa fila.</li>
 *   <li>Antes de insertar cada versión de documento se recalcula su hash SHA-256 (mismas reglas
 *       que {@code DocumentHistoryService}) y se compara contra {@code content_hash} guardado en
 *       el origen; si no corresponde, se aborta toda la importación sin modificar el origen.</li>
 *   <li>Después de insertar cada fila, se <b>relee desde el destino y se compara de nuevo</b>
 *       contra el origen, antes de hacer commit — esto detecta cualquier alteración silenciosa
 *       que el motor pudiera introducir al guardar (truncamiento, triggers, collation, etc.).
 *       Cualquier diferencia aborta toda la importación.</li>
 *   <li>Los errores SQL se reportan con una clasificación segura (clase de excepción + SQLState)
 *       y nunca con el mensaje crudo del driver, que en varios motores puede incluir los valores
 *       de datos de la fila involucrada (p. ej. el {@code DETAIL} de PostgreSQL ante violaciones
 *       de restricción). Tampoco se imprime nunca la URL JDBC completa, solo el esquema.</li>
 * </ul>
 *
 * <p><b>Importante:</b> para que la comparación origen/destino sea consistente, el origen H2 y
 * el destino PostgreSQL no deben recibir escrituras mientras corre esta herramienta (deténlas
 * antes de ejecutarla).</p>
 *
 * <p><b>Uso</b> (tras compilar, con el driver de PostgreSQL en el classpath):</p>
 * <pre>
 *   # Las contraseñas SIEMPRE por variable de entorno, nunca como argumento -D.
 *   $env:DB_USERNAME = "postgres"
 *   $env:DB_PASSWORD = "tu-password"
 *
 *   java -cp "target/classes;&lt;ruta-driver-postgresql.jar&gt;" ^
 *        -DsourceH2Path=./data/rfcdb ^
 *        -DtargetDbUrl=jdbc:postgresql://localhost:5432/release_mail_generator ^
 *        release_mail_generator.util.H2ToPostgresMigrator
 * </pre>
 *
 * <p>El destino también puede configurarse enteramente por variables de entorno ({@code DB_URL},
 * {@code DB_USERNAME}, {@code DB_PASSWORD} — las mismas que usa el perfil {@code prod}).</p>
 */
public final class H2ToPostgresMigrator {

    private static final ObjectMapper JSON = new ObjectMapper();

    private H2ToPostgresMigrator() { }

    // ── Resultado ────────────────────────────────────────────────────────────

    /** Estadísticas de una tabla migrada. {@code verified} = {@code inserted + identicalExisting}. */
    public record TableStats(long read, int inserted, int identicalExisting, int conflicts, int verified) {
        static TableStats of(long read, int inserted, int identicalExisting, int conflicts) {
            return new TableStats(read, inserted, identicalExisting, conflicts, inserted + identicalExisting);
        }
    }

    /**
     * Resultado completo de una corrida de migración.
     *
     * @param committed {@code true} si se hizo commit (todo consistente); {@code false} si se
     *                  hizo rollback (no se modificó el destino en absoluto).
     * @param issues    descripciones legibles de los problemas encontrados (nunca incluyen
     *                  contenido de documentos, JSON completo ni datos sensibles — solo
     *                  tabla + id + nombres de campo).
     */
    public record MigrationReport(TableStats rfcRecords, TableStats documentVersions,
                                   List<String> issues, boolean committed) { }

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        String sourceH2Path = prop("sourceH2Path", "SOURCE_H2_PATH", "./data/rfcdb");
        String sourceUrl = "jdbc:h2:file:" + sourceH2Path + ";ACCESS_MODE_DATA=r;AUTO_RECONNECT=TRUE";
        String sourceUser = prop("sourceH2User", "SOURCE_H2_USER", "sa");
        String sourcePassword = envOnly("SOURCE_H2_PASSWORD", "");

        String targetUrl = prop("targetDbUrl", "DB_URL", null);
        String targetUser = prop("targetDbUser", "DB_USERNAME", null);
        String targetPassword = envOnly("DB_PASSWORD", null);

        if (targetUrl == null || targetUser == null || targetPassword == null) {
            System.err.println("Faltan datos de conexión al destino PostgreSQL.");
            System.err.println("Define -DtargetDbUrl / -DtargetDbUser (no sensibles) y, SIEMPRE por variable");
            System.err.println("de entorno (nunca como argumento -D), DB_USERNAME / DB_PASSWORD.");
            System.exit(2);
            return;
        }

        System.out.println("ADVERTENCIA: detén las escrituras en el origen (H2) y en el destino");
        System.out.println("(PostgreSQL) mientras corre esta herramienta. Una comparación consistente");
        System.out.println("requiere que ninguno de los dos lados cambie durante la importación.");
        System.out.println("Esta herramienta IMPORTA datos; no sustituye un respaldo previo.");
        System.out.println();
        System.out.println("Origen (solo lectura): " + redactJdbcUrl(sourceUrl));
        System.out.println("Destino: " + redactJdbcUrl(targetUrl));
        System.out.println();

        MigrationReport report;
        try (Connection src = DriverManager.getConnection(sourceUrl, sourceUser, sourcePassword);
             Connection dst = DriverManager.getConnection(targetUrl, targetUser, targetPassword)) {
            report = migrate(src, dst);
        }

        printReport(report);
        if (!report.committed()) {
            System.exit(1);
        }
    }

    /**
     * Nunca imprime la URL JDBC completa (host, puerto, nombre de base, credenciales): solo el
     * esquema (p. ej. {@code jdbc:postgresql://***}), suficiente para confirmar a qué motor se
     * está conectando sin exponer datos de conexión.
     */
    private static String redactJdbcUrl(String url) {
        if (url == null || url.isBlank()) return "(no configurado)";
        int schemeEnd = url.indexOf("://");
        String scheme = schemeEnd > 0 ? url.substring(0, schemeEnd) : url.split(":")[0];
        return scheme + "://***";
    }

    // ── Migración (núcleo testeable, nunca llama a System.exit) ─────────────

    /**
     * Ejecuta la migración completa dentro de <b>una única transacción</b> en {@code dst}:
     * compara cada fila del origen contra el destino por id, inserta las que faltan, detecta
     * conflictos (mismo id con datos distintos) y valida los hashes de contenido del origen.
     * Si todo resulta consistente, hace commit; ante cualquier problema, hace rollback
     * explícito — el destino queda exactamente como estaba antes de llamar a este método.
     *
     * <p>El origen nunca se modifica (solo hace SELECTs, y se marca {@link Connection#setReadOnly
     * read-only}). El destino nunca sufre {@code UPDATE} ni {@code DELETE} — solo {@code INSERT}
     * de filas nuevas, y únicamente si se confirma el commit.</p>
     */
    public static MigrationReport migrate(Connection src, Connection dst) throws SQLException {
        src.setReadOnly(true);
        boolean originalAutoCommit = dst.getAutoCommit();
        dst.setAutoCommit(false);

        List<String> issues = new ArrayList<>();
        TableStats rfcStats = TableStats.of(0, 0, 0, 0);
        TableStats docStats = TableStats.of(0, 0, 0, 0);
        try {
            rfcStats = migrateRfcRecords(src, dst, issues);
            docStats = migrateDocumentVersions(src, dst, issues);

            boolean ok = issues.isEmpty();
            if (ok) {
                dst.commit();
            } else {
                dst.rollback();
            }
            return new MigrationReport(rfcStats, docStats, List.copyOf(issues), ok);
        } catch (SQLException e) {
            // Nunca se incluye e.getMessage(): los mensajes crudos de JDBC/PostgreSQL pueden
            // embeber valores de datos (p. ej. el DETAIL de una violación de restricción incluye
            // la fila completa). Solo se reporta una clasificación segura + SQLState.
            issues.add("Error SQL durante la migración (" + safeErrorCode(e) + ") — destino revertido, "
                    + "no se modificó nada. Revisa los logs del servidor de base de datos para más detalle.");
            try {
                dst.rollback();
            } catch (SQLException rollbackFailure) {
                issues.add("Además, el rollback falló (" + safeErrorCode(rollbackFailure) + ").");
            }
            return new MigrationReport(rfcStats, docStats, List.copyOf(issues), false);
        } finally {
            dst.setAutoCommit(originalAutoCommit);
        }
    }

    // ── RFC Técnico ──────────────────────────────────────────────────────────

    private static TableStats migrateRfcRecords(Connection src, Connection dst, List<String> issues) throws SQLException {
        String selectSrc = "SELECT id, rfc_number, change_name, validation_date, tester_name, requester, " +
                "environment, change_context, main_objective, specific_objectives, modules, stored_procedures, " +
                "jobs, tables, reports, other_components, business_rules, test_cases, related_bugs, final_result, " +
                "observations, risks, recommendations, final_notes, status, created_at, updated_at " +
                "FROM rfc_technical_records ORDER BY id";

        String selectDstById = "SELECT rfc_number, change_name, validation_date, tester_name, requester, " +
                "environment, change_context, main_objective, specific_objectives, modules, stored_procedures, " +
                "jobs, tables, reports, other_components, business_rules, test_cases, related_bugs, final_result, " +
                "observations, risks, recommendations, final_notes, status, created_at, updated_at " +
                "FROM rfc_technical_records WHERE id = ?";

        String insertSql = "INSERT INTO rfc_technical_records (id, rfc_number, change_name, validation_date, " +
                "tester_name, requester, environment, change_context, main_objective, specific_objectives, " +
                "modules, stored_procedures, jobs, tables, reports, other_components, business_rules, test_cases, " +
                "related_bugs, final_result, observations, risks, recommendations, final_notes, status, " +
                "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        long read = 0;
        int inserted = 0, identical = 0, conflicts = 0;

        try (Statement srcStmt = src.createStatement();
             ResultSet rs = srcStmt.executeQuery(selectSrc);
             PreparedStatement findDst = dst.prepareStatement(selectDstById);
             PreparedStatement ins = dst.prepareStatement(insertSql)) {

            while (rs.next()) {
                read++;
                String id = rs.getString("id");

                findDst.setString(1, id);
                boolean exists;
                List<String> diffs = null;
                try (ResultSet existing = findDst.executeQuery()) {
                    exists = existing.next();
                    if (exists) {
                        diffs = compareRfcRecord(rs, existing);
                    }
                }

                if (exists) {
                    if (diffs.isEmpty()) {
                        identical++;
                    } else {
                        conflicts++;
                        issues.add("rfc_technical_records id=" + id + ": ya existe en el destino con datos "
                                + "distintos en los campos " + diffs + " — importación abortada, destino no modificado.");
                    }
                    continue;
                }

                int i = 1;
                ins.setString(i++, id);
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
                ins.executeUpdate();
                inserted++;

                // Verificación post-inserción: relee la fila recién insertada y la compara contra
                // el origen ANTES del commit, para detectar cualquier alteración silenciosa (p. ej.
                // truncamiento, triggers, collation) que el motor pudiera introducir al guardarla.
                findDst.setString(1, id);
                try (ResultSet verifyRs = findDst.executeQuery()) {
                    if (!verifyRs.next()) {
                        issues.add("rfc_technical_records id=" + id + ": no se encontró al releerla tras "
                                + "insertarla — importación abortada.");
                    } else {
                        List<String> postDiffs = compareRfcRecord(rs, verifyRs);
                        if (!postDiffs.isEmpty()) {
                            issues.add("rfc_technical_records id=" + id + ": la fila insertada no coincide con "
                                    + "el origen al releerla (campos: " + postDiffs + ") — importación abortada.");
                        }
                    }
                }
            }
        }

        TableStats stats = TableStats.of(read, inserted, identical, conflicts);
        System.out.println("rfc_technical_records: leidos=" + stats.read() + " insertados=" + stats.inserted()
                + " existentes-identicos=" + stats.identicalExisting() + " conflictos=" + stats.conflicts()
                + " verificados=" + stats.verified());
        return stats;
    }

    private static List<String> compareRfcRecord(ResultSet srcRow, ResultSet dstRow) throws SQLException {
        List<String> diffs = new ArrayList<>();
        compareString("rfc_number", srcRow.getString("rfc_number"), dstRow.getString("rfc_number"), diffs);
        compareString("change_name", srcRow.getString("change_name"), dstRow.getString("change_name"), diffs);
        compareString("validation_date", srcRow.getString("validation_date"), dstRow.getString("validation_date"), diffs);
        compareString("tester_name", srcRow.getString("tester_name"), dstRow.getString("tester_name"), diffs);
        compareString("requester", srcRow.getString("requester"), dstRow.getString("requester"), diffs);
        compareString("environment", srcRow.getString("environment"), dstRow.getString("environment"), diffs);
        compareString("change_context", srcRow.getString("change_context"), dstRow.getString("change_context"), diffs);
        compareString("main_objective", srcRow.getString("main_objective"), dstRow.getString("main_objective"), diffs);
        compareString("specific_objectives", srcRow.getString("specific_objectives"), dstRow.getString("specific_objectives"), diffs);
        compareString("modules", srcRow.getString("modules"), dstRow.getString("modules"), diffs);
        compareString("stored_procedures", srcRow.getString("stored_procedures"), dstRow.getString("stored_procedures"), diffs);
        compareString("jobs", srcRow.getString("jobs"), dstRow.getString("jobs"), diffs);
        compareString("tables", srcRow.getString("tables"), dstRow.getString("tables"), diffs);
        compareString("reports", srcRow.getString("reports"), dstRow.getString("reports"), diffs);
        compareString("other_components", srcRow.getString("other_components"), dstRow.getString("other_components"), diffs);
        compareJsonField("business_rules", srcRow.getString("business_rules"), dstRow.getString("business_rules"), diffs);
        compareJsonField("test_cases", srcRow.getString("test_cases"), dstRow.getString("test_cases"), diffs);
        compareJsonField("related_bugs", srcRow.getString("related_bugs"), dstRow.getString("related_bugs"), diffs);
        compareString("final_result", srcRow.getString("final_result"), dstRow.getString("final_result"), diffs);
        compareString("observations", srcRow.getString("observations"), dstRow.getString("observations"), diffs);
        compareString("risks", srcRow.getString("risks"), dstRow.getString("risks"), diffs);
        compareString("recommendations", srcRow.getString("recommendations"), dstRow.getString("recommendations"), diffs);
        compareString("final_notes", srcRow.getString("final_notes"), dstRow.getString("final_notes"), diffs);
        compareString("status", srcRow.getString("status"), dstRow.getString("status"), diffs);
        compareTimestamp("created_at", srcRow.getTimestamp("created_at"), dstRow.getTimestamp("created_at"), diffs);
        compareTimestamp("updated_at", srcRow.getTimestamp("updated_at"), dstRow.getTimestamp("updated_at"), diffs);
        return diffs;
    }

    // ── Historial de documentos ──────────────────────────────────────────────

    private static TableStats migrateDocumentVersions(Connection src, Connection dst, List<String> issues) throws SQLException {
        String selectSrc = "SELECT id, document_type, document_ref, title, author, content, content_hash, " +
                "action, format, version_number, created_at, content_size FROM document_versions ORDER BY id";

        String selectDstById = "SELECT document_type, document_ref, title, author, content, content_hash, " +
                "action, format, version_number, created_at, content_size FROM document_versions WHERE id = ?";

        String insertSql = "INSERT INTO document_versions (id, document_type, document_ref, title, author, " +
                "content, content_hash, action, format, version_number, created_at, content_size) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

        long read = 0;
        int inserted = 0, identical = 0, conflicts = 0;

        try (Statement srcStmt = src.createStatement();
             ResultSet rs = srcStmt.executeQuery(selectSrc);
             PreparedStatement findDst = dst.prepareStatement(selectDstById);
             PreparedStatement ins = dst.prepareStatement(insertSql)) {

            while (rs.next()) {
                read++;
                String id = rs.getString("id");
                String content = rs.getString("content");
                String storedHash = rs.getString("content_hash");

                // Validación de integridad del ORIGEN: siempre se hace, exista o no ya en destino.
                if (storedHash != null && !storedHash.isBlank() && !storedHash.equals(sha256(content))) {
                    issues.add("document_versions id=" + id + ": el hash de contenido en el ORIGEN no corresponde "
                            + "a su contenido según las reglas de DocumentHistoryService (posible corrupción de "
                            + "datos) — importación abortada, origen no modificado.");
                    continue;
                }

                findDst.setString(1, id);
                boolean exists;
                List<String> diffs = null;
                try (ResultSet existing = findDst.executeQuery()) {
                    exists = existing.next();
                    if (exists) {
                        diffs = compareDocumentVersion(rs, existing);
                    }
                }

                if (exists) {
                    if (diffs.isEmpty()) {
                        identical++;
                    } else {
                        conflicts++;
                        issues.add("document_versions id=" + id + ": ya existe en el destino con datos distintos "
                                + "en los campos " + diffs + " — importación abortada, destino no modificado.");
                    }
                    continue;
                }

                int i = 1;
                ins.setString(i++, id);
                ins.setString(i++, rs.getString("document_type"));
                ins.setString(i++, rs.getString("document_ref"));
                ins.setString(i++, rs.getString("title"));
                ins.setString(i++, rs.getString("author"));
                ins.setString(i++, content);
                ins.setString(i++, storedHash);
                ins.setString(i++, rs.getString("action"));
                ins.setString(i++, rs.getString("format"));
                ins.setInt(i++, rs.getInt("version_number"));
                ins.setTimestamp(i++, rs.getTimestamp("created_at"));
                ins.setLong(i++, rs.getLong("content_size"));
                ins.executeUpdate();
                inserted++;

                // Verificación post-inserción: relee la fila recién insertada y la compara contra
                // el origen ANTES del commit (misma razón que en rfc_technical_records).
                findDst.setString(1, id);
                try (ResultSet verifyRs = findDst.executeQuery()) {
                    if (!verifyRs.next()) {
                        issues.add("document_versions id=" + id + ": no se encontró al releerla tras insertarla "
                                + "— importación abortada.");
                    } else {
                        List<String> postDiffs = compareDocumentVersion(rs, verifyRs);
                        if (!postDiffs.isEmpty()) {
                            issues.add("document_versions id=" + id + ": la fila insertada no coincide con el "
                                    + "origen al releerla (campos: " + postDiffs + ") — importación abortada.");
                        }
                    }
                }
            }
        }

        TableStats stats = TableStats.of(read, inserted, identical, conflicts);
        System.out.println("document_versions: leidos=" + stats.read() + " insertados=" + stats.inserted()
                + " existentes-identicos=" + stats.identicalExisting() + " conflictos=" + stats.conflicts()
                + " verificados=" + stats.verified());
        return stats;
    }

    private static List<String> compareDocumentVersion(ResultSet srcRow, ResultSet dstRow) throws SQLException {
        List<String> diffs = new ArrayList<>();
        compareString("document_type", srcRow.getString("document_type"), dstRow.getString("document_type"), diffs);
        compareString("document_ref", srcRow.getString("document_ref"), dstRow.getString("document_ref"), diffs);
        compareString("title", srcRow.getString("title"), dstRow.getString("title"), diffs);
        compareString("author", srcRow.getString("author"), dstRow.getString("author"), diffs);
        compareString("content", srcRow.getString("content"), dstRow.getString("content"), diffs);
        compareString("content_hash", srcRow.getString("content_hash"), dstRow.getString("content_hash"), diffs);
        compareString("action", srcRow.getString("action"), dstRow.getString("action"), diffs);
        compareString("format", srcRow.getString("format"), dstRow.getString("format"), diffs);
        if (srcRow.getInt("version_number") != dstRow.getInt("version_number")) diffs.add("version_number");
        compareTimestamp("created_at", srcRow.getTimestamp("created_at"), dstRow.getTimestamp("created_at"), diffs);
        if (srcRow.getLong("content_size") != dstRow.getLong("content_size")) diffs.add("content_size");
        return diffs;
    }

    // ── Comparadores de campo (nunca exponen valores, solo detectan diferencias) ────────────

    private static void compareString(String field, String a, String b, List<String> diffs) {
        if (!Objects.equals(a, b)) diffs.add(field);
    }

    /**
     * Compara dos columnas JSON por estructura y valores, ignorando el orden de las propiedades
     * dentro de cada objeto, pero respetando el orden de los elementos dentro de los arreglos
     * (igual que {@code List.equals}). Un valor nulo o en blanco se normaliza a {@code "[]"},
     * igual que hacen los AttributeConverter de la entidad (BusinessRuleListConverter, etc.).
     */
    private static void compareJsonField(String field, String a, String b, List<String> diffs) {
        String na = (a == null || a.isBlank()) ? "[]" : a;
        String nb = (b == null || b.isBlank()) ? "[]" : b;
        try {
            JsonNode ja = JSON.readTree(na);
            JsonNode jb = JSON.readTree(nb);
            if (!ja.equals(jb)) diffs.add(field);
        } catch (Exception e) {
            // JSON inválido en alguno de los dos lados: compara como texto plano.
            if (!Objects.equals(a, b)) diffs.add(field);
        }
    }

    /**
     * Compara dos timestamps truncando ambos a precisión de milisegundos, para tolerar
     * diferencias de precisión soportadas entre H2 y PostgreSQL sin generar falsos conflictos.
     */
    private static void compareTimestamp(String field, Timestamp a, Timestamp b, List<String> diffs) {
        LocalDateTime la = a == null ? null : a.toLocalDateTime().truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime lb = b == null ? null : b.toLocalDateTime().truncatedTo(ChronoUnit.MILLIS);
        if (!Objects.equals(la, lb)) diffs.add(field);
    }

    /**
     * Clasificación segura de una {@link SQLException} para reportar en la salida: solo el
     * nombre de la clase de excepción y el código SQLState estándar (p. ej. {@code 23505} =
     * violación de unicidad). Nunca incluye {@code e.getMessage()}, que en varios motores
     * (incluido PostgreSQL, en el {@code DETAIL} de violaciones de restricción) puede contener
     * los valores de datos de la fila involucrada.
     */
    private static String safeErrorCode(SQLException e) {
        String sqlState = e.getSQLState() != null ? e.getSQLState() : "desconocido";
        return e.getClass().getSimpleName() + ", SQLState=" + sqlState;
    }

    /** Mismo algoritmo que {@code DocumentHistoryService.sha256}: SHA-256 en hex, null tratado como "". */
    private static String sha256(String input) {
        String safe = input == null ? "" : input;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(safe.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(safe.hashCode());
        }
    }

    // ── Reporte ──────────────────────────────────────────────────────────────

    private static void printReport(MigrationReport report) {
        System.out.println();
        System.out.println("── Resumen ──────────────────────────────────────────────────");
        printTableStats("rfc_technical_records", report.rfcRecords());
        printTableStats("document_versions", report.documentVersions());

        if (!report.issues().isEmpty()) {
            System.out.println();
            System.out.println("Problemas encontrados:");
            for (String issue : report.issues()) {
                System.out.println("  - " + issue);
            }
        }

        System.out.println();
        System.out.println(report.committed()
                ? "RESULTADO: IMPORTACIÓN CONFIRMADA (commit) — todo el origen quedó consistente en el destino."
                : "RESULTADO: IMPORTACIÓN REVERTIDA (rollback) — el destino NO se modificó. Revisa los problemas anteriores.");
    }

    private static void printTableStats(String table, TableStats stats) {
        System.out.println(table + ": leidos=" + stats.read() + " insertados=" + stats.inserted()
                + " existentes-identicos=" + stats.identicalExisting() + " conflictos=" + stats.conflicts()
                + " verificados=" + stats.verified());
    }

    // ── Configuración ────────────────────────────────────────────────────────

    /** Para valores no sensibles: system property primero, luego variable de entorno. */
    private static String prop(String sysPropName, String envName, String defaultValue) {
        String v = System.getProperty(sysPropName);
        if (v == null || v.isBlank()) v = System.getenv(envName);
        if (v == null || v.isBlank()) v = defaultValue;
        return v;
    }

    /**
     * Para contraseñas: SOLO variable de entorno (nunca system property), para evitar que
     * queden visibles en el historial de la shell o en listados de procesos (`ps`/Task Manager).
     */
    private static String envOnly(String envName, String defaultValue) {
        String v = System.getenv(envName);
        if (v == null || v.isBlank()) v = defaultValue;
        return v;
    }
}
