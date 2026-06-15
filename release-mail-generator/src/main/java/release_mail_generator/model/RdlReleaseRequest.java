package release_mail_generator.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RdlReleaseRequest {

    // Jackson deserializa la lista correctamente desde JSON
    private List<RdlItem> rdls = new ArrayList<>();

    // Datos globales de la liberación (compartidos entre todos los RDL's)
    private String rdlReleaseDate;   // Ej: 05/06/2026 8:30 am
    private String rdlReleaseUrl;    // Release en Jira
}
