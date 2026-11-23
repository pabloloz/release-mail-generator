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
    private String requerimientosImagen; // Legacy single image (backward compat)
    private List<String> requerimientosImagenes = new ArrayList<>(); // Multiple images (base64 data URIs)

    // Bloques dinámicos: ejemplos, pasos, evidencias (texto + imagen opcional cada uno)
    private List<UatBlock> blocks = new ArrayList<>();

    // Cierre
    private String nota;            // Sección "NOTA:" — texto libre
    private String cierre;          // "Quedo pendiente..." — default si está vacío

    // ── Formato estructurado (todo opcional; si no viene nada se usa el formato legacy) ──
    private String recipient;                 // "equipo de UAT"
    private String subject;                   // Asunto; si está vacío se genera "VoBo UAT – RFC {n} – {nombre}"
    private List<String> relatedRfcs = new ArrayList<>(); // "25554"
    private String publishDate;               // "yyyy-MM-dd" o texto libre
    private Integer requirementsStart;        // Numeración inicial de requerimientos (default 1)
    private String requirementsIntro;         // Texto libre previo a la lista ("y su anexo RFC 25554")
    private List<Requirement> requirements = new ArrayList<>();
    private List<Example> validationExamples = new ArrayList<>();
    private String validationModule;          // Default "Servicios_UAT"
    private String validationServer;          // Default "MEGANG-384"
    private List<Step> validationSteps = new ArrayList<>();

    @Data
    public static class Requirement {
        private String title;
        private String description;
        private List<String> bullets = new ArrayList<>();
        private String image;        // data URI
    }

    @Data
    public static class KeyValue {
        private String key;
        private String value;
    }

    @Data
    public static class Example {
        private String title;        // Encabezado editable; si está vacío y hay varios: "Ejemplo N:"
        private List<KeyValue> pairs = new ArrayList<>();
        private String image;        // data URI
    }

    @Data
    public static class Step {
        private String text;
        /** data URI, o "@step1".."@step3" para usar las imágenes predeterminadas del servidor. */
        private String image;
    }
}
