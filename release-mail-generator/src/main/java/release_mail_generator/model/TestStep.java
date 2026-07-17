package release_mail_generator.model;

import lombok.Data;

@Data
public class TestStep {
    private int stepNumber;
    private String action;
    private String expectedResult;
}
