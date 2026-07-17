package release_mail_generator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import release_mail_generator.model.RfcTechnicalRecord;

import java.util.List;
import java.util.Optional;

@Repository
public interface RfcTechnicalRepository extends JpaRepository<RfcTechnicalRecord, String> {

    List<RfcTechnicalRecord> findAllByOrderByCreatedAtDesc();

    Optional<RfcTechnicalRecord> findByRfcNumberIgnoreCase(String rfcNumber);

    List<RfcTechnicalRecord> findAllByRfcNumberIgnoreCase(String rfcNumber);

    @Query("SELECT r FROM RfcTechnicalRecord r WHERE " +
           "LOWER(r.rfcNumber) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(r.changeName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(r.testerName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY r.createdAt DESC")
    List<RfcTechnicalRecord> search(@Param("q") String query);
}
