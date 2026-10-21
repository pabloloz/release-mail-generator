package release_mail_generator.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import release_mail_generator.model.DocumentVersion;

import java.time.LocalDateTime;
import java.util.List;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, String> {

    /**
     * Proyección ligera: excluye el campo `content` (CLOB, HTML con imágenes
     * base64 embebidas) que no se necesita para listados/paginación/dashboard.
     */
    interface DocumentVersionSummary {
        String getId();
        String getDocumentType();
        String getDocumentRef();
        String getTitle();
        String getAuthor();
        String getAction();
        String getFormat();
        int getVersionNumber();
        java.time.LocalDateTime getCreatedAt();
        long getContentSize();
        String getContentHash();
    }

    @Query("SELECT d.id as id, d.documentType as documentType, d.documentRef as documentRef, " +
           "d.title as title, d.author as author, d.action as action, d.format as format, " +
           "d.versionNumber as versionNumber, d.createdAt as createdAt, " +
           "d.contentSize as contentSize, d.contentHash as contentHash FROM DocumentVersion d " +
           "ORDER BY d.createdAt DESC")
    List<DocumentVersionSummary> findSummaryAllByOrderByCreatedAtDesc();

    @Query("SELECT d.id as id, d.documentType as documentType, d.documentRef as documentRef, " +
           "d.title as title, d.author as author, d.action as action, d.format as format, " +
           "d.versionNumber as versionNumber, d.createdAt as createdAt, " +
           "d.contentSize as contentSize, d.contentHash as contentHash FROM DocumentVersion d " +
           "WHERE d.documentType = :type ORDER BY d.createdAt DESC")
    List<DocumentVersionSummary> findSummaryByDocumentType(@Param("type") String documentType);

    @Query("SELECT d.id as id, d.documentType as documentType, d.documentRef as documentRef, " +
           "d.title as title, d.author as author, d.action as action, d.format as format, " +
           "d.versionNumber as versionNumber, d.createdAt as createdAt, " +
           "d.contentSize as contentSize, d.contentHash as contentHash FROM DocumentVersion d WHERE " +
           "LOWER(d.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(d.documentRef) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(d.author) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY d.createdAt DESC")
    List<DocumentVersionSummary> searchSummary(@Param("q") String query);

    @Query("SELECT d.id as id, d.documentType as documentType, d.documentRef as documentRef, " +
           "d.title as title, d.author as author, d.action as action, d.format as format, " +
           "d.versionNumber as versionNumber, d.createdAt as createdAt, " +
           "d.contentSize as contentSize, d.contentHash as contentHash FROM DocumentVersion d " +
           "ORDER BY d.createdAt DESC")
    List<DocumentVersionSummary> findTop10Summary(Pageable pageable);

    /**
     * Listado paginado del historial a nivel de base de datos, con filtro opcional por tipo
     * de documento y búsqueda opcional (combinables entre sí). El orden lo define el {@link
     * Pageable#getSort()} recibido (ver {@code DocumentHistoryService.buildSort}); esta consulta
     * no incluye su propio {@code ORDER BY} a propósito, para que Spring Data lo añada a partir
     * del {@code Sort} del {@code Pageable} (incluyendo soporte para orden insensible a
     * mayúsculas vía {@code Sort.Order.ignoreCase()}).
     *
     * <p>Nunca selecciona {@code content} (el HTML/CLOB con imágenes base64 embebidas) — es una
     * proyección ligera, igual que el resto de métodos "Summary" de este repositorio.</p>
     */
    @Query(value = "SELECT d.id as id, d.documentType as documentType, d.documentRef as documentRef, " +
            "d.title as title, d.author as author, d.action as action, d.format as format, " +
            "d.versionNumber as versionNumber, d.createdAt as createdAt, " +
            "d.contentSize as contentSize, d.contentHash as contentHash FROM DocumentVersion d WHERE " +
            "(:type IS NULL OR d.documentType = :type) AND " +
            "(:q IS NULL OR LOWER(d.title) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
            "LOWER(d.documentRef) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
            "LOWER(d.author) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))",
           countQuery = "SELECT COUNT(d) FROM DocumentVersion d WHERE " +
            "(:type IS NULL OR d.documentType = :type) AND " +
            "(:q IS NULL OR LOWER(d.title) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
            "LOWER(d.documentRef) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
            "LOWER(d.author) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))")
    Page<DocumentVersionSummary> findPage(@Param("type") String type, @Param("q") String q, Pageable pageable);

    /**
     * Versiones de un documento específico, en proyección ligera (sin {@code content}). Usada
     * por el listado público de versiones; el historial completo con contenido solo se carga
     * para una versión puntual vía {@code findById}.
     */
    @Query("SELECT d.id as id, d.documentType as documentType, d.documentRef as documentRef, " +
           "d.title as title, d.author as author, d.action as action, d.format as format, " +
           "d.versionNumber as versionNumber, d.createdAt as createdAt, " +
           "d.contentSize as contentSize, d.contentHash as contentHash FROM DocumentVersion d " +
           "WHERE d.documentType = :type AND d.documentRef = :ref ORDER BY d.versionNumber DESC")
    List<DocumentVersionSummary> findVersionsSummary(@Param("type") String type, @Param("ref") String ref);

    List<DocumentVersion> findAllByOrderByCreatedAtDesc();

    List<DocumentVersion> findByDocumentTypeOrderByCreatedAtDesc(String documentType);

    @Query("SELECT d FROM DocumentVersion d WHERE d.documentType = :type AND d.documentRef = :ref ORDER BY d.versionNumber DESC")
    List<DocumentVersion> findVersions(@Param("type") String type, @Param("ref") String ref);

    @Query("SELECT COALESCE(MAX(d.versionNumber), 0) FROM DocumentVersion d WHERE d.documentType = :type AND d.documentRef = :ref")
    int findMaxVersion(@Param("type") String type, @Param("ref") String ref);

    @Query("SELECT d FROM DocumentVersion d WHERE " +
           "LOWER(d.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(d.documentRef) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(d.author) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY d.createdAt DESC")
    List<DocumentVersion> search(@Param("q") String query);

    long countByDocumentType(String documentType);

    long countByFormat(String format);

    long countByAction(String action);

    List<DocumentVersion> findTop10ByOrderByCreatedAtDesc();

    @Query("SELECT d.documentType, COUNT(d) FROM DocumentVersion d WHERE d.action <> 'EXPORTED' GROUP BY d.documentType")
    List<Object[]> countByTypeGrouped();

    /**
     * Fechas de creación desde {@code since}, para construir la gráfica de actividad diaria.
     * El agrupamiento por día se hace en Java (ver DocumentHistoryService) en lugar de con
     * una función de fecha específica de motor (H2 FORMATDATETIME no existe en PostgreSQL),
     * para que la consulta funcione igual en ambos motores.
     */
    @Query("SELECT d.createdAt FROM DocumentVersion d WHERE d.createdAt >= :since")
    List<LocalDateTime> findCreatedAtSince(@Param("since") LocalDateTime since);

    /** Search within document content (DB-level, avoids loading all CLOBs into memory). */
    @Query("SELECT d FROM DocumentVersion d WHERE LOWER(CAST(d.content AS string)) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY d.createdAt DESC")
    List<DocumentVersion> searchContent(@Param("q") String query);
}
