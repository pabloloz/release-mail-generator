package release_mail_generator.model;

import lombok.Data;

/**
 * Fila de la tabla "Datos para Pruebas" en VoBo UAT.
 */
@Data
public class UatRequirement {
    private String sucursal;
    private String suscriptor;
    private String servicio;
    private String observaciones;
}
