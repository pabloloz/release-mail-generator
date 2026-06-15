package release_mail_generator.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class UatRequest {

    // Información general
    private String rfcNumber;
    private String rfcName;
    private String moduleName;
    private String environment;
    private String releaseJira;

    // Alcance (texto libre)
    private String alcance;

    // Escenarios a validar
    private List<String> scenarios = new ArrayList<>();

    // Datos para pruebas (sucursal / suscriptor / servicio / observaciones)
    private List<UatRequirement> testData = new ArrayList<>();

    // Casos validados (sucursal / suscriptor / servicio / resultado)
    private List<UatTestCaseItem> validatedCases = new ArrayList<>();

    // Pasos de ejecución
    private List<String> executionSteps = new ArrayList<>();

    // Adjuntos (solo nombres, se mencionan en el correo)
    private List<String> attachmentNames = new ArrayList<>();
}
