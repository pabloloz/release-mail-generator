package release_mail_generator.model;

import lombok.Data;
import java.util.List;

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

    // Módulos funcionales a distribuir cuando hasModules = true
    private List<String> distributionModules;

    // Telegram: módulos para sección Acción
    private List<String> telegramModules;

    // Telegram: scripts a ejecutar (lista dinámica)
    private List<String> telegramScripts;

    // Telegram: notas/instrucciones
    private String telegramNotes;

    // Telegram: versiones
    private String telegramVersionModule;
    private String telegramVersionDllCsharp;
    private String telegramVersionWinterX;

    // Telegram: cambios de release (listas paralelas)
    private List<String> telegramChangeIds;
    private List<String> telegramChangeDescriptions;

    // Telegram: rollback
    private String telegramRollbackVersion;

    // Telegram: branches
    private String telegramBranchModules;
    private String telegramBranchWinterX;
    private String telegramBranchDllRepoUrl;
    private String telegramBranchDllName;

    // Telegram: link de release
    private String telegramReleaseUrl;

    // Scripts SQL (uno por línea)
    private String scripts;

    // SPS's - tickets y stored procedures
    private String spsTickets;

    // Proyectos / RFCs
    private String projects;

    // Notas especiales
    private String notes;
}
