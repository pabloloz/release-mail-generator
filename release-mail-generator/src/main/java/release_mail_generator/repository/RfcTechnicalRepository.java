package release_mail_generator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import release_mail_generator.model.RfcTechnicalRecord;

import java.util.List;

@Repository
public interface RfcTechnicalRepository extends JpaRepository<RfcTechnicalRecord, String> {

    List<RfcTechnicalRecord> findAllByOrderByCreatedAtDesc();

    List<RfcTechnicalRecord> findAllByRfcNumberIgnoreCase(String rfcNumber);

    /**
     * Proyección ligera para listados: excluye testCases/businessRules/relatedBugs
     * (TEXT con evidencias en base64) que no se usan en la vista de lista.
     */
    @Query("SELECT r.id as id, r.rfcNumber as rfcNumber, r.changeName as changeName, " +
           "r.validationDate as validationDate, r.testerName as testerName, " +
           "r.requester as requester, r.environment as environment, " +
           "r.status as status, r.finalResult as finalResult, " +
           "r.createdAt as createdAt, r.updatedAt as updatedAt " +
           "FROM RfcTechnicalRecord r ORDER BY r.createdAt DESC")
    List<RfcSummary> findAllSummaries();

    interface RfcSummary {
        String getId();
        String getRfcNumber();
        String getChangeName();
        String getValidationDate();
        String getTesterName();
        String getRequester();
        String getEnvironment();
        String getStatus();
        String getFinalResult();
        java.time.LocalDateTime getCreatedAt();
        java.time.LocalDateTime getUpdatedAt();
    }

    @Query("SELECT r FROM RfcTechnicalRecord r WHERE " +
           "LOWER(r.rfcNumber) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(r.changeName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(r.testerName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY r.createdAt DESC")
    List<RfcTechnicalRecord> search(@Param("q") String query);

    /** Deep content search — searches across text fields without loading full entities into app memory. */
    @Query("SELECT r FROM RfcTechnicalRecord r WHERE " +
           "LOWER(r.changeContext) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(r.mainObjective) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(r.modules) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(r.storedProcedures) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(r.jobs) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(r.tables) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(r.reports) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(r.observations) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(r.risks) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(r.finalNotes) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY r.createdAt DESC")
    List<RfcTechnicalRecord> deepSearch(@Param("q") String query);
}
