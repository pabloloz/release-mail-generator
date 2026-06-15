package release_mail_generator.model;

import lombok.Data;

@Data
public class UatTestCaseItem {
    private String sucursal;
    private String suscriptor;
    private String servicio;
    private String expectedResult;
    private String obtainedResult;
    private String observations;
}
