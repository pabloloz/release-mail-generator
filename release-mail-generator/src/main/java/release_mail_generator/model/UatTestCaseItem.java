package release_mail_generator.model;

import lombok.Data;

/**
 * Fila de la tabla "Casos Validados" en VoBo UAT.
 */
@Data
public class UatTestCaseItem {
    private String sucursal;
    private String suscriptor;
    private String servicio;
    private String resultado;
}
