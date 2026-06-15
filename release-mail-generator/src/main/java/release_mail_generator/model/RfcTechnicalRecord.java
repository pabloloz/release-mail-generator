package release_mail_generator.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class RfcTechnicalRecord {

    private String id;

    // 1. General info
    private String rfcNumber;
    private String changeName;
    private String validationDate;
    private String testerName;
    private String requester;
    private String environment; // DEV, QA, UAT, Otro

    // 2. Context
    private String changeContext;

    // 3. Objectives
    private String mainObjective;
    private String specificObjectives;

    // 4. Technical components
    private String modules;
    private String storedProcedures;
    private String jobs;
    private String tables;
    private String reports;
    private String otherComponents;

    // 5. Business rules
    private List<BusinessRule> businessRules = new ArrayList<>();

    // 6. Test cases
    private List<TestCase> testCases = new ArrayList<>();

    // 7. Related bugs
    private List<RelatedBug> relatedBugs = new ArrayList<>();

    // 8. Conclusions
    private String finalResult; // Cumple, No cumple
    private String observations;
    private String risks;
    private String recommendations;

    // 9. Final notes
    private String finalNotes;

    // Status
    private String status; // Borrador, En validación, Aprobado, Rechazado

    @JsonIgnore
    private LocalDateTime createdAt;

    @JsonIgnore
    private LocalDateTime updatedAt;
}
