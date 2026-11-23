package release_mail_generator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;
import release_mail_generator.model.UatBlock;
import release_mail_generator.model.UatRequest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UatEmailServiceTest {

    private static final String PIXEL =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

    private UatEmailService service;

    @BeforeEach
    void setUp() {
        service = new UatEmailService();
        ReflectionTestUtils.setField(service, "resourceLoader", new DefaultResourceLoader());
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) n++;
        return n;
    }

    /** Caso real: RFC 25473, anexo 25554, requerimientos desde el 3, ejemplo Suscriptor 513 / Sucursal 225. */
    static UatRequest casoReal() {
        UatRequest r = new UatRequest();
        r.setRfcNumber("25473");
        r.setRfcName("Anexo 5: Nueva Estrategia Comercial 06 de Octubre");
        r.setSaludo("Buenas tardes");
        r.setRecipient("equipo de UAT");
        r.setAdjunto("Se adjunta la documentaci\u00f3n correspondiente.");
        r.setRelatedRfcs(List.of("25554"));
        r.setRequirementsStart(3);
        r.setRequirementsIntro("y su anexo RFC 25554");

        UatRequest.Requirement r1 = new UatRequest.Requirement();
        r1.setTitle("Alta de promociones en el cat\u00e1logo");
        r1.setDescription("Se agregan las promociones nuevas al cat\u00e1logo comercial.");
        r1.setBullets(List.of("Promoci\u00f3n 2315 - Doble se vuelve Triple", "Promoci\u00f3n 3816 - Internet 120 Mbps + TV"));
        UatRequest.Requirement r2 = new UatRequest.Requirement();
        r2.setTitle("Convivencia con plan base");
        r2.setDescription("Las promociones deben convivir con el plan base de 120 Mbps.");
        r.setRequirements(new ArrayList<>(List.of(r1, r2)));

        UatRequest.KeyValue k1 = new UatRequest.KeyValue();
        k1.setKey("Suscriptor"); k1.setValue("513");
        UatRequest.KeyValue k2 = new UatRequest.KeyValue();
        k2.setKey("Sucursal"); k2.setValue("225");
        UatRequest.Example ex = new UatRequest.Example();
        ex.setPairs(new ArrayList<>(List.of(k1, k2)));
        r.setValidationExamples(new ArrayList<>(List.of(ex)));

        r.setValidationModule("Servicios_UAT");
        r.setValidationServer("MEGANG-384");
        r.setNota("Validar solo servicios residenciales.");
        r.setCierre("Quedo pendiente por cualquier duda o comentario.");
        return r;
    }

    // ── Payload legacy ─────────────────────────────────────────────────────

    @Test
    void payloadMinimoGeneraCorreo() {
        UatRequest r = new UatRequest();
        r.setRfcNumber("RFC 24269");
        r.setRfcName("Prueba");
        String html = service.generateEmail(r);
        assertTrue(html.contains("RFC 24269"));
        assertTrue(html.contains("Prueba"));
        assertTrue(html.contains("MEGANG-384"), "los pasos por defecto siguen presentes");
    }

    @Test
    void payloadLegacyCompletoSigueGenerandoElFormatoAnterior() {
        UatRequest r = new UatRequest();
        r.setRfcNumber("RFC 24269");
        r.setRfcName("Promociones combinen con 120 Mbps");
        r.setSaludo("Buenos d\u00edas.");
        r.setAdjunto("Adjunto documentaci\u00f3n y universo.");
        r.setRequerimientos("<ul><li><b>2315</b> Doble se vuelve Triple</li></ul>");
        r.setRequerimientosImagen(PIXEL);
        r.setRequerimientosImagenes(List.of(PIXEL, PIXEL));
        UatBlock b1 = new UatBlock();
        b1.setTexto("<p>Suscriptor <b>513</b></p><a href=\"https://ejemplo.com\">liga</a>");
        b1.setImagenBase64(PIXEL);
        UatBlock b2 = new UatBlock();
        b2.setTexto("Solo texto plano & simple");
        r.setBlocks(List.of(b1, b2));
        r.setNota("Solo residenciales");
        r.setCierre("Gracias.");

        String html = service.generateEmail(r);
        assertTrue(html.contains("Buenos d\u00edas."));
        assertTrue(html.contains("RFC 24269"));
        assertTrue(html.contains("Adjunto documentaci\u00f3n y universo."));
        assertTrue(html.contains("<b>Requerimientos:</b>"));
        assertTrue(html.contains("<b>2315</b>"));
        assertTrue(html.contains("Ejemplos de validaci&oacute;n:"));
        assertTrue(html.contains("href=\"https://ejemplo.com\""));
        assertTrue(html.contains("Solo texto plano &amp; simple"));
        assertTrue(html.contains("<b>NOTA:</b>"));
        assertTrue(html.contains("Solo residenciales"));
        assertTrue(html.contains("Gracias."));
        assertTrue(html.contains("MEGANG-384"));
        assertTrue(count(html, "<img") >= 3, "imagenes de requerimientos, del bloque y de los pasos");
        assertFalse(html.contains("RFCs relacionados"), "no mezcla el formato nuevo");

        assertTrue(service.generateMarkdown(r).length > 0);
    }

    @Test
    void contenidoLegacyConImgOnerrorSeNeutraliza() {
        UatRequest r = new UatRequest();
        r.setRfcNumber("RFC 1");
        r.setRfcName("x");
        r.setRequerimientos("<b>Req</b><img src=x onerror=alert(1)>");
        UatBlock b = new UatBlock();
        b.setTexto("<p>Ejemplo</p><a href=\"javascript:alert(1)\">enlace</a>");
        r.setBlocks(List.of(b));
        String html = service.generateEmail(r).toLowerCase();
        assertFalse(html.contains("onerror"), html);
        assertFalse(html.contains("javascript:"), html);
        assertTrue(html.contains("<b>req</b>"), html);
        assertTrue(html.contains("ejemplo"), html);
    }

    @Test
    void imagenConSrcMalicioso_noRompeElAtributo() {
        UatBlock b = new UatBlock();
        b.setImagenBase64("data:image/png;base64,AAAA\" onerror=\"alert(1)");
        UatRequest r = new UatRequest();
        r.setBlocks(List.of(b));
        String html = service.generateEmail(r).toLowerCase();
        assertFalse(html.contains("onerror"), html);
    }

    @Test
    void camposSimplesSeEscapan() {
        UatRequest r = new UatRequest();
        r.setRfcName("<script>alert(1)</script>");
        r.setSaludo("<img src=x onerror=alert(1)>");
        r.setAdjunto("\"><svg onload=alert(1)>");
        String html = service.generateEmail(r);
        assertFalse(html.contains("<script"), html);
        assertFalse(html.contains("<img src=x"), html);
        assertFalse(html.contains("<svg"), html);
    }

    // ── Formato estructurado ───────────────────────────────────────────────

    @Test
    void casoRealGeneraLaEstructuraEsperada() {
        String html = service.generateEmail(casoReal());
        assertTrue(html.contains("Buenas tardes, equipo de UAT."), html);
        assertTrue(html.contains("<b>RFC 25473 &ndash; Anexo 5: Nueva Estrategia Comercial 06 de Octubre</b>"), html);
        assertTrue(html.contains("Se adjunta la documentaci\u00f3n correspondiente."), html);
        assertTrue(html.contains("RFCs relacionados: 25554."), html);
        assertTrue(html.contains("y su anexo RFC 25554"), html);
        assertTrue(html.contains("<b>3. Alta de promociones en el cat\u00e1logo</b>"), html);
        assertTrue(html.contains("<b>4. Convivencia con plan base</b>"), html);
        assertTrue(html.contains("&bull; Promoci\u00f3n 2315 - Doble se vuelve Triple"), html);
        assertTrue(html.contains("Suscriptor: 513<br>Sucursal: 225"), html);
        assertTrue(html.contains("Favor de validar con el m&oacute;dulo Servicios_UAT."), html);
        assertTrue(html.contains("1. Entrar a la carpeta M\u00f3dulos SFYC UAT"), html);
        assertTrue(html.contains("3. En el campo Servidor seleccionar MEGANG-384"), html);
        assertEquals(3, count(html, "<img"), "una imagen por cada paso por defecto");
        assertTrue(html.contains("Quedo pendiente por cualquier duda o comentario."), html);
        assertFalse(html.contains("<ol"), "numeracion escrita, sin ol");
        assertFalse(html.contains("background"), "sin fondos");
        assertFalse(html.contains("border-radius"), "sin bordes redondeados");
    }

    @Test
    void numeracionInicialConfigurableYSaltaVacios() {
        UatRequest r = casoReal();
        r.setRequirementsStart(7);
        r.getRequirements().add(1, new UatRequest.Requirement());
        String html = service.generateEmail(r);
        assertTrue(html.contains("<b>7. Alta"), html);
        assertTrue(html.contains("<b>8. Convivencia"), html);
    }

    @Test
    void pasoEditadoConservaSuImagenPredeterminada() {
        UatRequest r = casoReal();
        UatRequest.Step s1 = new UatRequest.Step();
        s1.setText("Abrir la carpeta de m\u00f3dulos UAT");
        s1.setImage("@step1");
        UatRequest.Step s2 = new UatRequest.Step();
        s2.setText("Paso sin imagen");
        r.setValidationSteps(List.of(s1, s2));
        String html = service.generateEmail(r);
        assertTrue(html.contains("1. Abrir la carpeta de m\u00f3dulos UAT"), html);
        assertTrue(html.contains("2. Paso sin imagen"), html);
        assertEquals(1, count(html, "<img"), html);
        assertFalse(html.contains("MEGANG-384"), "con pasos propios no se inyectan los de por defecto");
    }

    @Test
    void imagenesDeRequerimientoYEjemploSeIncrustan() {
        UatRequest r = casoReal();
        r.getRequirements().get(0).setImage(PIXEL);
        r.getValidationExamples().get(0).setImage(PIXEL);
        assertEquals(5, count(service.generateEmail(r), "<img"));
    }

    @Test
    void variosEjemplosLlevanEncabezado() {
        UatRequest r = casoReal();
        r.setValidationExamples(List.of(r.getValidationExamples().get(0), r.getValidationExamples().get(0)));
        String html = service.generateEmail(r);
        assertTrue(html.contains("Ejemplo 1:"), html);
        assertTrue(html.contains("Ejemplo 2:"), html);
    }

    @Test
    void fechaNoSeDuplicaSiElNombreYaLaIncluye() {
        UatRequest r = casoReal();
        r.setPublishDate("2026-10-06");
        assertFalse(service.generateEmail(r).contains("programado para el"));

        r.setRfcName("Anexo 5: Nueva Estrategia Comercial");
        assertTrue(service.generateEmail(r).contains("programado para el 06 de octubre de 2026"));
    }

    @Test
    void camposEstructuradosSeEscapan() {
        UatRequest r = casoReal();
        r.setRequirementsIntro("<img src=x onerror=alert(1)>");
        r.getRequirements().get(0).setTitle("<script>alert(1)</script>");
        r.getRequirements().get(0).setBullets(List.of("<b onclick=x()>negrita</b>"));
        r.getValidationExamples().get(0).getPairs().get(0).setValue("\"><svg onload=x>");
        r.setRecipient("<i>UAT</i>");
        r.setRelatedRfcs(List.of("1\"><script>"));
        r.setValidationModule("<u>m</u>");
        r.getRequirements().get(1).setImage("data:image/png;base64,AAAA\" onerror=\"x");
        String html = service.generateEmail(r).toLowerCase();
        assertFalse(html.contains("<script"), html);
        assertFalse(html.contains("<svg"), html);
        assertFalse(html.contains("<img src=x"), html);
        assertFalse(html.contains("<b onclick"), html);
        assertFalse(html.contains("<i>uat"), html);
        assertFalse(html.contains("onerror=\""), html);
    }

    @Test
    void asuntoPorDefectoYEditable() {
        UatRequest r = casoReal();
        assertEquals("VoBo UAT \u2013 RFC 25473 \u2013 Anexo 5: Nueva Estrategia Comercial 06 de Octubre", service.subjectFor(r));
        r.setSubject("Asunto propio");
        assertEquals("Asunto propio", service.subjectFor(r));
    }

    @Test
    void estructuradoConLegacyMezcladoConservaContenidoLibre() {
        UatRequest r = casoReal();
        r.setRequirementsIntro("");
        r.setRequerimientos("Texto de requerimientos antiguo");
        UatBlock b = new UatBlock();
        b.setTexto("<p>Contenido libre <b>avanzado</b></p>");
        r.setBlocks(List.of(b));
        String html = service.generateEmail(r);
        assertTrue(html.contains("Texto de requerimientos antiguo"), html);
        assertTrue(html.contains("Contenido libre <b>avanzado</b>"), html);
        int libre = html.indexOf("Contenido libre <b>avanzado</b>");
        assertTrue(libre > html.indexOf("Suscriptor: 513"), "el contenido libre va despues de los ejemplos");
        assertEquals(html.lastIndexOf("Ejemplos de validaci&oacute;n:"), html.indexOf("Ejemplos de validaci&oacute;n:"));
        r.setValidationExamples(new ArrayList<>());
        assertFalse(service.generateEmail(r).contains("Ejemplos de validaci&oacute;n:"),
                "el contenido libre no se mezcla bajo el titulo de ejemplos");
    }

    @Test
    void encabezadoDeEjemploEditable() {
        UatRequest r = casoReal();
        r.getValidationExamples().get(0).setTitle("Ejemplo: Suscriptor 513");
        String html = service.generateEmail(r);
        assertTrue(html.contains("<b>Ejemplo: Suscriptor 513</b>"), html);
        UatRequest.Example otro = new UatRequest.Example();
        UatRequest.KeyValue kv = new UatRequest.KeyValue();
        kv.setKey("Plaza"); kv.setValue("7");
        otro.setPairs(List.of(kv));
        r.getValidationExamples().add(otro);
        html = service.generateEmail(r);
        assertTrue(html.contains("<b>Ejemplo: Suscriptor 513</b>"), html);
        assertTrue(html.contains("<b>Ejemplo 2:</b>"), "sin titulo usa el numerado por defecto: " + html);
    }

    @Test
    void vistaPreviaIncluyeTextoAsuntoYAvisosSuaves() {
        UatEmailService.Preview ok = service.preview(casoReal());
        assertTrue(ok.warnings().isEmpty(), ok.warnings().toString());
        assertTrue(ok.text().contains("3. Alta de promociones en el cat\u00e1logo"), ok.text());
        assertTrue(ok.text().contains("   * Promoci\u00f3n 2315 - Doble se vuelve Triple"), ok.text());
        assertTrue(ok.text().contains("Suscriptor: 513"), ok.text());
        assertFalse(ok.text().contains("<"), ok.text());

        UatRequest vacio = new UatRequest();
        vacio.setRfcNumber("abc");
        vacio.setRecipient("equipo de UAT");
        UatEmailService.Preview avisos = service.preview(vacio);
        assertEquals(3, avisos.warnings().size(), avisos.warnings().toString());
        assertTrue(avisos.html().contains("RFC abc"), "avisa pero genera igual");
    }

    @Test
    void pdfYMarkdownEstructuradosSeGeneran() throws Exception {
        byte[] pdf = service.generatePdf(casoReal());
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        String md = new String(service.generateMarkdown(casoReal()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(md.contains("3. Alta de promociones"), md);
        assertTrue(md.contains("**Requerimientos:**"), md);
    }
}
