package release_mail_generator.model;

import lombok.Data;

/**
 * Un bloque libre del correo VoBo UAT: texto + imagen de evidencia opcional.
 */
@Data
public class UatBlock {
    private String texto;
    private String imagenBase64; // data URI (data:image/...;base64,...) o vacío
}
