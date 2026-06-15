package release_mail_generator.model;

import lombok.Data;

@Data
public class TestCase {
    private String caseId;
    private String description;
    private String expectedResult;
    private String obtainedResult;
    private String result; // Exitoso, Fallido
}
