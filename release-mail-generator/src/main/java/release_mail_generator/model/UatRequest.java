package release_mail_generator.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class UatRequest {

    private String id;

    // 1. General info
    private String rfcNumber;
    private String rfcName;
    private String moduleName;
    private String environment;
    private String qaResponsible;
    private String requestDate;

    // 2. Scope of validation
    private String scopeDescription;         // Intro sentence / "This RFC allows..."
    private List<String> scopeItems = new ArrayList<>();   // bullet items (services / promotions)
    private String promotionNumbers;         // "5251, 3815, 3816, ..."
    private String functionalScope;          // optional extra scope text

    // 3. Validation scenarios
    private List<String> scenarios = new ArrayList<>();

    // 4. Requirements for testers
    private List<UatRequirement> requirements = new ArrayList<>();

    // 5. Test cases / validated examples
    private List<UatTestCaseItem> testCases = new ArrayList<>();

    // 6. Execution steps
    private List<String> executionSteps = new ArrayList<>();

    // 7. Technical config (all optional)
    private String server;
    private String path;
    private String technicalEnvironment;
    private String appUrl;
    private String application;

    // 8. Attachments (metadata only — names listed in the email)
    private List<String> attachmentNames = new ArrayList<>();

    // Final comments
    private String finalComments;

    // Status
    private String status; // Borrador, Enviado, Aprobado, Rechazado

    @JsonIgnore
    private LocalDateTime createdAt;

    @JsonIgnore
    private LocalDateTime updatedAt;
}
