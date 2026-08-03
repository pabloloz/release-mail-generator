package release_mail_generator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import release_mail_generator.model.DocumentVersion;

import java.time.LocalDateTime;
import java.util.List;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, String> {

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

    @Query("SELECT FUNCTION('FORMATDATETIME', d.createdAt, 'yyyy-MM-dd') as day, COUNT(d) FROM DocumentVersion d WHERE d.createdAt >= :since GROUP BY FUNCTION('FORMATDATETIME', d.createdAt, 'yyyy-MM-dd') ORDER BY day")
    List<Object[]> dailyActivitySince(@Param("since") LocalDateTime since);

    /** Search within document content (DB-level, avoids loading all CLOBs into memory). */
    @Query("SELECT d FROM DocumentVersion d WHERE LOWER(CAST(d.content AS string)) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY d.createdAt DESC")
    List<DocumentVersion> searchContent(@Param("q") String query);
}
