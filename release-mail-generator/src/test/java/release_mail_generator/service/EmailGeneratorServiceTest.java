package release_mail_generator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import release_mail_generator.model.RdlItem;
import release_mail_generator.model.RdlReleaseRequest;
import release_mail_generator.model.ReleaseRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EmailGeneratorServiceTest {

    @InjectMocks
    private EmailGeneratorService service;

    @BeforeEach
    void init() throws Exception {
        // Invocar @PostConstruct manualmente ya que no hay contexto Spring
        var method = EmailGeneratorService.class.getDeclaredMethod("init");
        method.setAccessible(true);
        method.invoke(service);
    }

    // ── generateEmail ──────────────────────────────────────────────────────

    @Test
    void generateEmail_retornaHtmlConSaludo() {
        ReleaseRequest r = new ReleaseRequest();
        r.setHasModules(true);
        r.setPathModules("\\\\servidor\\ruta\\Modulos");

        String html = service.generateEmail(r);

        assertThat(html).contains("Buen día Operaciones");
        assertThat(html).contains("Módulos");
        assertThat(html).contains("<a href=\"file://servidor/ruta/Modulos\"");
    }

    @Test
    void generateEmail_escapaHtmlEnRuta() {
        ReleaseRequest r = new ReleaseRequest();
        r.setHasModules(true);
        r.setPathModules("\\\\servidor\\ruta\\<script>alert(1)</script>");

        String html = service.generateEmail(r);

        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void generateEmail_conVersion_incluyeVersionEnHtml() {
        ReleaseRequest r = new ReleaseRequest();
        r.setVersion("2.5.1");
        r.setPublishDate("20/06/2026");

        String html = service.generateEmail(r);

        assertThat(html).contains("2.5.1");
        assertThat(html).contains("20/06/2026");
    }

    @Test
    void generateEmail_conNota_incluyeNotaEnHtml() {
        ReleaseRequest r = new ReleaseRequest();
        r.setNotes("Requiere reinicio de servicio.");

        String html = service.generateEmail(r);

        assertThat(html).contains("Nota importante").contains("Requiere reinicio de servicio.");
    }

    @Test
    void generateEmail_notaEspecial_esLaCapturadaSeResaltaYUnificaHorarios() {
        ReleaseRequest r = new ReleaseRequest();
        r.setNotes("Favor de cambiar C# en zona de RECUPERADORES a las 11 pm y DLL en los servidores de saldo de AWS a las 11:00 pm");
        r.setSpsTickets("Ticket: 1\nSP: sp_a");

        String html = service.generateEmail(r);

        assertThat(html).contains("\u26A0\uFE0F&nbsp;Nota importante</p>")
            .contains("<b>Favor de cambiar C# en zona de RECUPERADORES a las 11:00 pm y DLL en los servidores de saldo de AWS a las 11:00 pm</b>");
        // No se agrega ninguna nota que el usuario no haya escrito
        assertThat(html).doesNotContain("coordinarse").doesNotContain("Nota:");
        assertThat(service.generateEmailPlainText(r)).contains("NOTA IMPORTANTE\nFavor de cambiar C# en zona de RECUPERADORES a las 11:00 pm y DLL")
            .doesNotContain("coordinarse");
    }

    @Test
    void generateEmail_sinNotaEspecial_noMuestraSeccionDeNota() {
        ReleaseRequest r = new ReleaseRequest();
        r.setSpsTickets("Ticket: 1\nSP: sp_a");

        assertThat(service.generateEmail(r)).doesNotContain("Nota importante").doesNotContain("coordinarse");
    }

    @Test
    void generateEmail_conSps_muestraRepositorioTicketYScriptsConVinetas() {
        ReleaseRequest r = new ReleaseRequest();
        r.setSpsTickets("Ticket: 129660496\r\nSP: [dbo].[sp_a]\r\nSP: sp_c\r\n\r\nTicket: 7\r\nSP: sp_b");

        String html = service.generateEmail(r);

        String mono = "<span style=\"font-family: Consolas, 'Courier New', monospace; font-size: 12px; font-weight: normal;\">";
        assertThat(html).contains("&#8226; <b>SP's @DBAs TI</b><br>Se encuentra en la ubicación: <a href=\"https://github.com/Megacable-IT/storedproc.git\""
            + " style=\"color: #2F6DB5; font-family: Consolas, 'Courier New', monospace; font-size: 12px; font-weight: normal;\">"
            + "https://github.com/Megacable-IT/storedproc.git</a>; los scripts correspondientes se encuentran adjuntos en este correo.");
        assertThat(html).contains("<br>Ticket: 129660496<br>&#8226; " + mono + "[dbo].[sp_a]</span><br>&#8226; " + mono + "sp_c</span>")
            .contains("<br>Ticket: 7<br>&#8226; " + mono + "sp_b</span>");
        assertThat(html).doesNotContain("SP: [dbo]").doesNotContain(">SP: sp_b").doesNotContain("1.&nbsp;");
        // La frase de los scripts adjuntos aparece una sola vez aunque haya varios tickets
        assertThat(html.split("los scripts correspondientes se encuentran adjuntos en este correo", -1)).hasSize(2);
    }

    @Test
    void generateEmail_disenoCorporativo_estructuraEmojisYReglasTecnicas() {
        ReleaseRequest r = new ReleaseRequest();
        r.setVersion("26.10.31");
        r.setPublishDate("2026-11-05");
        r.setRollbackVersion("26.10.30");
        r.setHasModules(true);
        r.setDistributionModules(List.of("Cartera", "Servicios", "Control"));
        r.setPathModules("\\\\srv\\Modulos");
        r.setHasCitrix(true);
        r.setPathCitrix("\\\\srv\\Citrix");
        r.setSpsTickets("Ticket: 1\nSP: sp_a");
        r.setNotes("Nota");
        r.setProjects("RFC 1");
        r.setBranchModules("Feature_X");
        r.setReleaseUrl("https://jira/x");

        String html = service.generateEmail(r);

        // Solo p, b, a, br y span; sin tablas, listas reales, anchos fijos ni CSS avanzado
        assertThat(html).doesNotContainPattern("<(?!/?(?:p|b|a|br|span)\\b)");
        assertThat(html).doesNotContain("class=", "width", "max-width", "margin: auto", "flex", "grid", "var(--", "<style");
        // Cada párrafo define su margen de forma explícita
        assertThat(html).doesNotContainPattern("<p(?![^>]*margin:)");
        assertThat(html).contains("margin: 18px 0 6px 0;").contains("margin: 0 0 6px 0;").contains("margin: 0 0 8px 0;");
        // Sin fondos ni color de texto propio: el único color es el azul de acento #2F6DB5
        assertThat(html).doesNotContain("bgcolor", "background", "gradient", "shadow", "border", "#8A8F98");
        assertThat(html).doesNotContainPattern("(?<![-\\w])color:\\s*+(?!#2F6DB5)");
        // Tipografía: secciones 15 px, cuerpo 13 px / interlineado 1.5, rutas y nombres técnicos 12 px
        assertThat(html).contains("font-size: 15px; font-weight: bold; color: #2F6DB5;")
            .contains("font-size: 13px;").contains("font-size: 12px;").contains("line-height: 1.5")
            .doesNotContain("font-size: 22px");
        assertThat(html).contains("font-family: 'Segoe UI', Calibri, Arial, sans-serif;");
        // Solo los emojis permitidos, uno por título de sección
        assertThat(html).contains("\uD83D\uDCE6&nbsp;Distribución</p>")
            .contains("\uD83D\uDCCB&nbsp;Datos de la liberación</p>")
            .contains("\uD83C\uDF3F&nbsp;Branches de compilación</p>")
            .contains("\uD83D\uDCDD&nbsp;Proyectos</p>")
            .contains("\u26A0\uFE0F&nbsp;Nota importante</p>");
        String sinPermitidos = html.replace("\uD83D\uDCE6", "").replace("\uD83D\uDCCB", "").replace("\uD83C\uDF3F", "")
            .replace("\uD83D\uDCDD", "").replace("\u26A0\uFE0F", "");
        assertThat(sinPermitidos).doesNotContainPattern("[\\x{2190}-\\x{2BFF}\\x{1F000}-\\x{1FAFF}\\x{FE0F}]");
        // Asunto, saludo y contexto exactos
        assertThat(html).startsWith("<p style=\"font-family: 'Segoe UI', Calibri, Arial, sans-serif; line-height: 1.5; font-size: 13px; margin: 0 0 12px 0;\">"
            + "<b>Asunto:</b> Solicitud de liberación 26.10.31 – 05/11/2026</p>");
        assertThat(html).contains("Buen día Operaciones:</p>")
            .contains("Solicitando su apoyo para la distribución de Módulos (Cartera, Servicios, Control), SP's @DBAs TI;"
                + " a continuación se mencionan las rutas donde se encontrará lo necesario para el cambio.</p>");
        // Citrix no se menciona en la frase de introducción, pero su sección con la ruta se conserva
        assertThat(html).doesNotContain("Control), Citrix");
        // Orden: distribución, datos, branches, proyectos, nota y cierre
        assertThat(html.indexOf("Buen día Operaciones")).isLessThan(html.indexOf("Distribución</p>"));
        assertThat(html.indexOf("Distribución</p>")).isLessThan(html.indexOf("Datos de la liberación</p>"));
        assertThat(html.indexOf("Datos de la liberación</p>")).isLessThan(html.indexOf("Branches de compilación</p>"));
        assertThat(html.indexOf("Branches de compilación</p>")).isLessThan(html.indexOf("Proyectos</p>"));
        assertThat(html.indexOf("Proyectos</p>")).isLessThan(html.indexOf("Nota importante</p>"));
        assertThat(html.indexOf("Nota importante</p>")).isLessThan(html.indexOf("Quedo atento"));
        // Componentes: "• Nombre" en negritas y, debajo, "Se encuentra en la siguiente ubicación:" con la ruta como hipervínculo
        String link = "style=\"color: #2F6DB5; font-family: Consolas, 'Courier New', monospace; font-size: 12px; font-weight: normal;\">";
        assertThat(html).contains("&#8226; <b>Módulos</b><br>Se encuentra en la siguiente ubicación: <a href=\"file://srv/Modulos\" " + link + "\\\\srv\\Modulos</a>")
            .contains("&#8226; <b>Citrix</b><br>Se encuentra en la siguiente ubicación: <a href=\"file://srv/Citrix\" " + link + "\\\\srv\\Citrix</a>");
        assertThat(html.indexOf("<b>Módulos</b>")).isLessThan(html.indexOf("<b>Citrix</b>"));
        assertThat(html.indexOf("<b>Citrix</b>")).isLessThan(html.indexOf("<b>SP's @DBAs TI</b>"));
        // Etiquetas en negritas con el valor en la misma línea; branches, proyectos y nota
        assertThat(html).contains("<b>Fecha de publicación:</b> 05/11/2026<br><b>Versión:</b> 26.10.31<br><b>Rollback:</b> 26.10.30"
            + "<br><b>Release:</b> <a href=\"https://jira/x\"");
        assertThat(html).contains("<b>Módulos:</b> <span style=\"font-family: Consolas, 'Courier New', monospace; font-size: 12px; font-weight: normal;\">Feature_X</span>")
            .contains("&#8226; RFC 1").contains("<b>Nota</b></p>");
        // Rutas, scripts y branches en Consolas 12 px y sin negritas
        assertThat(html).doesNotContainPattern("<b>(?:(?!</b>).)*(?:Consolas|file://)");
        assertThat(html).contains("Quedo atento a cualquier duda.<br>Saludos.");
    }

    @Test
    void generateEmail_scriptsVanComoComponenteAntesDeLosSps() {
        ReleaseRequest r = new ReleaseRequest();
        r.setHasCitrix(true);
        r.setPathCitrix("\\\\srv\\Citrix");
        r.setHasScripts(true);
        r.setPathScripts("\\\\srv\\Scripts");
        r.setScripts("a.sql\nb.sql");
        r.setSpsTickets("Ticket: 1\nSP: sp_a");

        String html = service.generateEmail(r);

        assertThat(html).contains("distribución de Scripts, SP's @DBAs TI;").doesNotContain("distribución de Citrix");
        assertThat(html).contains("&#8226; <b>Scripts</b><br>Se encuentra en la siguiente ubicación: <a href=\"file://srv/Scripts\"")
            .contains("<br>&#8226; <span").contains(">a.sql</span>").contains(">b.sql</span>");
        assertThat(html.indexOf("<b>Citrix</b>")).isLessThan(html.indexOf("<b>Scripts</b>"));
        assertThat(html.indexOf("<b>Scripts</b>")).isLessThan(html.indexOf("<b>SP's @DBAs TI</b>"));
        assertThat(html).doesNotContain("coordinarse");
    }

    @Test
    void generateEmail_conUnSoloModulo_usaSingularEnIntroduccionYSeccion() {
        ReleaseRequest r = new ReleaseRequest();
        r.setHasModules(true);
        r.setDistributionModules(List.of("Cartera"));
        r.setPathModules("\\\\srv\\Modulos");
        r.setHasDll(true);
        r.setPathDll("\\\\srv\\Dll");

        String html = service.generateEmail(r);
        String text = service.generateEmailPlainText(r);

        assertThat(html).contains("distribución del Módulo de Cartera, SFyCWSDLL;")
            .contains("&#8226; <b>Módulos</b><br>Se encuentra en la siguiente ubicación:")
            .doesNotContain("Módulos (Cartera)").doesNotContain("<b>Módulo de Cartera</b>").doesNotContain("Módulo (Cartera)");
        assertThat(text).contains("distribución del Módulo de Cartera, SFyCWSDLL;").contains("• Módulos\n")
            .doesNotContain("• Módulo de Cartera");
    }

    @Test
    void generateEmail_conVariosModulos_mantieneElPlural() {
        ReleaseRequest r = new ReleaseRequest();
        r.setHasModules(true);
        r.setDistributionModules(List.of("Cartera", "Servicios"));
        r.setPathModules("\\\\srv\\Modulos");

        assertThat(service.generateEmail(r)).contains("distribución de Módulos (Cartera, Servicios);")
            .contains("&#8226; <b>Módulos</b><br>");
    }

    @Test
    void generateTelegramMessage_accionEnPluralSoloConVariosModulos() {
        ReleaseRequest uno = new ReleaseRequest();
        uno.setTelegramModules(List.of("Cartera"));
        ReleaseRequest varios = new ReleaseRequest();
        varios.setTelegramModules(List.of("Cartera", "DLL"));

        assertThat(service.generateTelegramMessage(uno)).contains("Acción: Actualizar módulo\n");
        assertThat(service.generateTelegramMessage(varios)).contains("Acción: Actualizar módulos\n");
    }

    @Test
    void normalizeTimes_unificaHorariosAlFormatoHoraMinutosAmPm() {
        assertThat(service.normalizeTimes("C# a las 11pm y DLL a las 11:30 P.M.")).isEqualTo("C# a las 11:00 pm y DLL a las 11:30 pm.");
        assertThat(service.normalizeTimes("Cambiar a las 23:00 hrs. y a las 08:15 h")).isEqualTo("Cambiar a las 11:00 pm y a las 8:15 am");
        assertThat(service.normalizeTimes("Ya en 11:00 pm, 12:00 AM y 5 am")).isEqualTo("Ya en 11:00 pm, 12:00 am y 5:00 am");
        assertThat(service.normalizeTimes("Sin horarios: v26.6.11 y amigos")).isEqualTo("Sin horarios: v26.6.11 y amigos");
    }

    @Test
    void generateEmailPlainText_mismaInformacionSinFormatoNiEmojis() {
        ReleaseRequest r = new ReleaseRequest();
        r.setVersion("26.10.31");
        r.setPublishDate("2026-11-05");
        r.setRollbackVersion("26.10.30");
        r.setHasModules(true);
        r.setDistributionModules(List.of("Cartera", "Servicios"));
        r.setPathModules("\\\\srv\\Modulos");
        r.setSpsTickets("Ticket: 129660496\nSP: sp_a\nSP: sp_b");

        String text = service.generateEmailPlainText(r);

        assertThat(text).startsWith("Asunto: Solicitud de liberación 26.10.31 – 05/11/2026\n\nBuen día Operaciones:\n"
            + "Solicitando su apoyo para la distribución de Módulos (Cartera, Servicios), SP's @DBAs TI;"
            + " a continuación se mencionan las rutas donde se encontrará lo necesario para el cambio.\n");
        assertThat(text).contains("\nDISTRIBUCIÓN\n• Módulos\nSe encuentra en la siguiente ubicación: \\\\srv\\Modulos\n"
                + "• SP's @DBAs TI\nSe encuentra en la ubicación: https://github.com/Megacable-IT/storedproc.git;"
                + " los scripts correspondientes se encuentran adjuntos en este correo.\nTicket: 129660496\n• sp_a\n• sp_b\n")
            .contains("\nDATOS DE LA LIBERACIÓN\nFecha de publicación: 05/11/2026\nVersión: 26.10.31\nRollback: 26.10.30\n")
            .doesNotContain("coordinarse")
            .endsWith("\nQuedo atento a cualquier duda.\nSaludos.\n");
        assertThat(text).doesNotContain("<").doesNotContainPattern("[\\x{2190}-\\x{2BFF}\\x{1F000}-\\x{1FAFF}\\x{FE0F}]");
    }

    @Test
    void generateEmail_sinArtefactos_noIncluyeTarjetas() {
        ReleaseRequest r = new ReleaseRequest();

        String html = service.generateEmail(r);

        assertThat(html).contains("Buen día Operaciones");
        assertThat(html).doesNotContain("Distribución</p>").doesNotContain("Datos de la liberación");
    }

    // ── generateTelegramMessage ────────────────────────────────────────────

    @Test
    void generateTelegramMessage_usaBloqueDeCodigoConSeccionesEIconos() {
        ReleaseRequest r = new ReleaseRequest();
        r.setTelegramModules(List.of("Cartera", "Wirelink"));
        r.setTelegramScripts(List.of("update.sql"));
        r.setTelegramNotes("Reiniciar servicio");
        r.setPublishDate("2026-11-20");
        r.setTelegramVersionModule("26.10.8");
        r.setTelegramChangeIds(List.of("RFC 24015"));
        r.setTelegramChangeDescriptions(List.of("Ajuste de cobro"));
        r.setTelegramRollbackVersion("26.10.1");
        r.setTelegramBranchModules("Feature_X");
        r.setTelegramReleaseUrl("https://jira/release/1");

        String msg = service.generateTelegramMessage(r);

        assertThat(msg).startsWith("```\n").endsWith("```");
        assertThat(msg).contains("Acción: Actualizar módulos\n\uD83D\uDCE6 Cartera\n\uD83D\uDCE6 Wirelink");
        assertThat(msg).contains("Ejecutar Scripts:\n\uD83D\uDCC4 update.sql");
        assertThat(msg).contains("\u26A0\uFE0F Nota: Reiniciar servicio");
        assertThat(msg).contains("Publicar: 20/11/2026");
        assertThat(msg).contains("\uD83C\uDFF7\uFE0F Versión Módulo: 26.10.8");
        assertThat(msg).contains("Cambios Release:\n\u2705 RFC 24015 Ajuste de cobro");
        assertThat(msg).contains("\u21A9\uFE0F Versión Rollback: 26.10.1");
        assertThat(msg).contains("\uD83C\uDF3F Branch compilación Módulos: Feature_X");
        assertThat(msg).contains("\uD83D\uDD17 Release Publicación: https://jira/release/1");
    }

    @Test
    void generateTelegramMessage_soloUrl_noDejaLineaVaciaInicial() {
        ReleaseRequest r = new ReleaseRequest();
        r.setTelegramReleaseUrl("https://jira/release/1");

        assertThat(service.generateTelegramMessage(r))
            .isEqualTo("```\n\uD83D\uDD17 Release Publicación: https://jira/release/1\n```");
    }

    @Test
    void generateTelegramMessage_incluyeSpsPorTicketAntesDeLosScripts() {
        ReleaseRequest r = new ReleaseRequest();
        r.setTelegramModules(List.of("Cartera"));
        r.setTelegramSps("Ticket: 129660496\nSP: [dbo].[mcab_obtener]\n\nTicket: 111\nSP: sp_a\nSP: sp_b");
        r.setTelegramScripts(List.of("update.sql"));

        assertThat(service.generateTelegramMessage(r)).isEqualTo(
            "```\nAcción: Actualizar módulo\n\uD83D\uDCE6 Cartera\n\n"
            + "Actualizar SP:\n\uD83C\uDFAB Ticket: 129660496\n\uD83D\uDCC2 [dbo].[mcab_obtener]\n\n"
            + "\uD83C\uDFAB Ticket: 111\n\uD83D\uDCC2 sp_a\n\uD83D\uDCC2 sp_b\n\n"
            + "Ejecutar Scripts:\n\uD83D\uDCC4 update.sql\n\n```");
    }

    @Test
    void generateTelegramMessage_sinSpsDeTelegram_usaLosSpsDelCorreo() {
        ReleaseRequest r = new ReleaseRequest();
        r.setSpsTickets("Ticket: 5\nSP: sp_x");

        assertThat(service.generateTelegramMessage(r)).contains("Actualizar SP:\n\uD83C\uDFAB Ticket: 5\n\uD83D\uDCC2 sp_x");
    }

    // ── DLL C# en el correo de liberación ──────────────────────────────────

    private ReleaseRequest requestConDllCsharp() {
        ReleaseRequest r = new ReleaseRequest();
        r.setVersion("26.10.8");
        r.setHasDllCsharp(true);
        r.setPathDllCsharp("\\\\ntdesarrollo.evolution.corp\\sprints$\\RFC'S Semana 47 2026\\DLL C#");
        r.setVersionDllCsharp("26.6.11.2");
        return r;
    }

    @Test
    void generateEmail_conDllCsharp_incluyeIntroArtefactoYVersionSinBranch() {
        String html = service.generateEmail(requestConDllCsharp());

        assertThat(html).contains("<b>DLL C#</b>");
        assertThat(html).contains("<a href=\"file://ntdesarrollo.evolution.corp/");
        assertThat(html).contains("Versión DLL C#").contains("26.6.11.2");
        // El branch de la DLL C# es siempre el mismo: no se captura ni se muestra en el correo
        assertThat(html).doesNotContain("Branch de Compilación").doesNotContain("Branches de Compilación")
            .doesNotContain("repositorio");
    }

    @Test
    void generateEmail_conDllCsharp_codificaNumeralEnHrefPeroNoEnTextoVisible() {
        String html = service.generateEmail(requestConDllCsharp());

        assertThat(html).contains("sprints$/RFC'S Semana 47 2026/DLL C%23\"");
        assertThat(html).contains(">\\\\ntdesarrollo.evolution.corp\\sprints$\\RFC'S Semana 47 2026\\DLL C#</a>");
    }

    @Test
    void generateEmail_sinDllCsharp_noMencionaDllCsharp() {
        ReleaseRequest r = new ReleaseRequest();
        r.setHasDll(true);
        r.setPathDll("\\\\srv\\SFyCWSDLL");
        r.setVersion("1.0.0");
        // Datos residuales de DLL C# sin el artefacto marcado no deben generar el <li> ni la intro
        r.setPathDllCsharp("\\\\srv\\DLL C#");

        String html = service.generateEmail(r);

        assertThat(html).doesNotContain("<b>DLL C#</b>");
        assertThat(html).doesNotContain("Versión DLL C#");
        assertThat(html).doesNotContain("DLL C%23");
    }

    @Test
    void generateEmail_dllCsharpJuntoAOtrosArtefactos_introListaTodosEnOrden() {
        ReleaseRequest r = requestConDllCsharp();
        r.setHasDll(true);
        r.setPathDll("\\\\srv\\SFyCWSDLL");
        r.setHasWinterX(true);
        r.setPathWinterX("\\\\srv\\WinterX");

        String html = service.generateEmail(r);

        int dll = html.indexOf("<b>SFyCWSDLL</b>");
        int dllCs = html.indexOf("<b>DLL C#</b>");
        int winter = html.indexOf("<b>WinterX</b>");
        assertThat(dll).isPositive();
        assertThat(dllCs).isGreaterThan(dll);
        assertThat(winter).isGreaterThan(dllCs);
        assertThat(html).contains("Distribución</p>", "WinterX");
    }

    @Test
    void generateEmail_dllCsharp_escapaHtmlEnVersion() {
        ReleaseRequest r = new ReleaseRequest();
        r.setHasDllCsharp(true);
        r.setVersionDllCsharp("<img src=x>");

        String html = service.generateEmail(r);

        assertThat(html).doesNotContain("<img src=x>");
        assertThat(html).contains("&lt;img src=x&gt;");
    }

    @Test
    void generateEmail_versionDllCsharpSinArtefactoMarcado_seIgnora() {
        ReleaseRequest r = new ReleaseRequest();
        r.setVersionDllCsharp("26.6.11.2");

        String html = service.generateEmail(r);

        assertThat(html).doesNotContain("Versión DLL C#");
    }

    @Test
    void generateTelegramMessage_versionDllCsharpSinArtefactoMarcado_noUsaElRespaldoDelCorreo() {
        ReleaseRequest r = new ReleaseRequest();
        r.setVersion("26.10.8");
        r.setVersionDllCsharp("26.6.11.2");

        assertThat(service.generateTelegramMessage(r)).doesNotContain("DLL C#").doesNotContain("26.6.11.2");
    }

    @Test
    void generateTelegramMessage_versionDllCsharpDeTelegramSeUsaAunSinArtefactoEnElCorreo() {
        ReleaseRequest r = new ReleaseRequest();
        r.setTelegramVersionDllCsharp("26.6.11.2");

        assertThat(service.generateTelegramMessage(r)).contains("Versión DLL C#: 26.6.11.2");
    }

    @Test
    void generateReleaseMarkdown_versionDllCsharpSinArtefactoMarcado_seIgnora() {
        ReleaseRequest r = new ReleaseRequest();
        r.setVersionDllCsharp("26.6.11.2");

        String md = new String(service.generateReleaseMarkdown(r), java.nio.charset.StandardCharsets.UTF_8);

        assertThat(md).doesNotContain("26.6.11.2");
    }

    // ── Sincronización correo → Telegram (DLL C#) ──────────────────────────

    @Test
    void generateTelegramMessage_sinCamposTelegram_usaModuloYVersionDllCsharpDelCorreo() {
        ReleaseRequest r = requestConDllCsharp();

        String msg = service.generateTelegramMessage(r);

        assertThat(msg).contains("\uD83D\uDCE6 DLL C#");
        assertThat(msg).contains("\uD83C\uDFF7\uFE0F Versión Módulo: 26.10.8");
        assertThat(msg).contains("\uD83C\uDFF7\uFE0F Versión DLL C#: 26.6.11.2");
        assertThat(msg).doesNotContain("Branch compilación DLL C#");
    }

    @Test
    void generateTelegramMessage_camposTelegramTienenPrioridadSobreElCorreo() {
        ReleaseRequest r = requestConDllCsharp();
        r.setTelegramModules(List.of("Cartera"));
        r.setTelegramVersionDllCsharp("99.9.9.9");
        r.setTelegramBranchDllName("otra/rama");
        r.setTelegramBranchDllRepoUrl("https://otro/repo.git");

        String msg = service.generateTelegramMessage(r);

        assertThat(msg).contains("\uD83C\uDFF7\uFE0F Versión DLL C#: 99.9.9.9");
        assertThat(msg).contains("DLL C#: https://otro/repo.git  branch otra/rama");
        assertThat(msg).doesNotContain("26.6.11.2");
        // Con módulos de Telegram explícitos, el artefacto del correo no se agrega solo
        assertThat(msg).contains("\uD83D\uDCE6 Cartera").doesNotContain("\uD83D\uDCE6 DLL C#");
    }

    @Test
    void generateTelegramMessage_dllCsharpSinMarcar_noAgregaModuloNiVersion() {
        ReleaseRequest r = new ReleaseRequest();
        r.setHasDll(true);
        r.setVersion("26.10.8");

        String msg = service.generateTelegramMessage(r);

        assertThat(msg).contains("\uD83D\uDCE6 DLL").doesNotContain("DLL C#");
    }

    @Test
    void generateTelegramMessage_dllVbYDllCsharpSonModulosDistintos() {
        ReleaseRequest r = requestConDllCsharp();
        r.setHasDll(true);
        r.setHasCitrix(true);
        r.setHasWinterX(true);

        String msg = service.generateTelegramMessage(r);

        assertThat(msg).contains("\uD83D\uDCE6 DLL\n\uD83D\uDCE6 DLL C#\n\uD83D\uDCE6 WinterX")
            .doesNotContain("Citrix");
    }

    @Test
    void generateTelegramMessage_citrixNoSeMencionaAunqueLlegueEnLosModulos() {
        ReleaseRequest r = new ReleaseRequest();
        r.setTelegramModules(List.of("Cartera", "Citrix", "DLL"));

        assertThat(service.generateTelegramMessage(r))
            .contains("\uD83D\uDCE6 Cartera\n\uD83D\uDCE6 DLL").doesNotContain("Citrix");
    }

    @Test
    void generateTelegramMessage_soloRamaDllCsharp_sinRepositorio() {
        ReleaseRequest r = new ReleaseRequest();
        r.setTelegramBranchDllName("origin/release");

        assertThat(service.generateTelegramMessage(r))
            .contains("\uD83C\uDF3F Branch compilación DLL C#: branch origin/release");
    }

    // ── Exportes ───────────────────────────────────────────────────────────

    @Test
    void generateReleaseMarkdown_conDllCsharp_incluyeRutaYVersion() {
        String md = new String(service.generateReleaseMarkdown(requestConDllCsharp()),
            java.nio.charset.StandardCharsets.UTF_8);

        assertThat(md).contains("| Versión DLL C# | 26.6.11.2 |");
        assertThat(md).contains("- **DLL C#:** `\\\\ntdesarrollo.evolution.corp\\sprints$\\RFC'S Semana 47 2026\\DLL C#`");
        assertThat(md).doesNotContain("## Branches");
    }

    @Test
    void generateReleaseMarkdown_sinDllCsharp_noIncluyeLineasDllCsharp() {
        ReleaseRequest r = new ReleaseRequest();
        r.setVersion("1.0.0");

        String md = new String(service.generateReleaseMarkdown(r), java.nio.charset.StandardCharsets.UTF_8);

        assertThat(md).doesNotContain("DLL C#");
    }

    @Test
    void generateReleasePdf_conDllCsharp_incluyeVersion() throws Exception {
        byte[] pdf = service.generateReleasePdf(requestConDllCsharp());

        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        var reader = new com.lowagie.text.pdf.PdfReader(pdf);
        String text = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);
        assertThat(text).contains("Versión DLL C#:").contains("26.6.11.2");
        assertThat(text).contains("DLL C#:");
    }

    // ── generateRdlEmail ───────────────────────────────────────────────────

    @Test
    void generateRdlEmail_conUnRdl_retornaHtmlValido() {
        RdlItem item = new RdlItem();
        item.setRdlReportName("CAR_CuentasMaestras");
        item.setRdlUrlMegang("http://megang-612/Reports/report/Modulos/Cartera/CAR_CuentasMaestras");

        RdlReleaseRequest r = new RdlReleaseRequest();
        r.setRdls(List.of(item));

        String html = service.generateRdlEmail(r);

        assertThat(html).contains("CAR_CuentasMaestras");
        assertThat(html).contains("MEGANG-612");
        assertThat(html).contains("NTRS02");
    }

    @Test
    void generateRdlEmail_conVariosRdl_separaCadaUnoConBannerNumerado() {
        RdlItem a = new RdlItem();
        a.setRdlReportName("Reporte_A");
        a.setHasRdlSp(true);
        a.setRdlSpName("sp_uno\nsp_dos");
        a.setRdlSpTicket("111\n222");
        RdlItem b = new RdlItem();
        b.setRdlReportName("Reporte_B");

        RdlReleaseRequest r = new RdlReleaseRequest();
        r.setRdls(List.of(a, b));

        String html = service.generateRdlEmail(r);

        assertThat(html).contains("class=\"rdl-hdr\"").contains("1. Reporte_A").contains("2. Reporte_B");
        assertThat(html).contains("Ejecutar SP's @DBAs TI").contains("sp_dos").contains("Ticket VoBo: 222");
    }

    @Test
    void generateRdlEmail_listaVacia_noFalla() {
        RdlReleaseRequest r = new RdlReleaseRequest();

        String html = service.generateRdlEmail(r);

        assertThat(html).isNotNull();
        assertThat(html).contains("Buen día Operaciones");
    }
}
