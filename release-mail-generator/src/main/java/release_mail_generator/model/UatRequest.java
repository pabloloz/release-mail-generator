package release_mail_generator.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class UatRequest {

    // Encabezado del correo
    private String rfcNumber;       // "RFC 24269"
    private String rfcName;         // "Anexo a RFC 24015 Promociones combinen con 120 Mbps"
    private String saludo;          // "Buenas tardes." — default si está vacío
    private String adjunto;         // "Adjunto documentación y universo." — opcional

    // Cuerpo libre
    private String requerimientos;       // Sección "Requerimientos:" — texto libre
    private String requerimientosImagen; // Imagen opcional bajo requerimientos (base64 data URI)

    // Bloques dinámicos: ejemplos, pasos, evidencias (texto + imagen opcional cada uno)
    private List<UatBlock> blocks = new ArrayList<>();

    // Cierre
    private String nota;            // Sección "NOTA:" — texto libre
    private String cierre;          // "Quedo pendiente..." — default si está vacío
}
