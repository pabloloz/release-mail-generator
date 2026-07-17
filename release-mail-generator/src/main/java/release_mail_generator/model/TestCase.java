package release_mail_generator.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class TestCase {
    // ── General ───────────────────────────────────────────────
    private String caseId;
    private String caseName;
    private String priority;       // Alta, Media, Baja
    private String testType;       // Funcional, Regresión, Integración, Smoke, UAT
    private String status;         // Pendiente, En ejecución, Aprobado, Fallido, Bloqueado
    private String testerName;
    private String executionDate;

    // ── Objetivo ──────────────────────────────────────────────
    private String objective;

    // ── Precondiciones ────────────────────────────────────────
    private String preconditions;

    // ── Datos de Prueba ───────────────────────────────────────
    private String testData;

    // ── Pasos de Ejecución ────────────────────────────────────
    private List<TestStep> steps = new ArrayList<>();

    // ── Resultado ─────────────────────────────────────────────
    private String obtainedResult;
    private String observations;
    private String incidents;

    // ── Evidencias ────────────────────────────────────────────
    private List<TestEvidence> evidences = new ArrayList<>();

    // ── Observaciones Finales ─────────────────────────────────
    private String finalObservations;

    // ── Legacy fields (backward compat) ───────────────────────
    private String description;    // maps to caseName/objective
    private String expectedResult; // legacy
    private String result;         // legacy: Exitoso/Fallido → maps to status
}
