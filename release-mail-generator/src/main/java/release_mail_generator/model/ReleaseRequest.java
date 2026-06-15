package release_mail_generator.model;

import lombok.Data;

@Data
public class ReleaseRequest {

    // Metadata
    private String version;
    private String rollbackVersion;
    private String publishDate;
    private String releaseUrl;

    // Branches
    private String branchModules;
    private String branchWinter;

    // Rutas individuales por artefacto
    private String pathModules;
    private String pathCitrix;
    private String pathDll;
    private String pathWinterX;
    private String pathScripts;

    // Artefactos a publicar (checkboxes)
    private boolean hasModules;
    private boolean hasCitrix;
    private boolean hasDll;
    private boolean hasWinterX;
    private boolean hasScripts;

    // Scripts SQL (uno por línea)
    private String scripts;

    // SPS's - tickets y stored procedures
    private String spsTickets;

    // Proyectos / RFCs
    private String projects;

    // Notas especiales
    private String notes;
}
