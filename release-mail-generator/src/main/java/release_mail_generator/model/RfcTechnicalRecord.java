package release_mail_generator.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import release_mail_generator.converter.BusinessRuleListConverter;
import release_mail_generator.converter.RelatedBugListConverter;
import release_mail_generator.converter.TestCaseListConverter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rfc_technical_records")
@Data
public class RfcTechnicalRecord {

    @Id
    private String id;

    // 1. General info
    private String rfcNumber;
    private String changeName;
    private String validationDate;
    private String testerName;
    private String requester;
    private String environment;

    // 2. Context
    @Column(columnDefinition = "TEXT")
    private String changeContext;

    // 3. Objectives
    @Column(columnDefinition = "TEXT")
    private String mainObjective;

    @Column(columnDefinition = "TEXT")
    private String specificObjectives;

    // 4. Technical components
    @Column(columnDefinition = "TEXT")
    private String modules;

    @Column(columnDefinition = "TEXT")
    private String storedProcedures;

    @Column(columnDefinition = "TEXT")
    private String jobs;

    @Column(columnDefinition = "TEXT")
    private String tables;

    @Column(columnDefinition = "TEXT")
    private String reports;

    @Column(columnDefinition = "TEXT")
    private String otherComponents;

    // 5. Business rules
    @Convert(converter = BusinessRuleListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<BusinessRule> businessRules = new ArrayList<>();

    // 6. Test cases
    @Convert(converter = TestCaseListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<TestCase> testCases = new ArrayList<>();

    // 7. Related bugs
    @Convert(converter = RelatedBugListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<RelatedBug> relatedBugs = new ArrayList<>();

    // 8. Conclusions
    private String finalResult;

    @Column(columnDefinition = "TEXT")
    private String observations;

    @Column(columnDefinition = "TEXT")
    private String risks;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    // 9. Final notes
    @Column(columnDefinition = "TEXT")
    private String finalNotes;

    // Status
    private String status;

    @JsonIgnore
    private LocalDateTime createdAt;

    @JsonIgnore
    private LocalDateTime updatedAt;
}

