package release_mail_generator.model;

import lombok.Data;

@Data
public class RdlItem {

    private String rdlReportName;    // Ej: CAR_CuentasMaestras
    private String rdlReportFolder;  // Ej: Modulos/Cartera/Reportes/Cartera

    private String rdlUrlMegang;     // http://megang-612/Reports/report/...
    private String rdlUrlNtrs02;     // http://ntrs02/Reports/Pages/Report.aspx?ItemPath=...

    private String rdlPathMegang;    // \\ntdesarrollo...\RDLL's\MEGANG-612
    private String rdlPathNtrs02;    // \\ntdesarrollo...\RDLL's\NTRS02

    // Stored Procedure (opcional, por RDL)
    private boolean hasRdlSp;
    private String rdlSpName;        // Ej: mcab_rdl_cuentasmaestras.sql
    private String rdlSpTicket;      // N° ticket VoBo

    // Fecha y Proyecto (por RDL)
    private String rdlReleaseDate;   // Ej: 04/05/2026 8:30 am
    private String rdlProject;       // Ej: RFC 23533 | "CAR_CuentasMaestras" cambio RDL y SP
}
