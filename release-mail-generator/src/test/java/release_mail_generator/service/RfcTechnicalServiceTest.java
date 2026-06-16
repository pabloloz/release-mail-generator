package release_mail_generator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import release_mail_generator.model.BusinessRule;
import release_mail_generator.model.RfcTechnicalRecord;
import release_mail_generator.model.TestCase;
import release_mail_generator.repository.RfcTechnicalRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RfcTechnicalServiceTest {

    @Mock
    private RfcTechnicalRepository repository;

    @InjectMocks
    private RfcTechnicalService service;

    // ── save ───────────────────────────────────────────────────────────────

    @Test
    void save_nuevoRegistro_asignaIdYFechaCreacion() {
        RfcTechnicalRecord record = new RfcTechnicalRecord();
        record.setRfcNumber("RFC-001");

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RfcTechnicalRecord saved = service.save(record);

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo("Borrador");
    }

    @Test
    void save_statusExistente_noSobreescribeStatus() {
        RfcTechnicalRecord record = new RfcTechnicalRecord();
        record.setRfcNumber("RFC-002");
        record.setStatus("Aprobado");

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RfcTechnicalRecord saved = service.save(record);

        assertThat(saved.getStatus()).isEqualTo("Aprobado");
    }

    @Test
    void save_registroExistente_conservaFechaCreacion() {
        RfcTechnicalRecord existing = new RfcTechnicalRecord();
        existing.setId("existing-id");
        existing.setCreatedAt(java.time.LocalDateTime.of(2026, 1, 1, 10, 0));

        RfcTechnicalRecord updated = new RfcTechnicalRecord();
        updated.setId("existing-id");
        updated.setRfcNumber("RFC-003");

        when(repository.findById("existing-id")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RfcTechnicalRecord saved = service.save(updated);

        assertThat(saved.getCreatedAt()).isEqualTo(existing.getCreatedAt());
    }

    @Test
    void save_inicializaListasNulas() {
        RfcTechnicalRecord record = new RfcTechnicalRecord();
        record.setBusinessRules(null);
        record.setTestCases(null);
        record.setRelatedBugs(null);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RfcTechnicalRecord saved = service.save(record);

        assertThat(saved.getBusinessRules()).isNotNull();
        assertThat(saved.getTestCases()).isNotNull();
        assertThat(saved.getRelatedBugs()).isNotNull();
    }

    // ── findById ───────────────────────────────────────────────────────────

    @Test
    void findById_idExistente_retornaRegistro() {
        RfcTechnicalRecord record = new RfcTechnicalRecord();
        record.setId("test-id");
        when(repository.findById("test-id")).thenReturn(Optional.of(record));

        Optional<RfcTechnicalRecord> result = service.findById("test-id");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("test-id");
    }

    @Test
    void findById_idInexistente_retornaVacio() {
        when(repository.findById("no-existe")).thenReturn(Optional.empty());

        Optional<RfcTechnicalRecord> result = service.findById("no-existe");

        assertThat(result).isEmpty();
    }

    // ── findAll ────────────────────────────────────────────────────────────

    @Test
    void findAll_retornaListaOrdenadaDelRepositorio() {
        RfcTechnicalRecord r1 = new RfcTechnicalRecord();
        r1.setId("id1");
        RfcTechnicalRecord r2 = new RfcTechnicalRecord();
        r2.setId("id2");
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(r1, r2));

        List<RfcTechnicalRecord> result = service.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("id1");
    }

    // ── delete ─────────────────────────────────────────────────────────────

    @Test
    void delete_idExistente_retornaTrue() {
        when(repository.existsById("test-id")).thenReturn(true);
        doNothing().when(repository).deleteById("test-id");

        boolean result = service.delete("test-id");

        assertThat(result).isTrue();
        verify(repository).deleteById("test-id");
    }

    @Test
    void delete_idInexistente_retornaFalse() {
        when(repository.existsById("no-existe")).thenReturn(false);

        boolean result = service.delete("no-existe");

        assertThat(result).isFalse();
        verify(repository, never()).deleteById(any());
    }

    // ── generatePdf ────────────────────────────────────────────────────────

    @Test
    void generatePdf_registroCompleto_nofalla() throws Exception {
        RfcTechnicalRecord record = new RfcTechnicalRecord();
        record.setId("pdf-test");
        record.setRfcNumber("RFC-100");
        record.setChangeName("Cambio de prueba");
        record.setTesterName("Tester QA");
        record.setEnvironment("UAT");
        record.setStatus("Aprobado");
        record.setFinalResult("Cumple");

        BusinessRule rule = new BusinessRule();
        rule.setDescription("Regla de negocio 1");
        rule.setValidationStatus("Válida");
        record.setBusinessRules(List.of(rule));

        TestCase tc = new TestCase();
        tc.setCaseId("TC-01");
        tc.setDescription("Caso de prueba");
        tc.setResult("Exitoso");
        record.setTestCases(List.of(tc));

        byte[] pdf = service.generatePdf(record);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(0);
    }
}
