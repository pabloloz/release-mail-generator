package release_mail_generator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import release_mail_generator.model.RfcTechnicalRecord;

import java.util.List;

@Repository
public interface RfcTechnicalRepository extends JpaRepository<RfcTechnicalRecord, String> {

    List<RfcTechnicalRecord> findAllByOrderByCreatedAtDesc();
}
