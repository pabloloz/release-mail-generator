package release_mail_generator.model;

import lombok.Data;

@Data
public class BusinessRule {
    private String description;
    private String validationStatus; // Válida, Inválida, Pendiente
}
